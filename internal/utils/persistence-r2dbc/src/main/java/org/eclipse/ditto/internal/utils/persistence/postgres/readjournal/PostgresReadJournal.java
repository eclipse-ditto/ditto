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
package org.eclipse.ditto.internal.utils.persistence.postgres.readjournal;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.persistence.api.DeleteOutcome;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.JournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.ReadJournalRestarts;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotFilter;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.JournalRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations.PrioritizedPid;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations.WindowedPid;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.SnapshotHead;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.SnapshotRow;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.R2dbcException;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.event.Logging;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.Sequence;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.RestartSettings;
import org.apache.pekko.stream.SharedKillSwitch;
import org.apache.pekko.stream.javadsl.RestartSource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * PostgreSQL {@link DittoReadJournal} built on the shared {@link PostgresPersistenceOperations}.
 * <p>
 * Every one of the interface's abstract methods has an explicit disposition — real SQL, an intentional no-op, or a
 * documented stub — and a contract test asserts none throws {@code UnsupportedOperationException} at runtime. The
 * 4 {@code ensure*Index} methods are documented no-ops because the DDL (including the GIN tag index and the snapshot
 * indexes) is created upfront by {@code PostgresSchemaManager}; they complete successfully so callers that
 * await index creation are unblocked.
 * </p>
 * <p>
 * Offset decision: {@code eventsByTag}/{@code currentEventsByTag} build {@code Offset.sequence(seq)}
 * from the {@code seq BIGINT GENERATED ALWAYS AS IDENTITY} column — a monotonic global order. (Commit-visibility gaps
 * are possible under concurrency, as for any IDENTITY-based offset; there is no in-repo consumer of the tag offset
 * today, so this is the documented forward-compatible choice rather than {@code Offset.noOffset()}.)
 * </p>
 */
public final class PostgresReadJournal implements DittoReadJournal {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresReadJournal.class);

    /**
     * Pids examined per statement of the priority order: the shipped {@code read-journal-batch-size} of both pings.
     * The interface method has no batch size; {@link #getJournalPidsWithTagOrderedByPriorityTag(String, int, Duration)}
     * takes one.
     */
    public static final int PRIORITY_WALK_WINDOW = 500;

    /** Highest priority first; {@link List#sort} is stable, so pids of equal priority keep the walk's pid order. */
    private static final Comparator<PrioritizedPid> HIGHEST_PRIORITY_FIRST =
            Comparator.comparingInt(PrioritizedPid::priority).reversed();

    private final PostgresPersistenceOperations operations;

    private PostgresReadJournal(final PostgresPersistenceOperations operations) {
        this.operations = checkNotNull(operations, "operations");
    }

    /**
     * @param operations the shared persistence operations bound to this read journal's entity tables.
     * @return the read journal.
     */
    public static PostgresReadJournal of(final PostgresPersistenceOperations operations) {
        return new PostgresReadJournal(operations);
    }

    // -------------------------------------------------------------------------------------------------
    // Index management — documented NO-OPS: DDL is created upfront by PostgresSchemaManager.
    // -------------------------------------------------------------------------------------------------

    @Override
    public CompletionStage<Done> ensureTagPidIndex() {
        return alreadyEnsured();
    }

    @Override
    public CompletionStage<Done> ensureSnapshotCollectionPidIdIndex() {
        return alreadyEnsured();
    }

    @Override
    public CompletionStage<Done> ensureSnapshotCollectionPidSnIndex() {
        return alreadyEnsured();
    }

    @Override
    public CompletionStage<Done> ensureSnapshotCollectionPidSnIdIndex() {
        return alreadyEnsured();
    }

    private static CompletionStage<Done> alreadyEnsured() {
        // No-op: indexes are part of the upfront DDL bootstrap. Complete so index-awaiting callers proceed.
        return CompletableFuture.completedFuture(Done.getInstance());
    }

    // -------------------------------------------------------------------------------------------------
    // PID retrieval
    // -------------------------------------------------------------------------------------------------

    /**
     * Streams the FULL result set by repeating a bounded keyset page query until an empty page arrives — the contract
     * of {@code MongoReadJournal#unfoldBatchedSource}: every page is one short query that is read completely before any
     * of its rows is emitted, so no statement, transaction or snapshot spans the consumer's pace. The cursor of the
     * next page is derived from the LAST row of the raw page (before any caller-side filtering), so filtered-out
     * trailing rows cannot stall it, and it only moves forward: a pid is never delivered twice by an ID-order walk.
     * That relies on each page arriving in the statement's order, which the page queries of
     * {@link PostgresPersistenceOperations} guarantee by collecting the whole page before emitting it.
     * <p>
     * A failed page is retried as a whole, with exponential back-off, from the same cursor ({@link PageRetry}); rows
     * of a failed attempt are discarded, so a retry neither repeats nor skips rows. When the retries are exhausted
     * the stream fails with the page's error. When the stream ends (completed, failed or cancelled by its consumer),
     * a page that is still being retried is stopped.
     * </p>
     *
     * @param streamName names the stream in failure logs.
     * @param firstCursor the cursor of the first page.
     * @param pageQuery builds the page query after a cursor.
     * @param cursorOf derives the next cursor from the last row of a page.
     * @param retry how failed pages are retried.
     */
    private static <C, T> Source<List<T>, NotUsed> keysetPages(final String streamName,
            final C firstCursor,
            final Function<C, Source<T, NotUsed>> pageQuery,
            final Function<T, C> cursorOf,
            final PageRetry retry) {
        return Source.fromMaterializer((mat, attributes) -> {
                    final SharedKillSwitch pageRetries = KillSwitches.shared(streamName);
                    return Source.unfoldAsync(firstCursor,
                                    cursor -> retry.fetch(streamName, cursor, () -> pageQuery.apply(cursor),
                                                    pageRetries, mat)
                                            .thenApply(page -> page.isEmpty()
                                                    ? Optional.<Pair<C, List<T>>>empty()
                                                    : Optional.of(Pair.create(
                                                            cursorOf.apply(page.get(page.size() - 1)), page))))
                            .withAttributes(Attributes.inputBuffer(1, 1))
                            .watchTermination((notUsed, done) -> stopOnTermination(notUsed, done, pageRetries));
                })
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    private static <M> M stopOnTermination(final M materialized, final CompletionStage<Done> streamTermination,
            final SharedKillSwitch retries) {
        streamTermination.whenComplete((done, error) -> retries.shutdown());
        return materialized;
    }

    @Override
    public Source<String, NotUsed> getJournalPids(final int batchSize, final Duration maxIdleTime,
            final Materializer mat) {
        return getJournalPidsAbove("", batchSize, mat);
    }

    @Override
    public Source<JournalEntry, NotUsed> getLatestJournalEntries(final int batchSize, final Duration maxIdleTime,
            final Materializer mat) {
        // The newest event per pid as a backend-neutral JournalEntry (pid + manifest + payload JSON), via a single
        // loose-index-scan streaming query (distinct-pid phase + per-pid top-1 LATERAL) — NOT an N+1 per-pid replay.
        return operations.getLatestJournalEntries()
                .map(row -> JournalEntry.of(row.pid(), row.manifest(), parse(row.eventJson())));
    }

    /**
     * The pids carrying {@code tag}, in pid order ({@code COLLATE "C"}), read window by window: each statement
     * examines the next {@code batchSize} distinct pids after the previous window, tagged or not, and the stream emits
     * those that qualify (see {@link PostgresPersistenceOperations#getPidWindowAbove}). Every statement is short and
     * read completely before its pids are emitted (as {@code MongoReadJournal} reads one page per query), so a
     * throttled consumer such as the persistence ping holds no statement, transaction or snapshot between statements,
     * and {@code statement_timeout} applies to one window at a time.
     * <p>
     * On PostgreSQL {@code batchSize} bounds the pids EXAMINED per statement, not the pids delivered: a window over
     * untagged pids delivers none and the walk moves on, so the cost of a statement does not depend on how the tagged
     * pids are spread over the key space (MongoDB pages over the tagged entries through its tag index; the PostgreSQL
     * schema has no index on (tag, pid)). A walk takes (distinct pids / batchSize) statements.
     * </p>
     * <p>
     * Without {@code considerOnlyLatest} a pid qualifies if any of its events carries the tag; with it, only if its
     * newest event does. Changes made while the stream runs, relative to the last window READ (at most one window ahead
     * of what the consumer has received): a pid that starts to qualify after it is delivered, before it in the next
     * walk; a pid deleted after it is not delivered; no pid is delivered twice.
     * </p>
     * <p>
     * A failed window is retried with exponential back-off from the same cursor; {@code maxIdleTime} bounds the number
     * of retries with the formula of the MongoDB read journal ({@link ReadJournalRestarts#computeMaxRestarts}). When
     * they are exhausted, the stream fails with the window's error.
     * </p>
     * <p>
     * {@code mat} is not used: the windows are read with the materializer that runs the returned source.
     * </p>
     */
    @Override
    public Source<String, NotUsed> getJournalPidsWithTag(final String tag, final int batchSize,
            final Duration maxIdleTime, final Materializer mat, final boolean considerOnlyLatest) {
        return pidWindows(considerOnlyLatest
                        ? "pids with tag <" + tag + "> on the newest event"
                        : "pids with tag <" + tag + ">",
                "", tag, considerOnlyLatest, batchSize, PageRetry.forMaxIdleTime(maxIdleTime));
    }

    private Source<String, NotUsed> pidWindows(final String streamName, final String lowerBoundPid, final String tag,
            final boolean considerOnlyLatest, final int window, final PageRetry retry) {
        return keysetPages(streamName, lowerBoundPid,
                (String start) -> operations.getPidWindowAbove(start, tag, considerOnlyLatest, window),
                WindowedPid::pid, retry)
                .mapConcat(page -> page.stream()
                        .filter(WindowedPid::qualifies)
                        .map(WindowedPid::pid)
                        .toList());
    }

    /**
     * The pids carrying {@code tag} on any event ({@code ""}: every pid), highest priority first, in windows of
     * {@link #PRIORITY_WALK_WINDOW} pids; see
     * {@link #getJournalPidsWithTagOrderedByPriorityTag(String, int, Duration)}.
     */
    @Override
    public Source<String, NotUsed> getJournalPidsWithTagOrderedByPriorityTag(final String tag,
            final Duration maxIdleTime) {
        return getJournalPidsWithTagOrderedByPriorityTag(tag, PRIORITY_WALK_WINDOW, maxIdleTime);
    }

    /**
     * The pids carrying {@code tag} on any of their events ({@code ""}: every pid), highest priority first, pids of
     * equal priority in pid order ({@code COLLATE "C"}). The priority of a pid is the one of its newest event that
     * carries the tag (see {@link PostgresPersistenceOperations#getPrioritizedPidWindowAbove}). MongoDB returns the
     * same set in the same priority order, with equal priorities in no defined order.
     * <p>
     * The priority is computed from the tags, not stored, so no index orders the pids by it. The walk therefore reads
     * all pids window by window in pid order, as the ID order does ({@code window} pids per statement, each statement
     * short and read completely), keeps the qualifying pids with their priority, and sorts them in memory once the
     * last window is read; only then is the first pid emitted. Every statement costs what an ID-order window costs,
     * whatever share of the journal carries the tag, and a walk takes (distinct pids / window) + 1 statements. The
     * memory is one small record per qualifying pid (its pid and priority, about 100–120 bytes) until the walk has
     * emitted them: about 12 MB for 100,000 connections. MongoDB holds the same set on the server ({@code $group} and
     * {@code $sort} of its aggregation).
     * </p>
     * <p>
     * Every window reads the current state, so the walk sees each pid as of the window that examined it: a pid that
     * starts to qualify (created or tagged) in a part of the key space not read yet comes in this walk, otherwise in
     * the next one; a pid deleted after its window was read is still delivered (as on MongoDB, whose aggregation reads
     * once); no pid comes twice. A failed window is retried with exponential back-off from its own cursor, keeping the
     * windows read before it, as in {@link #getJournalPidsWithTag}; {@code maxIdleTime} bounds the retries of a window.
     * When they are exhausted the stream fails without having delivered any pid.
     * </p>
     *
     * @param tag the tag, or {@code ""} for every pid.
     * @param window the number of pids examined per statement.
     * @param maxIdleTime bounds the retries of a window ({@link ReadJournalRestarts#computeMaxRestarts}).
     * @return the pids, highest priority first.
     */
    public Source<String, NotUsed> getJournalPidsWithTagOrderedByPriorityTag(final String tag, final int window,
            final Duration maxIdleTime) {
        final String streamName = "pids with tag <" + tag + "> in priority order";
        final PageRetry retry = PageRetry.forMaxIdleTime(maxIdleTime);
        // a fresh list per materialisation: the same source may be run more than once
        return Source.fromMaterializer((mat, attributes) -> keysetPages(streamName, "",
                                (String start) -> operations.getPrioritizedPidWindowAbove(start, tag, window),
                                PrioritizedPid::pid, retry)
                        .fold(new ArrayList<PrioritizedPid>(), PostgresReadJournal::addQualifying)
                        .mapConcat(PostgresReadJournal::inPriorityOrder))
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    private static ArrayList<PrioritizedPid> addQualifying(final ArrayList<PrioritizedPid> collected,
            final List<PrioritizedPid> window) {
        for (final PrioritizedPid pid : window) {
            if (pid.qualifies()) {
                collected.add(pid);
            }
        }
        return collected;
    }

    private static List<String> inPriorityOrder(final ArrayList<PrioritizedPid> collected) {
        collected.sort(HIGHEST_PRIORITY_FIRST);
        return collected.stream().map(PrioritizedPid::pid).toList();
    }

    @Override
    public Source<String, NotUsed> getJournalPidsAbove(final String lowerBoundPid, final int batchSize,
            final Materializer mat) {
        // Cursor-paginated to exhaustion: batchSize is the page size, never a cap (Mongo parity, no retries).
        // mat is not used: the pages are read with the materializer that runs the returned source.
        return keysetPages("pids", lowerBoundPid,
                (String start) -> operations.getPidsAbove(start, batchSize),
                Function.identity(), PageRetry.NONE)
                .mapConcat(page -> page);
    }

    @Override
    public Source<String, NotUsed> getMostRecentJournalTagsForPid(final String pid) {
        return operations.getMostRecentTagsForPid(pid);
    }

    /**
     * Like {@link #getJournalPidsWithTag} (any tagged event, windows of {@code batchSize} pids), starting after
     * {@code lowerBoundPid}, without retries (MongoDB parity: 0 restarts for this variant). {@code mat} is not used.
     */
    @Override
    public Source<String, NotUsed> getJournalPidsAboveWithTag(final String lowerBoundPid, final String tag,
            final int batchSize, final Materializer mat) {
        return pidWindows("pids with tag <" + tag + "> above <" + lowerBoundPid + ">", lowerBoundPid, tag, false,
                batchSize, PageRetry.NONE);
    }

    // -------------------------------------------------------------------------------------------------
    // Snapshot retrieval
    // -------------------------------------------------------------------------------------------------

    @Override
    public Source<Long, NotUsed> getLastSnapshotSequenceNumberBeforeTimestamp(final String pid,
            final Instant timestamp) {
        return operations.getLastSnapshotSequenceNumberBeforeTimestamp(pid, timestamp);
    }

    @Override
    public Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(final String lowerBoundPid, final int batchSize,
            final Materializer mat, final String... snapshotFields) {
        return newestSnapshots(lowerBoundPid, "", batchSize, false, Duration.ZERO, mat, snapshotFields);
    }

    @Override
    public Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(final String lowerBoundPid, final int batchSize,
            final boolean includeDeleted, final Duration minAgeFromNow, final Materializer mat,
            final String... snapshotFields) {
        return newestSnapshots(lowerBoundPid, "", batchSize, includeDeleted, minAgeFromNow, mat, snapshotFields);
    }

    @Override
    public Source<SnapshotEntry, NotUsed> getNewestSnapshotsAbove(final SnapshotFilter snapshotFilter,
            final int batchSize, final Materializer mat, final String... snapshotFields) {
        return newestSnapshots(snapshotFilter.lowerBoundPid(), snapshotFilter.pidFilter(), batchSize, false,
                snapshotFilter.minAgeFromNow(), mat, snapshotFields);
    }

    /**
     * Newest-per-pid snapshot streaming, paginated to exhaustion (batchSize is the page size, never a cap —
     * Mongo parity). DELETED filtering happens HERE, post-newest-per-pid-selection (Mongo post-$group parity): a pid whose
     * newest snapshot is DELETED is dropped entirely instead of resurrecting an older live snapshot. The
     * pagination seed is taken from the raw page before filtering, so a page whose trailing pids are all
     * DELETED still advances the cursor.
     * <p>
     * The snapshot payload is fetched only when the caller requests snapshot fields. A stream without fields
     * (background cleanup, the connectivity service's retrieval of all connection ids) runs
     * {@link PostgresPersistenceOperations#getNewestSnapshotHeadsAbove}, which selects no payload, and its entries
     * carry {@link JsonObject#empty()} as payload, where MongoDB returns {@code {sn, __lifecycle}}; no caller in this
     * repository reads the payload of a stream without fields. Known limitation: with fields requested, the
     * projection is still not pushed into SQL: the whole payload is fetched and parsed (efficiency follow-up, out of
     * scope). The pid regex uses POSIX ERE ({@code ~}), which covers the {@code ^…(a|b)…} patterns
     * {@code SnapshotStreamingActor.getSnapshotFilterFromCommand} generates.
     */
    private Source<SnapshotEntry, NotUsed> newestSnapshots(final String lowerBoundPid, final String pidFilterRegex,
            final int batchSize, final boolean includeDeleted, final Duration minAgeFromNow,
            final Materializer mat, final String... snapshotFields) {
        if (snapshotFields == null || snapshotFields.length == 0) {
            return keysetPages("newest snapshot heads", lowerBoundPid,
                    (String start) -> operations.getNewestSnapshotHeadsAbove(start, batchSize, minAgeFromNow,
                            pidFilterRegex),
                    SnapshotHead::pid, PageRetry.NONE)
                    .mapConcat(page -> page.stream()
                            .filter(head -> includeDeleted || !"DELETED".equals(head.lifecycle()))
                            .map(PostgresReadJournal::toSnapshotEntryWithoutPayload)
                            .collect(java.util.stream.Collectors.toList()));
        }
        return keysetPages("newest snapshots", lowerBoundPid,
                (String start) -> operations.getNewestSnapshotsAbove(start, batchSize, minAgeFromNow, pidFilterRegex),
                SnapshotRow::pid, PageRetry.NONE)
                .mapConcat(page -> page.stream()
                        .filter(row -> includeDeleted || !"DELETED".equals(row.lifecycle()))
                        .map(PostgresReadJournal::toSnapshotEntry)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public Source<Optional<Long>, NotUsed> getSmallestEventSeqNo(final String pid) {
        return operations.getSmallestEventSeqNo(pid);
    }

    @Override
    public Source<Optional<Long>, NotUsed> getLatestEventSeqNo(final String pid) {
        // Consults the high-water-mark metadata, NEVER bare MAX(sn).
        return operations.getLatestEventSeqNo(pid);
    }

    @Override
    public Source<Optional<Long>, NotUsed> getSmallestSnapshotSeqNo(final String pid) {
        return operations.getSmallestSnapshotSeqNo(pid);
    }

    // -------------------------------------------------------------------------------------------------
    // Deletion (cleanup) — bounded PK range delete (distinct from SnapshotStore.deleteAsync).
    // -------------------------------------------------------------------------------------------------

    @Override
    public Source<DeleteOutcome, NotUsed> deleteEvents(final String pid, final long minSeqNr, final long maxSeqNr) {
        return operations.deleteEvents(pid, minSeqNr, maxSeqNr).map(DeleteOutcome::acknowledged);
    }

    @Override
    public Source<DeleteOutcome, NotUsed> deleteSnapshots(final String pid, final long minSeqNr, final long maxSeqNr) {
        return operations.deleteSnapshots(pid, minSeqNr, maxSeqNr).map(DeleteOutcome::acknowledged);
    }

    // -------------------------------------------------------------------------------------------------
    // Pekko ReadJournal SPI
    // -------------------------------------------------------------------------------------------------

    @Override
    public Source<EventEnvelope, NotUsed> currentEventsByPersistenceId(final String persistenceId,
            final long fromSequenceNr, final long toSequenceNr) {
        return operations.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
                .map(PostgresReadJournal::toEventEnvelope);
    }

    @Override
    public Source<EventEnvelope, NotUsed> currentEventsByTag(final String tag, final Offset offset) {
        return operations.eventsByTag(tag, toSeqOffset(offset)).map(PostgresReadJournal::toEventEnvelope);
    }

    @Override
    public Source<String, NotUsed> currentPersistenceIds() {
        return operations.currentPersistenceIds();
    }

    @Override
    public Source<EventEnvelope, NotUsed> eventsByPersistenceId(final String persistenceId, final long fromSequenceNr,
            final long toSequenceNr) {
        // No live tailing in this backend yet; the bounded current query satisfies the in-repo (recovery) consumers.
        return currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr);
    }

    @Override
    public Source<EventEnvelope, NotUsed> eventsByTag(final String tag, final Offset offset) {
        return currentEventsByTag(tag, offset);
    }

    @Override
    public Source<String, NotUsed> persistenceIds() {
        return currentPersistenceIds();
    }

    // -------------------------------------------------------------------------------------------------
    // Event payload mapping: decode the JSONB/text payload to a JsonObject; no BsonDocument cast.
    // -------------------------------------------------------------------------------------------------

    @Override
    public JsonObject toEventJson(final EventEnvelope eventEnvelope) {
        final Object event = eventEnvelope.event();
        if (event instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        if (event instanceof CharSequence charSequence) {
            return parse(charSequence.toString());
        }
        if (event instanceof byte[] bytes) {
            return parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("Cannot convert event payload of type <"
                + (event == null ? "null" : event.getClass()) + "> to JsonObject; expected JSONB-derived text.");
    }

    // -------------------------------------------------------------------------------------------------
    // mapping helpers
    // -------------------------------------------------------------------------------------------------

    private static EventEnvelope toEventEnvelope(final JournalRow row) {
        // Offset = the global monotonic seq IDENTITY column.
        return EventEnvelope.apply(Offset.sequence(row.seq()), row.pid(), row.sequenceNr(), row.eventJson());
    }

    private static SnapshotEntry toSnapshotEntry(final SnapshotRow row) {
        return SnapshotEntry.of(row.pid(), row.sequenceNr(), row.lifecycle(), parse(row.snapshotJson()));
    }

    private static SnapshotEntry toSnapshotEntryWithoutPayload(final SnapshotHead head) {
        return SnapshotEntry.of(head.pid(), head.sequenceNr(), head.lifecycle(), JsonObject.empty());
    }

    private static long toSeqOffset(final Offset offset) {
        if (offset instanceof Sequence sequence) {
            return sequence.value();
        }
        // Offset.noOffset() (or any non-sequence offset) -> start from the beginning.
        return 0L;
    }

    private static JsonObject parse(final String json) {
        return JsonFactory.newObject(json);
    }

    /**
     * How a failed page query is retried: the whole page again, from the same cursor, after an exponential back-off,
     * with the values the MongoDB read journal uses for its per-query restarts ({@link ReadJournalRestarts}: minimum
     * back-off 1 s, maximum 128 s, random factor 0.1, and the number of restarts derived from {@code maxIdleTime}).
     * Each failed attempt is logged at WARN with its SQLSTATE.
     * <p>
     * One difference to MongoDB: its restart count is capped within a window of the minimum back-off, so an attempt
     * that runs longer than one second before it fails resets the count, and a query that keeps failing slowly (for
     * example cancelled by {@code statement_timeout}) is retried without bound. Here every failed attempt of a page
     * counts ({@link #RESTART_COUNT_WINDOW}), so the retries end and the stream fails with the page's error; its
     * consumer (the persistence ping) then starts its next iteration as scheduled.
     * </p>
     */
    static final class PageRetry {

        /** No retry: the first failure of a page fails the stream (MongoDB parity for the unbounded lookups). */
        static final PageRetry NONE = new PageRetry(0);

        /**
         * The restart count resets only if a single attempt runs longer than this before it fails. One attempt is one
         * page query, bounded by {@code statement_timeout} and the pool's acquire timeout, so the count of a page is
         * never reset and a page gets at most {@code maxRestarts} retries.
         */
        static final Duration RESTART_COUNT_WINDOW = Duration.ofDays(1L);

        private final int maxRestarts;

        private PageRetry(final int maxRestarts) {
            this.maxRestarts = maxRestarts;
        }

        /**
         * @param maxIdleTime how long a stream may go without delivering a pid; bounds the retries of one page.
         * @return the retry policy of a page.
         */
        static PageRetry forMaxIdleTime(final Duration maxIdleTime) {
            return new PageRetry(ReadJournalRestarts.computeMaxRestarts(maxIdleTime));
        }

        /**
         * Reads one page completely, retrying failed attempts. The attempts run as a stream of their own; it stops
         * (no further attempt, no back-off left waiting) when {@code stop} is shut down, which the paged stream does
         * when it ends.
         *
         * @return the page, or the error of the last attempt once the retries are exhausted.
         */
        <C, T> CompletionStage<List<T>> fetch(final String streamName, final C cursor,
                final Supplier<Source<T, NotUsed>> pageQuery, final SharedKillSwitch stop, final Materializer mat) {
            final AtomicInteger attempts = new AtomicInteger();
            final Supplier<Source<List<T>, NotUsed>> attempt = () -> {
                final int attemptNumber = attempts.incrementAndGet();
                final CompletionStage<List<T>> page = pageQuery.get().runWith(Sink.<T>seq(), mat);
                page.whenComplete((rows, error) -> {
                    if (error != null) {
                        LOGGER.warn("Page query of read-journal stream <{}> after <{}> failed on attempt <{}> (at most "
                                        + "<{}> retries with back-off): {}", streamName, describeCursor(cursor),
                                attemptNumber, maxRestarts, describe(error));
                    }
                });
                return Source.completionStage(page);
            };
            final Source<List<T>, ?> pageSource;
            if (maxRestarts <= 0) {
                pageSource = Source.lazySource(attempt::get);
            } else {
                final RestartSettings settings = RestartSettings.create(ReadJournalRestarts.MIN_BACKOFF,
                                ReadJournalRestarts.MAX_BACKOFF, ReadJournalRestarts.RANDOM_FACTOR)
                        .withMaxRestarts(maxRestarts, RESTART_COUNT_WINDOW)
                        // each failed attempt is logged above with its SQLSTATE; avoid a second WARN per attempt.
                        .withLogSettings(RestartSettings.createLogSettings(Logging.DebugLevel()));
                pageSource = RestartSource.onFailuresWithBackoff(settings, attempt::get);
            }
            return pageSource.via(stop.flow()).runWith(Sink.head(), mat);
        }

        private static Object describeCursor(final Object cursor) {
            final Object unwrapped = cursor instanceof Optional<?> optional ? optional.orElse(null) : cursor;
            return unwrapped == null || "".equals(unwrapped) ? "the start" : unwrapped;
        }

        private static String describe(final Throwable error) {
            for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
                if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                    return "SQLSTATE <" + r2dbc.getSqlState() + "> " + t;
                }
            }
            return error.toString();
        }

    }

}
