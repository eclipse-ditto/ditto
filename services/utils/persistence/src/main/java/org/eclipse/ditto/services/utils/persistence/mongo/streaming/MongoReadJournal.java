/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;
import akka.contrib.persistence.mongodb.SnapshottingFieldNames$;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Reads the event journal of com.github.scullxbones.akka-persistence-mongo plugin.
 * In the Akka system configuration,
 * <ul>
 * <li>
 * {@code akka.persistence.journal.auto-start-journals} must contain exactly 1 configuration key {@code
 * <JOURNAL_KEY>},
 * </li>
 * <li>
 * {@code <JOURNAL_KEY>.overrides.journal-collection} must be defined and equal to the name of the event journal
 * collection.
 * </li>
 * </ul>
 */
@AllValuesAreNonnullByDefault
public class MongoReadJournal {
    // not a final class to test with Mockito

    /**
     * ID field of documents delivered by the read journal.
     */
    public static final String ID = JournallingFieldNames$.MODULE$.ID();

    private static final String AKKA_PERSISTENCE_JOURNAL_AUTO_START =
            "akka.persistence.journal.auto-start-journals";
    private static final String AKKA_PERSISTENCE_SNAPS_AUTO_START =
            "akka.persistence.snapshot-store.auto-start-snapshot-stores";

    private static final String JOURNAL_COLLECTION_NAME_KEY = "overrides.journal-collection";
    private static final String SNAPS_COLLECTION_NAME_KEY = "overrides.snaps-collection";

    private static final String PROCESSOR_ID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();
    private static final String SN = SnapshottingFieldNames$.MODULE$.SEQUENCE_NUMBER();

    // Not working: SnapshottingFieldNames.V2$.MODULE$.SERIALIZED()
    private static final String SERIALIZED_SNAPSHOT = "s2";
    private static final String LIFECYCLE = "__lifecycle";

    private static final Duration MAX_BACK_OFF_DURATION = Duration.ofSeconds(128L);

    private final String journalCollection;
    private final String snapsCollection;
    private final DittoMongoClient mongoClient;

    private MongoReadJournal(final String journalCollection, final String snapsCollection,
            final DittoMongoClient mongoClient) {
        this.journalCollection = journalCollection;
        this.snapsCollection = snapsCollection;
        this.mongoClient = mongoClient;
    }

    /**
     * Create a read journal for an actor system with a persistence plugin having a unique auto-start journal.
     *
     * @param system the actor system.
     * @return the read journal.
     */
    public static MongoReadJournal newInstance(final ActorSystem system) {
        final Config config = system.settings().config();
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        return newInstance(config, MongoClientWrapper.newInstance(mongoDbConfig));
    }

    /**
     * Instantiate a read journal from collection names and database client, primarily for tests.
     *
     * @param journalCollection the journal collection name.
     * @param snapsCollection the snapshot collection name.
     * @param dittoMongoClient the client.
     * @return a read journal for the journal and snapshot collections.
     */
    public static MongoReadJournal newInstance(final String journalCollection, final String snapsCollection,
            final DittoMongoClient dittoMongoClient) {

        return new MongoReadJournal(journalCollection, snapsCollection, dittoMongoClient);
    }

    /**
     * Creates a new {@code MongoReadJournal}.
     *
     * @param config The Akka system configuration.
     * @param mongoClient The Mongo client wrapper.
     * @return A {@code MongoReadJournal} object.
     */
    public static MongoReadJournal newInstance(final Config config, final DittoMongoClient mongoClient) {
        final String autoStartJournalKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_JOURNAL_AUTO_START);
        final String autoStartSnapsKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_SNAPS_AUTO_START);
        final String journalCollection =
                getOverrideCollectionName(config.getConfig(autoStartJournalKey), JOURNAL_COLLECTION_NAME_KEY);
        final String snapshotCollection =
                getOverrideCollectionName(config.getConfig(autoStartSnapsKey), SNAPS_COLLECTION_NAME_KEY);
        return new MongoReadJournal(journalCollection, snapshotCollection, mongoClient);
    }

    /**
     * Retrieve all unique PIDs in journals. Does its best not to create long-living cursors on the database by reading
     * {@code batchSize} events per query.
     *
     * @param batchSize how many events to read in one query.
     * @param maxIdleTime how long the stream is allowed to idle without sending any element. Bounds the number of
     * retries with exponential back-off.
     * @param mat the actor materializer to run the query streams.
     * @return Source of all persistence IDs such that each element contains the persistence IDs in {@code batchSize}
     * events that do not occur in prior buckets.
     */
    public Source<String, NotUsed> getJournalPids(final int batchSize, final Duration maxIdleTime,
            final Materializer mat) {

        final int maxRestarts = computeMaxRestarts(maxIdleTime);
        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listPidsInJournal(journal, "", batchSize, mat, MAX_BACK_OFF_DURATION, maxRestarts)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Retrieve all unique PIDs in journals above a lower bound. Does not limit database access in any way.
     *
     * @param lowerBoundPid the lower-bound PID.
     * @param batchSize how many events to read in 1 query.
     * @param mat the materializer.
     * @return all unique PIDs in journals above a lower bound.
     */
    public Source<String, NotUsed> getJournalPidsAbove(final String lowerBoundPid, final int batchSize,
            final Materializer mat) {

        return getJournal()
                .withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listPidsInJournal(journal, lowerBoundPid, batchSize, mat, MAX_BACK_OFF_DURATION, 0)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Retrieve all latest snapshots with unique PIDs in snapshot store above a lower bound.
     * Does not limit database access in any way.
     *
     * @param lowerBoundPid the lower-bound PID.
     * @param batchSize how many snapshots to read in 1 query.
     * @param mat the materializer.
     * @param snapshotFields snapshot fields to project out.
     * @return source of newest snapshots with unique PIDs.
     */
    public Source<Document, NotUsed> getNewestSnapshotsAbove(final String lowerBoundPid,
            final int batchSize,
            final Materializer mat,
            final String... snapshotFields) {

        return getSnapshotStore()
                .withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(snapshotStore ->
                        listNewestSnapshots(snapshotStore, lowerBoundPid, batchSize, mat,
                                snapshotFields)
                )
                .mapConcat(pids -> pids);
    }

    private Source<List<String>, NotUsed> listPidsInJournal(final MongoCollection<Document> journal,
            final String lowerBound, final int batchSize, final Materializer mat, final Duration maxBackOff,
            final int maxRestarts) {

        return unfoldBatchedSource(lowerBound, mat, Function.identity(), actualStart ->
                listJournalPidsAbove(journal, actualStart, batchSize, maxBackOff, maxRestarts)
        );
    }

    private Source<List<Document>, NotUsed> listNewestSnapshots(final MongoCollection<Document> snapshotStore,
            final String lowerBound,
            final int batchSize,
            final Materializer mat,
            final String... snapshotFields) {

        return this.unfoldBatchedSource(lowerBound,
                mat,
                SnapshotBatch::getMaxPid,
                actualStart -> listNewestActiveSnapshotsByBatch(snapshotStore, actualStart, batchSize, snapshotFields))
                .mapConcat(x -> x)
                .map(SnapshotBatch::getItems);
    }

    private <T> Source<List<T>, NotUsed> unfoldBatchedSource(
            final String lowerBound,
            final Materializer mat,
            final Function<T, String> seedCreator,
            final Function<String, Source<T, ?>> sourceCreator) {

        return Source.unfoldAsync("",
                start -> {
                    final String actualStart = lowerBound.compareTo(start) >= 0 ? lowerBound : start;
                    return sourceCreator.apply(actualStart)
                            .runWith(Sink.seq(), mat)
                            .thenApply(list -> {
                                if (list.isEmpty()) {
                                    return Optional.empty();
                                } else {
                                    return Optional.of(Pair.create(seedCreator.apply(list.get(list.size() - 1)), list));
                                }
                            });
                })
                .withAttributes(Attributes.inputBuffer(1, 1));
    }

    private Source<String, NotUsed> listJournalPidsAbove(final MongoCollection<Document> journal, final String start,
            final int batchSize, final Duration maxBackOff, final int maxRestarts) {

        final List<Bson> pipeline = new ArrayList<>(5);
        // optional match stage
        if (!start.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.gt(PROCESSOR_ID, start)));
        }

        // sort stage
        pipeline.add(Aggregates.sort(Sorts.ascending(PROCESSOR_ID)));

        // limit stage. It should come before group stage or MongoDB would scan the entire journal collection.
        pipeline.add(Aggregates.limit(batchSize));

        // group stage
        pipeline.add(Aggregates.group("$" + PROCESSOR_ID));

        // sort stage 2 -- order after group stage is not defined
        pipeline.add(Aggregates.sort(Sorts.ascending(ID)));

        final Duration minBackOff = Duration.ofSeconds(1L);
        final double randomFactor = 0.1;

        return RestartSource.onFailuresWithBackoff(minBackOff, maxBackOff, randomFactor, maxRestarts,
                () -> Source.fromPublisher(journal.aggregate(pipeline))
                        .flatMapConcat(document -> {
                            final Object pid = document.get(ID);
                            if (pid instanceof CharSequence) {
                                return Source.single(pid.toString());
                            } else {
                                return Source.empty();
                            }
                        })
        );
    }

    private int computeMaxRestarts(final Duration maxDuration) {
        if (MAX_BACK_OFF_DURATION.minus(maxDuration).isNegative()) {
            // maxBackOff < maxDuration: backOff at least 7 times (1+2+4+8+16+32+64=127s)
            return Math.max(7, 6 + (int) (maxDuration.toMillis() / MAX_BACK_OFF_DURATION.toMillis()));
        } else {
            // maxBackOff >= maxDuration: maxRestarts = log2 of maxDuration in seconds
            final int log2MaxDuration = 63 - Long.numberOfLeadingZeros(maxDuration.getSeconds());
            return Math.max(0, log2MaxDuration);
        }
    }

    private Source<SnapshotBatch, NotUsed> listNewestActiveSnapshotsByBatch(
            final MongoCollection<Document> snapshotStore,
            final String start,
            final int batchSize,
            final String... snapshotFields) {

        final List<Bson> pipeline = new ArrayList<>(5);
        // optional match stage
        if (!start.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.gt(PROCESSOR_ID, start)));
        }

        // sort stage
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.ascending(PROCESSOR_ID), Sorts.descending(SN))));

        // limit stage. It should come before group stage or MongoDB would scan the entire journal collection.
        pipeline.add(Aggregates.limit(batchSize));

        // group stage 1: by PID
        pipeline.add(Aggregates.group("$" + PROCESSOR_ID, asFirstSnapshotBsonFields(snapshotFields)));

        // sort stage 2 -- order after group stage is not defined
        pipeline.add(Aggregates.sort(Sorts.ascending(ID)));

        // group stage 2: filter out pids whose latest snapshot is a deleted snapshot, but retain max encountered pid
        final String maxPid = "m";
        final String items = "i";
        pipeline.add(Aggregates.group(null,
                Accumulators.max(maxPid, "$" + ID),
                Accumulators.push(
                        items,
                        new Document().append("$cond", new Document()
                                .append("if", new Document().append("$ne", Arrays.asList("$" + LIFECYCLE, "DELETED")))
                                .append("then", "$$CURRENT")
                                .append("else", null)
                        ))
        ));

        // remove null entries by projection
        pipeline.add(Aggregates.project(new Document()
                .append(maxPid, 1)
                .append(items, new Document()
                        .append("$setDifference", Arrays.asList("$" + items, Collections.singletonList(null)))
                )
        ));

        return Source.fromPublisher(snapshotStore.aggregate(pipeline))
                .flatMapConcat(document -> {
                    final String theMaxPid = document.getString(maxPid);
                    if (theMaxPid == null) {
                        return Source.empty();
                    } else {
                        return Source.single(new SnapshotBatch(theMaxPid, document.getList(items, Document.class)));
                    }
                });
    }

    /**
     * For $group stage of an aggregation pipeline over a snapshot collection: take the newest values of fields
     * of serialized snapshots. Always include the first snapshot lifecycle.
     *
     * @param snapshotFields fields of a serialized snapshot to project.
     * @return list of group stage field accumulators.
     */
    private List<BsonField> asFirstSnapshotBsonFields(final String... snapshotFields) {
        return Stream.concat(Stream.of(LIFECYCLE), Arrays.stream(snapshotFields))
                .map(fieldName -> {
                    final String serializedFieldName = String.format("$%s.%s", SERIALIZED_SNAPSHOT, fieldName);
                    return Accumulators.first(fieldName, serializedFieldName);
                })
                .collect(Collectors.toList());
    }

    private Source<MongoCollection<Document>, NotUsed> getJournal() {
        return Source.single(mongoClient.getDefaultDatabase().getCollection(journalCollection));
    }

    private Source<MongoCollection<Document>, NotUsed> getSnapshotStore() {
        return Source.single(mongoClient.getDefaultDatabase().getCollection(snapsCollection));
    }

    /**
     * Extract the auto-start journal/snaps config from the configuration of the actor system.
     * <p>
     * It assumes that in the Akka system configuration,
     * {@code akka.persistence.journal.auto-start-journals} or
     * {@code akka.persistence.snapshot-store.auto-start-snapshot-stores}
     * contains exactly 1 configuration key, which points to the configuration of the auto-start journal/snapshot-store.
     *
     * @param config the system configuration.
     * @param key either {@code akka.persistence.journal.auto-start-journals} or
     * {@code akka.persistence.snapshot-store.auto-start-snapshot-stores}.
     */
    private static String extractAutoStartConfigKey(final Config config, final String key) {
        final List<String> autoStartJournals = config.getStringList(key);
        if (autoStartJournals.size() != 1) {
            final String message = String.format("Expect %s to be a singleton list, but it is List(%s)",
                    AKKA_PERSISTENCE_JOURNAL_AUTO_START,
                    String.join(", ", autoStartJournals));
            throw new IllegalArgumentException(message);
        } else {
            return autoStartJournals.get(0);
        }
    }

    /**
     * Resolve event journal collection name (e.g. "things_journal") from the auto-start journal configuration.
     * <p>
     * It assumes that in the auto-start journal configuration,
     * {@code overrides.journal-collection} is defined and equal to the name of the event journal
     * collection.
     *
     * @param journalOrSnapsConfig The journal or snapshot-store configuration.
     * @param key Config key of the collection name.
     * @return The name of the event journal collection.
     * @throws IllegalArgumentException if {@code akka.persistence.journal.auto-start-journal} is not a singleton list.
     * @throws com.typesafe.config.ConfigException.Missing if a relevant config value is missing.
     * @throws com.typesafe.config.ConfigException.WrongType if a relevant config value has not the expected type.
     */
    private static String getOverrideCollectionName(final Config journalOrSnapsConfig, final String key) {
        return journalOrSnapsConfig.getString(key);
    }

    private static final class SnapshotBatch {

        private final String maxPid;
        private final List<Document> items;

        private SnapshotBatch(final String maxPid, final List<Document> items) {
            this.maxPid = maxPid;
            this.items = items;
        }

        private String getMaxPid() {
            return maxPid;
        }

        private List<Document> getItems() {
            return items;
        }
    }

}
