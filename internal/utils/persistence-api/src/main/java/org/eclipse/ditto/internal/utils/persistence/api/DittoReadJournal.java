/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import org.apache.pekko.persistence.query.javadsl.CurrentEventsByTagQuery;
import org.apache.pekko.persistence.query.javadsl.CurrentPersistenceIdsQuery;
import org.apache.pekko.persistence.query.javadsl.EventsByPersistenceIdQuery;
import org.apache.pekko.persistence.query.javadsl.EventsByTagQuery;
import org.apache.pekko.persistence.query.javadsl.PersistenceIdsQuery;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.json.JsonObject;

/**
 * Backend-agnostic read journal abstraction. Combines Pekko's standard read-journal SPI with
 * the Ditto-specific operations used for cleanup, namespace ops, and historical queries.
 * <p>
 * Implementations are provided per backend (MongoDB, PostgreSQL, ...) and selected at runtime
 * by the configured {@link PersistenceBackendProvider}.
 * <p>
 * All journal/snapshot entries and delete results crossing this interface are expressed through
 * backend-neutral DTOs ({@link JournalEntry}, {@link SnapshotEntry}, {@link DeleteOutcome}) so that
 * no backend driver type (e.g. {@code org.bson.Document}) leaks to consumers.
 *
 * @since 4.0.0
 */
public interface DittoReadJournal extends
        CurrentEventsByPersistenceIdQuery,
        CurrentEventsByTagQuery,
        CurrentPersistenceIdsQuery,
        EventsByPersistenceIdQuery,
        EventsByTagQuery,
        PersistenceIdsQuery {

    // ---------------------------------------------------------------------------------------
    // Ditto-convention constants (backend-neutral)
    // ---------------------------------------------------------------------------------------

    /**
     * Prefix of the priority tag used by
     * {@link #getJournalPidsWithTagOrderedByPriorityTag(String, java.time.Duration)} for ordering.
     */
    String PRIORITY_TAG_PREFIX = "priority-";

    /**
     * Tag marking an entity that should always be kept alive (never passivated by background cleanup).
     */
    String JOURNAL_TAG_ALWAYS_ALIVE = "always-alive";

    /**
     * Lifecycle marker field of snapshot entries.
     */
    String LIFECYCLE = "__lifecycle";

    /**
     * Persistence-id field of snapshot entries.
     */
    String S_ID = "_id";

    /**
     * Sequence-number field of snapshot entries.
     */
    String S_SN = "sn";

    // ---------------------------------------------------------------------------------------
    // Index management
    // ---------------------------------------------------------------------------------------

    CompletionStage<Done> ensureTagPidIndex();

    CompletionStage<Done> ensureSnapshotCollectionPidIdIndex();

    CompletionStage<Done> ensureSnapshotCollectionPidSnIndex();

    CompletionStage<Done> ensureSnapshotCollectionPidSnIdIndex();

    // ---------------------------------------------------------------------------------------
    // PID retrieval
    // ---------------------------------------------------------------------------------------

    Source<String, NotUsed> getJournalPids(int batchSize, Duration maxIdleTime, Materializer mat);

    Source<JournalEntry, NotUsed> getLatestJournalEntries(int batchSize, Duration maxIdleTime, Materializer mat);

    Source<String, NotUsed> getJournalPidsWithTag(String tag,
            int batchSize,
            Duration maxIdleTime,
            Materializer mat,
            boolean considerOnlyLatest);

    Source<String, NotUsed> getJournalPidsWithTagOrderedByPriorityTag(String tag, Duration maxIdleTime);

    Source<String, NotUsed> getJournalPidsAbove(String lowerBoundPid, int batchSize, Materializer mat);

    Source<String, NotUsed> getMostRecentJournalTagsForPid(String pid);

    Source<String, NotUsed> getJournalPidsAboveWithTag(String lowerBoundPid,
            String tag,
            int batchSize,
            Materializer mat);

    // ---------------------------------------------------------------------------------------
    // Snapshot retrieval
    // ---------------------------------------------------------------------------------------

    Source<Long, NotUsed> getLastSnapshotSequenceNumberBeforeTimestamp(String pid, Instant timestamp);

    Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(String lowerBoundPid,
            int batchSize,
            Materializer mat,
            String... snapshotFields);

    Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(String lowerBoundPid,
            int batchSize,
            boolean includeDeleted,
            Duration minAgeFromNow,
            Materializer mat,
            String... snapshotFields);

    Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(SnapshotFilter snapshotFilter,
            int batchSize,
            Materializer mat,
            String... snapshotFields);

    Source<Optional<Long>, NotUsed> getSmallestEventSeqNo(String pid);

    Source<Optional<Long>, NotUsed> getLatestEventSeqNo(String pid);

    Source<Optional<Long>, NotUsed> getSmallestSnapshotSeqNo(String pid);

    // ---------------------------------------------------------------------------------------
    // Deletion (cleanup)
    // ---------------------------------------------------------------------------------------

    Source<DeleteOutcome, NotUsed> deleteEvents(String pid, long minSeqNr, long maxSeqNr);

    Source<DeleteOutcome, NotUsed> deleteSnapshots(String pid, long minSeqNr, long maxSeqNr);

    // ---------------------------------------------------------------------------------------
    // Pekko ReadJournal SPI overrides
    // ---------------------------------------------------------------------------------------

    @Override
    Source<EventEnvelope, NotUsed> currentEventsByPersistenceId(String persistenceId,
            long fromSequenceNr,
            long toSequenceNr);

    @Override
    Source<EventEnvelope, NotUsed> currentEventsByTag(String tag, Offset offset);

    @Override
    Source<String, NotUsed> currentPersistenceIds();

    @Override
    Source<EventEnvelope, NotUsed> eventsByPersistenceId(String persistenceId,
            long fromSequenceNr,
            long toSequenceNr);

    @Override
    Source<EventEnvelope, NotUsed> eventsByTag(String tag, Offset offset);

    @Override
    Source<String, NotUsed> persistenceIds();

    // ---------------------------------------------------------------------------------------
    // Event payload mapping
    // ---------------------------------------------------------------------------------------

    /**
     * Converts the backend-specific event payload carried by an {@link EventEnvelope} into a
     * backend-neutral {@link JsonObject}.
     * <p>
     * The Mongo backend converts the BSON payload via {@code DittoBsonJson}; a Postgres backend
     * converts the JSONB/text payload. Consumers must use this method instead of casting
     * {@link EventEnvelope#event()} to a backend-specific type, which would throw
     * {@link ClassCastException} under a non-matching backend.
     *
     * @param eventEnvelope the event envelope whose {@link EventEnvelope#event() payload} should be converted.
     * @return the event payload as a backend-neutral JSON object.
     */
    JsonObject toEventJson(EventEnvelope eventEnvelope);

}
