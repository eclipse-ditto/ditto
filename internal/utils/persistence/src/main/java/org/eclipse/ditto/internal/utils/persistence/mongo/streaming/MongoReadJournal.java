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
package org.eclipse.ditto.internal.utils.persistence.mongo.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexInitializer;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.JavaDslMongoReadJournal;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;
import akka.contrib.persistence.mongodb.SnapshottingFieldNames$;
import akka.japi.Pair;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.Offset;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.CurrentEventsByTagQuery;
import akka.persistence.query.javadsl.CurrentPersistenceIdsQuery;
import akka.persistence.query.javadsl.EventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.persistence.query.javadsl.PersistenceIdsQuery;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.RestartSettings;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Reads the event journal of {@code com.github.scullxbones.akka-persistence-mongo} plugin.
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
public final class MongoReadJournal implements CurrentEventsByPersistenceIdQuery,
        CurrentEventsByTagQuery, CurrentPersistenceIdsQuery, EventsByPersistenceIdQuery, EventsByTagQuery,
        PersistenceIdsQuery {

    /**
     * ID field of documents delivered by the journal collection.
     */
    public static final String J_ID = JournallingFieldNames$.MODULE$.ID();

    /**
     * ID field of documents delivered by the snaps collection.
     */
    public static final String S_ID = J_ID;

    /**
     * Prefix of the priority tag which is used in
     * {@link #getJournalPidsWithTagOrderedByPriorityTag(String, java.time.Duration)}
     * for sorting/ordering by.
     */
    public static final String PRIORITY_TAG_PREFIX = "priority-";

    private static final String AKKA_PERSISTENCE_JOURNAL_AUTO_START =
            "akka.persistence.journal.auto-start-journals";
    private static final String AKKA_PERSISTENCE_SNAPS_AUTO_START =
            "akka.persistence.snapshot-store.auto-start-snapshot-stores";

    private static final String JOURNAL_COLLECTION_NAME_KEY = "overrides.journal-collection";
    private static final String SNAPS_COLLECTION_NAME_KEY = "overrides.snaps-collection";

    /**
     * Document field of PID in journals.
     */
    private static final String J_PROCESSOR_ID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    /**
     * Document field of the highest event sequence number in journals.
     */
    private static final String J_TO = JournallingFieldNames$.MODULE$.TO();
    private static final String J_TAGS = JournallingFieldNames$.MODULE$.TAGS();

    /**
     * Document field of PID in snapshot stores.
     */
    private static final String S_PROCESSOR_ID = SnapshottingFieldNames$.MODULE$.PROCESSOR_ID();

    /**
     * Document field of the sequence number of snapshots.
     */
    public static final String S_SN = SnapshottingFieldNames$.MODULE$.SEQUENCE_NUMBER();

    /**
     * Document field of the timestamp of snapshots.
     */
    public static final String S_TS = SnapshottingFieldNames$.MODULE$.TIMESTAMP();

    private static final String S_SERIALIZED_SNAPSHOT = "s2";

    /**
     * Document field of lifecycle of snapshots.
     */
    public static final String LIFECYCLE = "__lifecycle";

    private static final String J_EVENT = JournallingFieldNames$.MODULE$.EVENTS();
    public static final String J_EVENT_PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();
    public static final String J_EVENT_MANIFEST = JournallingFieldNames$.MODULE$.MANIFEST();
    private static final String J_EVENT_SN = JournallingFieldNames$.MODULE$.SEQUENCE_NUMBER();

    private static final Duration MAX_BACK_OFF_DURATION = Duration.ofSeconds(128L);

    private static final Index TAG_PID_INDEX =
            IndexFactory.newInstance("ditto_tag_pid", List.of(J_TAGS, J_PROCESSOR_ID), false, true);

    private final String journalCollection;
    private final String snapsCollection;
    private final DittoMongoClient mongoClient;
    private final IndexInitializer indexInitializer;

    private final JavaDslMongoReadJournal akkaReadJournal;

    private MongoReadJournal(final String journalCollection,
            final String snapsCollection,
            final String readJournalConfigurationKey,
            final DittoMongoClient mongoClient,
            final ActorSystem actorSystem) {

        this.journalCollection = journalCollection;
        this.snapsCollection = snapsCollection;
        this.mongoClient = mongoClient;
        final var materializer = SystemMaterializer.get(actorSystem).materializer();
        indexInitializer = IndexInitializer.of(mongoClient.getDefaultDatabase(), materializer);
        akkaReadJournal = PersistenceQuery.get(actorSystem)
                .getReadJournalFor(JavaDslMongoReadJournal.class, readJournalConfigurationKey);
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
        return newInstance(config, MongoClientWrapper.newInstance(mongoDbConfig), system);
    }

    /**
     * Creates a new {@code MongoReadJournal}.
     *
     * @param config The Akka system configuration.
     * @param mongoClient The Mongo client wrapper.
     * @return A {@code MongoReadJournal} object.
     */
    public static MongoReadJournal newInstance(final Config config, final DittoMongoClient mongoClient,
            final ActorSystem actorSystem) {

        final String autoStartJournalKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_JOURNAL_AUTO_START);
        final String autoStartSnapsKey = extractAutoStartConfigKey(config, AKKA_PERSISTENCE_SNAPS_AUTO_START);
        final String journalCollection =
                getOverrideCollectionName(config.getConfig(autoStartJournalKey), JOURNAL_COLLECTION_NAME_KEY);
        final String snapshotCollection =
                getOverrideCollectionName(config.getConfig(autoStartSnapsKey), SNAPS_COLLECTION_NAME_KEY);
        return new MongoReadJournal(journalCollection,
                snapshotCollection,
                autoStartJournalKey + "-read",
                mongoClient,
                actorSystem
        );
    }

    /**
     * Ensure a compound index exists for journal PID streaming based on tags.
     *
     * @return a future that completes after index creation completes or fails when index creation fails.
     */
    public CompletionStage<Done> ensureTagPidIndex() {
        return indexInitializer.createNonExistingIndices(journalCollection, List.of(TAG_PID_INDEX));
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
                        listPidsInJournal(journal, "", "", batchSize, mat, maxRestarts)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Retrieve latest journal entries for each distinct PID.
     * Does its best not to create long-living cursors on the database by reading {@code batchSize} events per query.
     *
     * @param batchSize how many events to read in one query.
     * @param maxIdleTime how long the stream is allowed to idle without sending any element. Bounds the number of
     * retries with exponential back-off.
     * @param mat the actor materializer to run the query streams.
     * @return Source of all latest journal entries per pid such that each element contains the persistence IDs in
     * {@code batchSize} events that do not occur in prior buckets.
     */
    public Source<Document, NotUsed> getLatestJournalEntries(final int batchSize, final Duration maxIdleTime,
            final Materializer mat) {

        final int maxRestarts = computeMaxRestarts(maxIdleTime);
        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(
                        journal -> listLatestJournalEntries(journal, batchSize, mat,
                                maxRestarts, J_EVENT_PID, J_EVENT_SN, J_EVENT_MANIFEST))
                .mapConcat(pids -> pids);
    }

    private Source<List<Document>, NotUsed> listLatestJournalEntries(final MongoCollection<Document> journal,
            final int batchSize,
            final Materializer mat,
            final int maxRestarts,
            final String... journalFields) {

        return unfoldBatchedSource("",
                mat,
                document -> document.getString(J_ID),
                actualStartPid -> listLatestJournalEntries(journal, actualStartPid, "", batchSize,
                        maxRestarts, journalFields));
    }

    /**
     * Retrieve all unique PIDs in journals selected by a provided {@code tag}.
     * Does its best not to create long-living cursors on the database by reading {@code batchSize} events per query.
     *
     * @param tag the Tag name the journal entries have to contain in order to be selected, or an empty string to select
     * all journal entries.
     * @param batchSize how many events to read in one query.
     * @param maxIdleTime how long the stream is allowed to idle without sending any element. Bounds the number of
     * retries with exponential back-off.
     * @param mat the actor materializer to run the query streams.
     * @param considerOnlyLatest whether only the latest available journal entry should have the provided {@code tag},
     * or if any still available journal entry should be considered. If set to {@code true} (only the latest available
     * journal entry must have the tag), this Source needs an additional DB query per found {@code batchSize} PIDs. So
     * e.g. one additional DB query for each 500 (if that is the {@code batchSize}) PIDs containing the {@code tag} in
     * any journal entry.
     * @return Source of all persistence IDs such that each element contains the persistence IDs in {@code batchSize}
     * events that do not occur in prior buckets.
     */
    public Source<String, NotUsed> getJournalPidsWithTag(final String tag,
            final int batchSize,
            final Duration maxIdleTime,
            final Materializer mat,
            final boolean considerOnlyLatest) {

        final int maxRestarts = computeMaxRestarts(maxIdleTime);
        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal -> listPidsInJournal(journal, "", tag, batchSize, mat, maxRestarts)
                        .mapConcat(pids -> pids)
                        .grouped(batchSize)
                        .flatMapConcat(pids -> {
                            if (considerOnlyLatest) {
                                return filterPidsThatDoesntContainTagInNewestEntry(journal, pids, tag);
                            } else {
                                return Source.from(pids);
                            }
                        }));
    }

    private Source<String, NotUsed> filterPidsThatDoesntContainTagInNewestEntry(final MongoCollection<Document> journal,
            final List<String> pids, final String tag) {
        return Source.fromPublisher(journal.aggregate(List.of(
                        Aggregates.match(Filters.in(J_PROCESSOR_ID, pids)),
                        Aggregates.sort(Sorts.descending(J_TO)),
                        Aggregates.group(
                                "$" + J_PROCESSOR_ID,
                                toFirstJournalEntryFields(Set.of(J_PROCESSOR_ID, J_TAGS))
                        ),
                        Aggregates.match(Filters.eq(J_TAGS, tag)),
                        Aggregates.sort(Sorts.ascending(J_PROCESSOR_ID))
                )))
                .flatMapConcat(document -> {
                    final Object objectPid = document.get(J_PROCESSOR_ID);
                    if (objectPid instanceof CharSequence) {
                        return Source.single(objectPid.toString());
                    } else {
                        return Source.empty();
                    }
                });
    }

    /**
     * Retrieve all unique PIDs in journals selected by a provided {@code tag}.
     * The PIDs are ordered based on the {@link #PRIORITY_TAG_PREFIX} tags of the events: Descending by the appended
     * priority (an integer value).
     *
     * @param tag the Tag name the journal entries have to contain in order to be selected, or an empty string to select
     * all journal entries.
     * @param maxIdleTime how long the stream is allowed to idle without sending any element. Bounds the number of
     * retries with exponential back-off.
     * @return Source of all persistence IDs tagged with the provided {@code tag}, sorted ascending by the value of an
     * additional {@link #PRIORITY_TAG_PREFIX} tag.
     */
    public Source<String, NotUsed> getJournalPidsWithTagOrderedByPriorityTag(final String tag,
            final Duration maxIdleTime) {

        final int maxRestarts = computeMaxRestarts(maxIdleTime);
        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listPidsInJournalOrderedByPriorityTag(journal, tag, maxRestarts)
                );
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
                        listPidsInJournal(journal, lowerBoundPid, "", batchSize, mat, 0)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Retrieves all tags (by looking at the last persisted event) of the passed {@code pid} in the
     * journal entries in the newest journal entry for that pid.
     *
     * @param pid the persistence id to look up the most recent tags for, e.g.: {@code connection:123456}
     * @return a Source of tags of the pid or an empty source if either the pid or no tags could be found
     */
    public Source<String, NotUsed> getMostRecentJournalTagsForPid(final String pid) {

        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listJournalEntryTags(journal, pid)
                )
                .mapConcat(tags -> tags);
    }

    /**
     * Retrieve all unique PIDs in journals selected by a provided {@code tag} above a lower bound.
     * Does its best not to create long-living cursors on the database by reading {@code batchSize} events per query.
     *
     * @param lowerBoundPid the lower-bound PID.
     * @param tag the Tag name the journal entries have to contain in order to be selected.
     * @param batchSize how many events to read in one query.
     * @param mat the actor materializer to run the query streams.
     * @return Source of all persistence IDs such that each element contains the persistence IDs in {@code batchSize}
     * events that do not occur in prior buckets.
     */
    public Source<String, NotUsed> getJournalPidsAboveWithTag(final String lowerBoundPid,
            final String tag,
            final int batchSize,
            final Materializer mat) {

        return getJournal().withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(journal ->
                        listPidsInJournal(journal, lowerBoundPid, tag, batchSize, mat, 0)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * A Source retrieving a single revision/sequence number of type {@code long} for the last snapshot sequence number
     * available for the passed {@code pid} and before the passed {@code timestamp}.
     *
     * @param pid the persistenceId to find out the last snapshot sequence number for.
     * @param timestamp the timestamp to use as selection criteria for the snapshot sequence number to find out.
     * @return a Source of a single element with the determined snapshot sequence number.
     */
    public Source<Long, NotUsed> getLastSnapshotSequenceNumberBeforeTimestamp(final String pid,
            final Instant timestamp) {

        final Bson filter = Filters.and(
                Filters.eq(S_PROCESSOR_ID, pid),
                Filters.lte(S_TS, timestamp.toEpochMilli())
        );
        return getSnapshotStore().flatMapConcat(snaps -> Source.fromPublisher(snaps
                .find(filter)
                .projection(Projections.include(S_SN))
                .sort(Sorts.descending(S_SN))
                .first()
        )).map(document -> document.getLong(S_SN));
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

        return getNewestSnapshotsAbove(lowerBoundPid, batchSize, false, Duration.ZERO, mat, snapshotFields);
    }

    /**
     * Retrieve all latest snapshots with unique PIDs in snapshot store above a lower bound.
     * Does not limit database access in any way.
     *
     * @param lowerBoundPid the lower-bound PID.
     * @param batchSize how many snapshots to read in 1 query.
     * @param includeDeleted whether to include deleted snapshots.
     * @param minAgeFromNow the minimum age (based on {@code Instant.now()}) the snapshot must have in order to get
     * selected.
     * @param mat the materializer.
     * @param snapshotFields snapshot fields to project out.
     * @return source of newest snapshots with unique PIDs.
     */
    public Source<Document, NotUsed> getNewestSnapshotsAbove(final String lowerBoundPid,
            final int batchSize,
            final boolean includeDeleted,
            final Duration minAgeFromNow,
            final Materializer mat,
            final String... snapshotFields) {

        return getSnapshotStore()
                .withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(snapshotStore ->
                        listNewestSnapshots(snapshotStore,
                                SnapshotFilter.of(lowerBoundPid, minAgeFromNow),
                                batchSize,
                                includeDeleted,
                                mat,
                                snapshotFields
                        )
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Retrieve all latest snapshots with unique PIDs in snapshot store above a lower bound.
     * Does not limit database access in any way.
     *
     * @param snapshotFilter filter applied when streaming snapshots
     * @param batchSize how many snapshots to read in 1 query.
     * @param mat the materializer.
     * @param snapshotFields snapshot fields to project out.
     * @return source of newest snapshots with unique PIDs.
     */
    public Source<Document, NotUsed> getNewestSnapshotsAbove(
            final SnapshotFilter snapshotFilter,
            final int batchSize,
            final Materializer mat,
            final String... snapshotFields) {

        return getSnapshotStore()
                .withAttributes(Attributes.inputBuffer(1, 1))
                .flatMapConcat(snapshotStore ->
                        listNewestSnapshots(snapshotStore, snapshotFilter, batchSize, false, mat, snapshotFields)
                )
                .mapConcat(pids -> pids);
    }

    /**
     * Find the smallest event sequence number of a PID.
     *
     * @param pid the PID to search for.
     * @return source of the smallest event sequence number, or an empty optional.
     */
    public Source<Optional<Long>, NotUsed> getSmallestEventSeqNo(final String pid) {
        return getJournal()
                .flatMapConcat(journal -> Source.fromPublisher(
                        journal.find(Filters.eq(J_PROCESSOR_ID, pid))
                                .sort(Sorts.ascending(J_TO))
                                .limit(1)
                ))
                .map(document -> Optional.of(document.getLong(J_TO)))
                .orElse(Source.single(Optional.empty()));
    }

    /**
     * Find the smallest snapshot sequence number of a PID.
     *
     * @param pid the PID to search for.
     * @return source of the smallest snapshot sequence number, or an empty optional.
     */
    public Source<Optional<Long>, NotUsed> getSmallestSnapshotSeqNo(final String pid) {
        return getSnapshotStore()
                .flatMapConcat(snaps -> Source.fromPublisher(
                        snaps.find(Filters.eq(S_PROCESSOR_ID, pid))
                                .sort(Sorts.ascending(S_SN))
                                .limit(1)
                ))
                .map(document -> Optional.of(document.getLong(S_SN)))
                .orElse(Source.single(Optional.empty()));
    }

    /**
     * Delete events of a PID.
     *
     * @param pid the PID.
     * @param minSeqNr minimum sequence number to delete (inclusive).
     * @param maxSeqNr maximum sequence number to delete (inclusive).
     * @return source of the delete result.
     */
    public Source<DeleteResult, NotUsed> deleteEvents(final String pid, final long minSeqNr, final long maxSeqNr) {

        final Bson filter = Filters.and(Filters.eq(J_PROCESSOR_ID, pid),
                Filters.gte(J_TO, minSeqNr),
                Filters.lte(J_TO, maxSeqNr));
        return getJournal()
                .flatMapConcat(journal -> Source.fromPublisher(journal.deleteMany(filter)));
    }

    /**
     * Delete snapshots of a PID.
     *
     * @param pid the PID.
     * @param minSeqNr minimum sequence number to delete (inclusive).
     * @param maxSeqNr maximum sequence number to delete (inclusive).
     * @return source of the delete result.
     */
    public Source<DeleteResult, NotUsed> deleteSnapshots(final String pid, final long minSeqNr, final long maxSeqNr) {

        final Bson filter = Filters.and(Filters.eq(S_PROCESSOR_ID, pid),
                Filters.gte(S_SN, minSeqNr),
                Filters.lte(S_SN, maxSeqNr));
        return getSnapshotStore()
                .flatMapConcat(snaps -> Source.fromPublisher(snaps.deleteMany(filter)));
    }


    @Override
    public Source<EventEnvelope, NotUsed> currentEventsByPersistenceId(final String persistenceId,
            final long fromSequenceNr,
            final long toSequenceNr) {
        return akkaReadJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr);
    }

    @Override
    public Source<EventEnvelope, NotUsed> currentEventsByTag(final String tag, final Offset offset) {
        return akkaReadJournal.currentEventsByTag(tag, offset);
    }

    @Override
    public Source<String, NotUsed> currentPersistenceIds() {
        return akkaReadJournal.currentPersistenceIds();
    }

    @Override
    public Source<EventEnvelope, NotUsed> eventsByPersistenceId(final String persistenceId, final long fromSequenceNr,
            final long toSequenceNr) {
        return akkaReadJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr);
    }

    @Override
    public Source<EventEnvelope, NotUsed> eventsByTag(final String tag, final Offset offset) {
        return akkaReadJournal.eventsByTag(tag, offset);
    }

    @Override
    public Source<String, NotUsed> persistenceIds() {
        return akkaReadJournal.persistenceIds();
    }

    private Source<List<String>, NotUsed> listPidsInJournal(final MongoCollection<Document> journal,
            final String lowerBoundPid,
            final String tag,
            final int batchSize,
            final Materializer mat,
            final int maxRestarts) {

        return unfoldBatchedSource(lowerBoundPid, mat, Function.identity(), actualStartPid ->
                listJournalPidsAbove(journal, actualStartPid, tag, batchSize, maxRestarts)
        );
    }

    private Source<String, NotUsed> listJournalPidsAbove(final MongoCollection<Document> journal,
            final String startPid,
            final String tag,
            final int batchSize,
            final int maxRestarts) {

        return listLatestJournalEntries(journal, startPid, tag, batchSize, maxRestarts, J_EVENT_PID)
                .flatMapConcat(document -> {
                    final Object pid = document.get(J_EVENT_PID);
                    if (pid instanceof CharSequence) {
                        return Source.single(pid.toString());
                    } else {
                        return Source.empty();
                    }
                });
    }

    private Source<List<Document>, NotUsed> listNewestSnapshots(final MongoCollection<Document> snapshotStore,
            final SnapshotFilter filter,
            final int batchSize,
            final boolean includeDeleted,
            final Materializer mat,
            final String... snapshotFields) {

        return unfoldBatchedSource(filter.lowerBoundPid(),
                mat,
                SnapshotBatch::maxPid,
                actualStartPid -> listNewestActiveSnapshotsByBatch(snapshotStore,
                        filter.withLowerBound(actualStartPid),
                        batchSize,
                        includeDeleted,
                        snapshotFields
                )
        )
                .mapConcat(x -> x)
                .map(SnapshotBatch::items);
    }

    private <T> Source<List<T>, NotUsed> unfoldBatchedSource(
            final String lowerBoundPid,
            final Materializer mat,
            final Function<T, String> seedCreator,
            final Function<String, Source<T, ?>> sourceCreator) {

        return Source.unfoldAsync("",
                        startPid -> {
                            final String actualStart = lowerBoundPid.compareTo(startPid) >= 0 ? lowerBoundPid : startPid;
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

    private static Source<String, NotUsed> listPidsInJournalOrderedByPriorityTag(
            final MongoCollection<Document> journal,
            final String tag,
            final int maxRestarts) {

        final List<Bson> pipeline = new ArrayList<>(4);
        // optional match stages: consecutive match stages are optimized together ($match + $match coalescence)
        if (!tag.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.eq(J_TAGS, tag)));
        }

        // group stage. We can assume that the $last element ist also new latest event because of the insert order.
        pipeline.add(Aggregates.group("$" + J_PROCESSOR_ID, Accumulators.last(J_TAGS, "$" + J_TAGS)));

        // Filter irrelevant tags for priority ordering.
        final BsonDocument arrayFilter = BsonDocument.parse(
                "{\n" +
                        "    $filter: {\n" +
                        "        input: \"$" + J_TAGS + "\",\n" +
                        "        as: \"tags\",\n" +
                        "        cond: {\n" +
                        "            $eq: [\n" +
                        "                {\n" +
                        "                    $substrCP: [\"$$tags\", 0, " + PRIORITY_TAG_PREFIX.length() + "]\n" +
                        "                }\n," +
                        "                \"" + PRIORITY_TAG_PREFIX + "\"\n" +
                        "            ]\n" +
                        "        }\n" +
                        "    }\n" +
                        "}"
        );
        pipeline.add(Aggregates.project(Projections.computed(J_TAGS, arrayFilter)));

        // sort stage 2 -- order after group stage is not defined
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.descending(J_TAGS))));

        final Duration minBackOff = Duration.ofSeconds(1L);
        final double randomFactor = 0.1;

        final RestartSettings restartSettings = RestartSettings.create(minBackOff,
                        MongoReadJournal.MAX_BACK_OFF_DURATION, randomFactor)
                .withMaxRestarts(maxRestarts, minBackOff);
        return RestartSource.onFailuresWithBackoff(restartSettings, () ->
                Source.fromPublisher(journal.aggregate(pipeline)
                                .collation(Collation.builder().locale("en_US").numericOrdering(true).build()))
                        .flatMapConcat(document -> {
                            final Object pid = document.get(J_ID);
                            if (pid instanceof CharSequence) {
                                return Source.single(pid.toString());
                            } else {
                                return Source.empty();
                            }
                        })
        );
    }

    private static Source<Document, NotUsed> listLatestJournalEntries(final MongoCollection<Document> journal,
            final String startPid,
            final String tag,
            final int batchSize,
            final int maxRestarts,
            final String... fieldNames) {

        final List<Bson> pipeline = new ArrayList<>(6);
        // optional match stages: consecutive match stages are optimized together ($match + $match coalescence)
        if (!tag.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.eq(J_TAGS, tag)));
        }
        if (!startPid.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.gt(J_PROCESSOR_ID, startPid)));
        }

        // sort stage
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.ascending(J_PROCESSOR_ID), Sorts.descending(J_TO))));

        // limit stage. It should come before group stage or MongoDB would scan the entire journal collection.
        pipeline.add(Aggregates.limit(batchSize));

        final Set<String> fieldNamesWithOptionalTags = Arrays.stream(fieldNames).collect(Collectors.toSet());
        // group stage
        pipeline.add(Aggregates.group("$" + J_PROCESSOR_ID, toFirstJournalEntryFields(fieldNamesWithOptionalTags)));

        // sort stage 2 -- order after group stage is not defined
        pipeline.add(Aggregates.sort(Sorts.ascending(J_ID)));

        final Duration minBackOff = Duration.ofSeconds(1L);
        final double randomFactor = 0.1;

        final RestartSettings restartSettings = RestartSettings.create(minBackOff,
                        MongoReadJournal.MAX_BACK_OFF_DURATION, randomFactor)
                .withMaxRestarts(maxRestarts, minBackOff);
        return RestartSource.onFailuresWithBackoff(restartSettings, () ->
                Source.fromPublisher(journal.aggregate(pipeline)
                        .batchSize(batchSize) // use batchSize also for the cursor batchSize (16 by default bc of backpressure!)
                )
        );
    }

    private static List<BsonField> toFirstJournalEntryFields(final Collection<String> journalFields) {
        return journalFields.stream()
                .map(fieldName -> {
                    final String serializedFieldName = String.format("$%s.%s", J_EVENT, fieldName);
                    final BsonArray bsonArray =
                            new BsonArray(List.of(new BsonString(serializedFieldName), new BsonInt32(0)));
                    return Accumulators.first(fieldName, new BsonDocument().append("$arrayElemAt", bsonArray));
                })
                .toList();
    }

    private static int computeMaxRestarts(final Duration maxDuration) {
        if (MAX_BACK_OFF_DURATION.minus(maxDuration).isNegative()) {
            // maxBackOff < maxDuration: backOff at least 7 times (1+2+4+8+16+32+64=127s)
            return Math.max(7, 6 + (int) (maxDuration.toMillis() / MAX_BACK_OFF_DURATION.toMillis()));
        } else {
            // maxBackOff >= maxDuration: maxRestarts = log2 of maxDuration in seconds
            final int log2MaxDuration = 63 - Long.numberOfLeadingZeros(maxDuration.getSeconds());
            return Math.max(0, log2MaxDuration);
        }
    }

    private static Source<SnapshotBatch, NotUsed> listNewestActiveSnapshotsByBatch(
            final MongoCollection<Document> snapshotStore,
            final SnapshotFilter snapshotFilter,
            final int batchSize,
            final boolean includeDeleted,
            final String... snapshotFields) {

        final List<Bson> pipeline = new ArrayList<>(5);
        // match stage
        pipeline.add(Aggregates.match(snapshotFilter.toMongoFilter()));

        // sort stage
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.ascending(S_PROCESSOR_ID), Sorts.descending(S_SN))));

        // limit stage. It should come before group stage or MongoDB would scan the entire journal collection.
        pipeline.add(Aggregates.limit(batchSize));

        // group stage 1: by PID. PID is from now on in field _id (S_ID)
        pipeline.add(Aggregates.group("$" + S_PROCESSOR_ID, asFirstSnapshotBsonFields(snapshotFields)));

        // sort stage 2 -- order after group stage is not defined
        pipeline.add(Aggregates.sort(Sorts.ascending(S_ID)));

        // group stage 2: filter out pids whose latest snapshot is a deleted snapshot, but retain max encountered pid
        // ---- or ---- : include latest deleted snapshots
        final String maxPid = "m";
        final String items = "i";
        pipeline.add(Aggregates.group(null,
                Accumulators.max(maxPid, "$" + S_ID),
                Accumulators.push(
                        items,
                        new Document().append("$cond", new Document()
                                .append("if",
                                        new Document().append("$ne", Arrays.asList("$" + LIFECYCLE, "DELETED")))
                                .append("then", "$$CURRENT")
                                .append("else", includeDeleted ? "$$CURRENT" : null)
                        ))
        ));

        // remove null entries by projection
        if (!includeDeleted) {
            pipeline.add(Aggregates.project(new Document()
                    .append(maxPid, 1)
                    .append(items, new Document()
                            .append("$setDifference", Arrays.asList("$" + items, Collections.singletonList(null)))
                    )
            ));
        }

        return Source.fromPublisher(snapshotStore.aggregate(pipeline)
                        .batchSize(batchSize) // use batchSize also for the cursor batchSize (16 by default bc of backpressure!)
                )
                .flatMapConcat(document -> {
                    final String theMaxPid = document.getString(maxPid);
                    if (theMaxPid == null) {
                        return Source.empty();
                    } else {
                        final SnapshotBatch snapshotBatch =
                                new SnapshotBatch(theMaxPid, document.getList(items, Document.class));
                        return Source.single(snapshotBatch);
                    }
                });
    }

    private static Source<List<String>, NotUsed> listJournalEntryTags(final MongoCollection<Document> journal,
            final String pid) {

        return Source.fromPublisher(journal.find(Filters.eq(J_PROCESSOR_ID, pid))
                        .sort(Sorts.descending(J_TO))
                        .limit(1)
                )
                .map(document -> Optional.ofNullable(document.getList(J_TAGS, String.class))
                        .orElse(List.of()))
                .orElse(Source.single(List.of()));
    }

    /**
     * For $group stage of an aggregation pipeline over a snapshot collection: take the newest values of fields
     * of serialized snapshots. Always include the first snapshot lifecycle.
     *
     * @param snapshotFields fields of a serialized snapshot to project.
     * @return list of group stage field accumulators.
     */
    private static List<BsonField> asFirstSnapshotBsonFields(final String... snapshotFields) {
        final Stream<BsonField> snFieldStream = Stream.of(Accumulators.first(S_SN, "$" + S_SN));
        final Stream<BsonField> snapshotFieldStream =
                Stream.concat(Stream.of(LIFECYCLE), Arrays.stream(snapshotFields))
                        .map(fieldName -> {
                            final String serializedFieldName =
                                    String.format("$%s.%s", S_SERIALIZED_SNAPSHOT, fieldName);
                            return Accumulators.first(fieldName, serializedFieldName);
                        });

        return Stream.concat(snFieldStream, snapshotFieldStream).toList();
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

    private record SnapshotBatch(String maxPid, List<Document> items) {

    }

}
