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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.QueryOperators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.ListCollectionsPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;
import akka.contrib.persistence.mongodb.SnapshottingFieldNames$;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
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

    // pattern that matches nothing
    private static final Pattern MATCH_NOTHING = Pattern.compile(".\\A");

    // group name of collection name suffix
    private static final String SUFFIX = "suffix";

    private static final String AKKA_PERSISTENCE_JOURNAL_AUTO_START =
            "akka.persistence.journal.auto-start-journals";
    private static final String AKKA_PERSISTENCE_SNAPS_AUTO_START =
            "akka.persistence.snapshot-store.auto-start-snapshot-stores";

    private static final String JOURNAL_COLLECTION_NAME_KEY = "overrides.journal-collection";
    private static final String SNAPS_COLLECTION_NAME_KEY = "overrides.snaps-collection";

    private static final String ID = JournallingFieldNames$.MODULE$.ID();
    private static final String PROCESSOR_ID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();
    private static final String TO = JournallingFieldNames$.MODULE$.TO();
    private static final String SN = SnapshottingFieldNames$.MODULE$.SEQUENCE_NUMBER();
    private static final String GTE = QueryOperators.GTE;
    private static final String LT = QueryOperators.LT;

    private static final Integer PROJECT_INCLUDE = 1;
    private static final Integer SORT_DESCENDING = -1;

    private static final Document JOURNAL_PROJECT_DOCUMENT =
            toDocument(new Object[][]{{PROCESSOR_ID, PROJECT_INCLUDE}, {TO, PROJECT_INCLUDE}});
    private static final Document SNAPS_PROJECT_DOCUMENT =
            toDocument(new Object[][]{{PROCESSOR_ID, PROJECT_INCLUDE}, {SN, PROJECT_INCLUDE}});

    private static final Document ID_DESC = toDocument(new Object[][]{{ID, SORT_DESCENDING}});

    private static final String COLLECTION_NAME_FIELD = "name";
    private static final Duration MAX_BACK_OFF_DURATION = Duration.ofSeconds(128L);

    private final Pattern journalCollectionPrefix;
    private final Pattern snapsCollectionPrefix;
    private final DittoMongoClient mongoClient;
    private final Logger log;

    private MongoReadJournal(final Pattern journalCollectionPrefix, final Pattern snapsCollectionPrefix,
            final DittoMongoClient mongoClient) {
        this.journalCollectionPrefix = journalCollectionPrefix;
        this.snapsCollectionPrefix = snapsCollectionPrefix;
        this.mongoClient = mongoClient;
        log = LoggerFactory.getLogger(MongoReadJournal.class);
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
     * Creates a new {@code MongoReadJournal}.
     *
     * @param config The Akka system configuration.
     * @param mongoClient The Mongo client wrapper.
     * @return A {@code MongoReadJournal} object.
     */
    public static MongoReadJournal newInstance(final Config config, final DittoMongoClient mongoClient) {
        final String autoStartJournalKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_JOURNAL_AUTO_START);
        final String autoStartSnapsKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_SNAPS_AUTO_START);
        final Pattern journalCollectionPrefix =
                getOverrideCollectionNamePattern(config.getConfig(autoStartJournalKey), JOURNAL_COLLECTION_NAME_KEY);
        final Pattern snapsCollectionPrefix =
                getOverrideCollectionNamePattern(config.getConfig(autoStartSnapsKey), SNAPS_COLLECTION_NAME_KEY);
        return new MongoReadJournal(journalCollectionPrefix, snapsCollectionPrefix, mongoClient);
    }

    /**
     * Retrieve sequence numbers for persistence IDs modified within the time interval as a source of {@code
     * PidWithSeqNr}. A persistence ID may appear multiple times with various sequence numbers.
     *
     * @param start start of the time window.
     * @param end end of the time window.
     * @return source of persistence IDs and sequence numbers written within the given time window.
     */
    public Source<PidWithSeqNr, NotUsed> getPidWithSeqNrsByInterval(final Instant start, final Instant end) {
        final MongoDatabase db = mongoClient.getDefaultDatabase();
        final Document idFilter = createIdFilter(start, end);

        log.debug("Looking for journal collection with pattern <{}>.", journalCollectionPrefix);

        return listJournalsAndSnapshotStores()
                .flatMapConcat(journalAndSnaps -> listPidWithSeqNr(journalAndSnaps, db, idFilter));
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
            final ActorMaterializer mat) {

        final int maxRestarts = computeMaxRestarts(maxIdleTime);
        return listJournals().withAttributes(Attributes.inputBuffer(1, 1))
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
     * @param maxIdleTime max idle time of the stream.
     * @param mat the materializer.
     * @return all unique PIDs in journals above a lower bound.
     */
    public Source<String, NotUsed> getJournalPidsAbove(final String lowerBoundPid, final int batchSize,
            final Duration maxIdleTime, final ActorMaterializer mat) {

        return listJournals().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listPidsInJournal(journal, lowerBoundPid, batchSize, mat, Duration.ZERO, 0)
                )
                .mapConcat(pids -> pids);
    }

    private Source<List<String>, NotUsed> listPidsInJournal(final MongoCollection<Document> journal,
            final String lowerBound, final int batchSize, final ActorMaterializer mat, final Duration maxBackOff,
            final int maxRestarts) {

        return Source.unfoldAsync("",
                start -> {
                    final String actualStart = lowerBound.compareTo(start) >= 0 ? lowerBound : start;
                    return listJournalPidsAbove(journal, actualStart, batchSize, maxBackOff, maxRestarts)
                            .runWith(Sink.seq(), mat)
                            .thenApply(list -> {
                                if (list.isEmpty()) {
                                    return Optional.empty();
                                } else {
                                    return Optional.of(Pair.create(list.get(list.size() - 1), list));
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
                () -> Source.fromPublisher(journal.aggregate(pipeline)).map(document ->
                        document.getString(ID)));
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

    private Source<PidWithSeqNr, NotUsed> listPidWithSeqNr(final JournalAndSnaps journalAndSnaps,
            final MongoDatabase database, final Document idFilter) {
        final Source<PidWithSeqNr, NotUsed> journalPids;
        final Source<PidWithSeqNr, NotUsed> snapsPids;

        if (journalAndSnaps.journal == null) {
            journalPids = Source.empty();
        } else {
            journalPids = find(database, journalAndSnaps.journal, idFilter, JOURNAL_PROJECT_DOCUMENT)
                    .map(doc -> new PidWithSeqNr(doc.getString(PROCESSOR_ID), doc.getLong(TO)));
        }

        if (journalAndSnaps.snaps == null) {
            snapsPids = Source.empty();
        } else {
            snapsPids = find(database, journalAndSnaps.snaps, idFilter, SNAPS_PROJECT_DOCUMENT)
                    .map(doc -> new PidWithSeqNr(doc.getString(PROCESSOR_ID), doc.getLong(SN)));
        }

        return journalPids.concat(snapsPids);
    }

    private Source<Document, NotUsed> find(final MongoDatabase db, final String collection, final Document filter,
            final Document project) {

        return Source.fromPublisher(
                db.getCollection(collection).find(filter).projection(project).sort(ID_DESC)
        );
    }

    private Source<JournalAndSnaps, NotUsed> listJournalsAndSnapshotStores() {
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        return resolveCollectionNames(journalCollectionPrefix, snapsCollectionPrefix, database, log)
                .map(this::toJournalAndSnaps);
    }

    private Source<MongoCollection<Document>, NotUsed> listJournals() {
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        return resolveCollectionNames(journalCollectionPrefix, MATCH_NOTHING, database, log)
                .map(database::getCollection);
    }

    private JournalAndSnaps toJournalAndSnaps(final String collectionName) {
        final Matcher matcher1 = journalCollectionPrefix.matcher(collectionName);
        if (matcher1.matches()) {
            return new JournalAndSnaps(matcher1.group(SUFFIX), collectionName, null);
        } else {
            final Matcher matcher2 = snapsCollectionPrefix.matcher(collectionName);
            if (matcher2.matches()) {
                return new JournalAndSnaps(matcher2.group(SUFFIX), null, collectionName);
            } else {
                throw new IllegalArgumentException(String.format(
                        "Collection is neither journal nor snapshot-store: <%s>", collectionName));
            }
        }
    }

    private Document createIdFilter(final Instant start, final Instant end) {
        final ObjectId startObjectId = instantToObjectIdBoundary(start);
        final ObjectId endObjectId = instantToObjectIdBoundary(end.plus(1L, ChronoUnit.SECONDS));
        log.debug("Limiting query to ObjectIds $gte {} and $lt {}", startObjectId, endObjectId);
        return toDocument(new Object[][]{
                {ID, toDocument(new Object[][]{
                        {GTE, startObjectId},
                        {LT, endObjectId}
                })}
        });
    }

    /* Create a ObjectID boundary from a timestamp to be used for comparison in MongoDB queries. */
    private static ObjectId instantToObjectIdBoundary(final Instant instant) {
        // MongoDBObject IDs only contain dates with precision of seconds, thus adjust the range of the query
        // appropriately to make sure a client does not miss data when providing Instants with higher precision.
        //
        // Do not use
        //
        //   new ObjectId(Date.from(startTruncatedToSecs))
        //
        // to compute object ID boundaries. The 1-argument constructor above appends incidental non-zero bits after
        // the timestamp and may filter out events persisted after 'instant' if they happen to have
        // a lower machine ID, process ID or counter value. (A MongoDB ObjectID is a byte array with fields for
        // timestamp, machine ID, process ID and counter such that timestamp occupies the most significant bits.)
        return new ObjectId(Date.from(instant.truncatedTo(ChronoUnit.SECONDS)), 0, (short) 0, 0);
    }

    private static Document toDocument(final Object[][] keyValuePairs) {
        final Map<String, Object> map = new HashMap<>(keyValuePairs.length);
        for (final Object[] keyValuePair : keyValuePairs) {
            map.put(keyValuePair[0].toString(), keyValuePair[1]);
        }
        return new Document(map);
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
     * Resolve event journal collection prefix (e.g. "things_journal") from the auto-start journal configuration.
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
    private static Pattern getOverrideCollectionNamePattern(final Config journalOrSnapsConfig, final String key) {
        final String collectionPrefix = journalOrSnapsConfig.getString(key);
        return Pattern.compile("^" + collectionPrefix + String.format("(?<%s>.*)", SUFFIX));
    }

    /**
     * Resolves all journal and snapshot-store collection names matching the passed prefixes.
     *
     * @param journalCollectionPrefix the prefix of the journal collections to resolve.
     * @param snapsCollectionPrefix the prefix of the journal collections to resolve.
     * @param database the MongoDB database to use for resolving collection names.
     * @return a source of resolved journal collection names which matched the prefix.
     */
    private static Source<String, NotUsed> resolveCollectionNames(final Pattern journalCollectionPrefix,
            final Pattern snapsCollectionPrefix, final MongoDatabase database, final Logger log) {

        // starts with "journalCollectionPrefix":
        final ListCollectionsPublisher<Document> documentListCollectionsPublisher = database.listCollections();
        final Bson filter = Filters.or(Filters.regex(COLLECTION_NAME_FIELD, journalCollectionPrefix),
                Filters.regex(COLLECTION_NAME_FIELD, snapsCollectionPrefix));
        final Publisher<Document> publisher = documentListCollectionsPublisher.filter(filter);
        return Source.fromPublisher(publisher)
                .map(document -> document.getString(COLLECTION_NAME_FIELD))
                // Double check in case the Mongo API persistence layer in use does not support listCollections with filtering
                .filter(collectionName -> journalCollectionPrefix.matcher(collectionName).matches() ||
                        snapsCollectionPrefix.matcher(collectionName).matches())
                .map(collectionName -> {
                    log.debug("Collection <{}> with patterns <{}> or <{}> found.", collectionName,
                            journalCollectionPrefix, snapsCollectionPrefix);
                    return collectionName;
                })
                // Each "get current PIDs" query collects all collection names in memory in order to list them in
                // a fixed order.
                .<SortedSet<String>>fold(new TreeSet<>(), (collectionNames, collectionName) -> {
                    collectionNames.add(collectionName);
                    return collectionNames;
                })
                .mapConcat(collectionNames -> collectionNames);
    }

    private static final class JournalAndSnaps {

        @Nullable
        private final String suffix;

        @Nullable
        private final String journal;

        @Nullable
        private final String snaps;

        private JournalAndSnaps() {
            this.suffix = null;
            journal = null;
            snaps = null;
        }

        private JournalAndSnaps(@Nullable final String suffix, @Nullable final String journal,
                @Nullable final String snaps) {
            this.suffix = suffix;
            this.journal = journal;
            this.snaps = snaps;
        }

        @Override
        public String toString() {
            return "JournalAndSnapshot[journal=" + journal + ",snaps=" + snaps + "]";
        }

        @Nullable
        private String getSuffix() {
            return suffix;
        }

        private static JournalAndSnaps merge(final JournalAndSnaps js1, final JournalAndSnaps js2) {
            final String suffix = js1.suffix != null ? js1.suffix : js2.suffix;
            final String journal = js1.journal != null ? js1.journal : js2.journal;
            final String snaps = js1.snaps != null ? js1.snaps : js2.snaps;
            return new JournalAndSnaps(suffix, journal, snaps);
        }
    }

}
