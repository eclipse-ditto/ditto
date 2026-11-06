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
package org.eclipse.ditto.internal.utils.persistence.postgres.ops;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSqlStates;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The shared connection / transaction / row-mapping layer reused by the three PostgreSQL Pekko plugins
 * ({@code PostgresJournal}, {@code PostgresSnapshotStore} and {@code PostgresReadJournal}).
 * <p>
 * It owns <em>all</em> the SQL for a single entity prefix and centralises the cross-cutting correctness invariants:
 * </p>
 * <ul>
 *     <li>The high-water-mark contract: every write upserts
 *     {@code <e>_journal_seq.highest_sn = GREATEST(highest_sn, max(sn))} inside the <em>same</em> per-{@code AtomicWrite}
 *     transaction, and the highest-sequence read is {@code GREATEST(COALESCE(MAX(sn),0), COALESCE(highest_sn,0))} —
 *     never a bare {@code MAX(sn)}, so a physically-pruned journal cannot make the reported sequence number regress;
 *     deletes only ever move {@code deleted_to}, never {@code highest_sn}.</li>
 *     <li>Snapshot idempotency: {@code INSERT … ON CONFLICT (pid, sn, written_at) DO UPDATE}, with
 *     {@code written_at} bound from {@code SnapshotMetadata.timestamp}, so re-saving the same snapshot updates rather
 *     than fails.</li>
 *     <li>Tag containment via {@code tags @> ARRAY[$1]::text[]} so the GIN index is used, and the
 *     numeric-anchored priority regex {@code ^priority-[0-9]+$} that prevents an integer-cast failure on a malformed
 *     {@code priority-x} user tag.</li>
 * </ul>
 * <p>
 * Reactive results are exposed both as Reactor {@link Mono}/{@link Flux} (consumed by the japi journal / snapshot-store
 * plugins, which must return a {@code scala.concurrent.Future}) and as Pekko {@link Source} (consumed by the streaming
 * read journal). Every method routes through {@link DittoPostgresClient}, whose {@code Flux.usingWhen} guarantees the
 * pooled connection is released on completion, error <em>and</em> cancel.
 * </p>
 */
@ThreadSafe
public final class PostgresPersistenceOperations {

    /**
     * Server-side portal size bound via {@link Statement#fetchSize(int)} on the long, genuinely-unbounded streaming
     * read-journal queries without a {@code LIMIT} ({@code currentPersistenceIds}, {@code eventsByTag},
     * {@code getLatestJournalEntries}). The tag pid streams of the persistence ping and of connection-id retrieval do
     * not use a portal: both orders read one short window per statement ({@link #getPidWindowAbove},
     * {@link #getPrioritizedPidWindowAbove}), each read completely before its rows are emitted.
     * <p>
     * The connection-pool-wide default is {@code fetch-size=0} (see {@code ConnectionPoolFactory}/{@code PostgresConfig}),
     * for which the r2dbc-postgresql driver sends one fetch-all {@code Execute}: it still reads the rows demand-driven
     * (client memory stays bounded), but the server produces the whole result in one go and blocks on the unread socket
     * while the consumer is slow. A positive per-statement {@code fetchSize} opens a server-side portal instead, so the
     * server produces the rows fetch by fetch as the Pekko stream demands them. It is applied ONLY on these read-journal
     * streaming statements — never on the write path — because a server-side portal is the PgBouncer transaction-pooling
     * hazard the {@code fetch-size=0} default deliberately avoids.
     * </p>
     * <p>
     * The portal bounds client memory, not the transaction: the statement's implicit transaction and snapshot stay
     * open until the stream is drained, and {@code statement_timeout} counts across the suspended portal. A slowly
     * consumed stream therefore holds back VACUUM and fails with SQLSTATE {@code 57014} once it outlives the timeout.
     * That is why a throttled consumer such as the persistence ping must never read through a portal: with the
     * production connection wiring a paused portal stream of tagged pids was cancelled with {@code 57014} after its
     * first 256-row fetch.
     * </p>
     */
    static final int STREAMING_FETCH_SIZE = 256;

    private final DittoPostgresClient client;
    private final PostgresTableNames tables;

    private PostgresPersistenceOperations(final DittoPostgresClient client, final PostgresTableNames tables) {
        this.client = client;
        this.tables = tables;
    }

    /**
     * @param client the shared R2DBC client.
     * @param entityPrefix the entity prefix (e.g. {@code things}); one of the known entities.
     * @return the persistence operations bound to that entity's tables.
     */
    public static PostgresPersistenceOperations of(final DittoPostgresClient client, final String entityPrefix) {
        return new PostgresPersistenceOperations(checkNotNull(client, "client"), PostgresTableNames.of(entityPrefix));
    }

    public PostgresTableNames tables() {
        return tables;
    }

    public DittoPostgresClient client() {
        return client;
    }

    // =====================================================================================================
    // journal write path
    // =====================================================================================================

    /**
     * Inserts a batch of journal events for a single {@code AtomicWrite} and upserts the high-water-mark, all in a
     * single transaction. Ditto's own actors produce single-event {@code AtomicWrite}s only (no {@code persistAll}
     * call-site); Pekko's remember-entities shard store on the {@code connection_remember} tables persists up to
     * {@code max-updates-per-write} events in one, which this transaction keeps atomic.
     *
     * @param events the events of one {@code AtomicWrite} (all sharing one persistence id), in ascending {@code sn}.
     * @return a {@code Mono} completing when the transaction commits.
     */
    public Mono<Void> insertEvents(final List<JournalInsert> events) {
        if (events.isEmpty()) {
            return Mono.empty();
        }
        final String pid = events.get(0).pid();
        final long maxSn = events.stream().mapToLong(JournalInsert::sequenceNr).max().orElseThrow();
        return inTransaction(connection -> {
            final Flux<Void> inserts = Flux.fromIterable(events)
                    .concatMap(event -> executeUpdate(connection,
                            // written_at uses clock_timestamp() (per-statement wall-clock), NOT now() (the
                            // transaction-start instant): every event of one AtomicWrite is inserted in THIS single
                            // transaction (see inTransaction), so now() would collapse all rows of a future
                            // multi-event AtomicWrite to one identical written_at. clock_timestamp() re-evaluates per
                            // INSERT so each event keeps its own ingest instant (r2mlh-l15a). The _journal_seq upsert
                            // below carries no timestamp, so there is no watermark/row ordering inconsistency.
                            "INSERT INTO " + tables.journalTable()
                                    + " (pid, sn, manifest, tags, event, written_at) "
                                    + "VALUES ($1, $2, $3, $4, $5::jsonb, clock_timestamp())",
                            stmt -> {
                                stmt.bind(0, event.pid());
                                stmt.bind(1, event.sequenceNr());
                                stmt.bind(2, event.manifest());
                                stmt.bind(3, event.tags().toArray(new String[0]));
                                stmt.bind(4, event.eventJson());
                            }));
            final Mono<Void> hwm = executeUpdate(connection,
                    "INSERT INTO " + tables.journalSeqTable() + " (pid, highest_sn) VALUES ($1, $2) "
                            + "ON CONFLICT (pid) DO UPDATE SET highest_sn = "
                            + "GREATEST(" + tables.journalSeqTable() + ".highest_sn, excluded.highest_sn)",
                    stmt -> {
                        stmt.bind(0, pid);
                        stmt.bind(1, maxSn);
                    });
            return inserts.then(hwm);
        });
    }

    /**
     * Selects an existing journal row for idempotency checks on a duplicate {@code (pid, sn)}. Used by
     * the journal plugin to decide between idempotent-success (payload + manifest match) and a fatal mismatch.
     *
     * @param pid the persistence id.
     * @param sequenceNr the sequence number.
     * @return a {@code Mono} of the existing row (manifest + event JSON), empty if none.
     */
    public Mono<JournalRow> findEvent(final String pid, final long sequenceNr) {
        return Mono.from(client.executeSqlPublisher(
                        "SELECT pid, sn, seq, manifest, tags, event::text AS event FROM "
                                + tables.journalTable() + " WHERE pid = $1 AND sn = $2",
                        stmt -> {
                            stmt.bind(0, pid);
                            stmt.bind(1, sequenceNr);
                        },
                        (row, meta) -> mapJournalRow(row)));
    }

    /**
     * Replays journal events in {@code (pid, sn)} order (index scan), bounded by {@code [from, to]} and {@code max}.
     *
     * @param pid the persistence id.
     * @param fromSequenceNr the inclusive lower bound.
     * @param toSequenceNr the inclusive upper bound.
     * @param max the maximum number of rows to replay.
     * @return a {@code Flux} of journal rows.
     */
    public Flux<JournalRow> replayEvents(final String pid, final long fromSequenceNr, final long toSequenceNr,
            final long max) {
        if (max <= 0L) {
            return Flux.empty();
        }
        return Flux.from(client.executeSqlPublisher(
                "SELECT pid, sn, seq, manifest, tags, event::text AS event FROM " + tables.journalTable()
                        + " WHERE pid = $1 AND sn >= $2 AND sn <= $3 ORDER BY sn ASC LIMIT $4",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, fromSequenceNr);
                    stmt.bind(2, toSequenceNr);
                    stmt.bind(3, max);
                },
                (row, meta) -> mapJournalRow(row)));
    }

    /**
     * Reads the highest sequence number for a pid, NEVER as a bare {@code MAX(sn)}. Combines the live
     * journal max with the persisted high-water-mark so a physically-pruned table cannot regress the value.
     *
     * @param pid the persistence id.
     * @return a {@code Mono} of the highest sequence number (0 if the pid is unknown).
     */
    public Mono<Long> readHighestSequenceNr(final String pid) {
        return Flux.from(client.executeSqlPublisher(
                        "SELECT GREATEST("
                                + "COALESCE((SELECT MAX(sn) FROM " + tables.journalTable() + " WHERE pid = $1), 0), "
                                + "COALESCE((SELECT highest_sn FROM " + tables.journalSeqTable()
                                + " WHERE pid = $1), 0)) AS hwm",
                        stmt -> stmt.bind(0, pid),
                        (row, meta) -> getLong(row, "hwm")))
                .next()
                .defaultIfEmpty(0L);
    }

    /**
     * Physically deletes journal events {@code sn <= toSequenceNr} and records {@code deleted_to} — never touching
     * {@code highest_sn} — in a single transaction.
     *
     * @param pid the persistence id.
     * @param toSequenceNr the inclusive upper bound.
     * @return a {@code Mono} completing when the transaction commits.
     */
    public Mono<Void> deleteMessagesTo(final String pid, final long toSequenceNr) {
        return inTransaction(connection -> {
            final Mono<Void> delete = executeUpdate(connection,
                    "DELETE FROM " + tables.journalTable() + " WHERE pid = $1 AND sn <= $2",
                    stmt -> {
                        stmt.bind(0, pid);
                        stmt.bind(1, toSequenceNr);
                    });
            final Mono<Void> markDeletedTo = executeUpdate(connection,
                    // INSERT path (no prior journal_seq row): seed highest_sn with the literal 0, never toSequenceNr.
                    // A delete must only ever move deleted_to; raising highest_sn here would fabricate a sequence number
                    // that was never persisted, which readHighestSequenceNr (GREATEST(MAX(sn), highest_sn)) would report.
                    "INSERT INTO " + tables.journalSeqTable() + " (pid, highest_sn, deleted_to) VALUES ($1, 0, $2) "
                            + "ON CONFLICT (pid) DO UPDATE SET deleted_to = "
                            + "GREATEST(" + tables.journalSeqTable() + ".deleted_to, excluded.deleted_to)",
                    stmt -> {
                        stmt.bind(0, pid);
                        stmt.bind(1, toSequenceNr);
                    });
            return delete.then(markDeletedTo);
        });
    }

    // =====================================================================================================
    // snapshot store
    // =====================================================================================================

    /**
     * Loads the single best snapshot honouring all four bounds of a Pekko {@code SnapshotSelectionCriteria}.
     * {@code Long.MAX_VALUE}/epoch-bounds are guarded by the caller before binding.
     *
     * @param pid the persistence id.
     * @param maxSeq inclusive upper {@code sn} bound.
     * @param minSeq inclusive lower {@code sn} bound.
     * @param maxTs inclusive upper {@code written_at} bound.
     * @param minTs inclusive lower {@code written_at} bound.
     * @return a {@code Mono} of the matching snapshot row, empty if none.
     */
    public Mono<SnapshotRow> loadSnapshot(final String pid, final long maxSeq, final long minSeq,
            final Instant maxTs, final Instant minTs) {
        return Flux.from(client.executeSqlPublisher(
                        "SELECT pid, sn, snapshot::text AS snapshot, lifecycle, written_at FROM " + tables.snapsTable()
                                + " WHERE pid = $1 AND sn <= $2 AND sn >= $3 AND written_at <= $4 AND written_at >= $5 "
                                + "ORDER BY sn DESC, written_at DESC LIMIT 1",
                        stmt -> {
                            stmt.bind(0, pid);
                            stmt.bind(1, maxSeq);
                            stmt.bind(2, minSeq);
                            stmt.bind(3, maxTs);
                            stmt.bind(4, minTs);
                        },
                        (row, meta) -> mapSnapshotRow(row)))
                .next();
    }

    /**
     * Idempotent snapshot save: {@code written_at} is bound from {@code SnapshotMetadata.timestamp}
     * (NOT {@code now()}), and a conflicting {@code (pid, sn, written_at)} updates instead of crashing.
     *
     * @param pid the persistence id.
     * @param sequenceNr the sequence number.
     * @param snapshotJson the JSONB snapshot value (JSON text).
     * @param lifecycle the lifecycle marker, or {@code null}.
     * @param writtenAt the snapshot timestamp.
     * @return a {@code Mono} completing on success.
     */
    public Mono<Void> saveSnapshot(final String pid, final long sequenceNr, final String snapshotJson,
            @Nullable final String lifecycle, final Instant writtenAt) {
        return Mono.from(client.executeUpdatePublisher(
                        "INSERT INTO " + tables.snapsTable() + " (pid, sn, snapshot, lifecycle, written_at) "
                                + "VALUES ($1, $2, $3::jsonb, $4, $5) "
                                + "ON CONFLICT (pid, sn, written_at) DO UPDATE "
                                + "SET snapshot = excluded.snapshot, lifecycle = excluded.lifecycle",
                        stmt -> {
                            stmt.bind(0, pid);
                            stmt.bind(1, sequenceNr);
                            stmt.bind(2, snapshotJson);
                            if (lifecycle == null) {
                                stmt.bindNull(3, String.class);
                            } else {
                                stmt.bind(3, lifecycle);
                            }
                            stmt.bind(4, writtenAt);
                        }))
                .then();
    }

    /**
     * Deletes exactly one snapshot row {@code (pid, sn, written_at)} — the {@code deleteAsync(SnapshotMetadata)}
     * overload. A bare sn-range would wrongly drop every {@code written_at} row at that sn.
     */
    public Mono<Long> deleteSnapshotExact(final String pid, final long sequenceNr, final Instant writtenAt) {
        return Mono.from(client.executeUpdatePublisher(
                "DELETE FROM " + tables.snapsTable() + " WHERE pid = $1 AND sn = $2 AND written_at = $3",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, sequenceNr);
                    stmt.bind(2, writtenAt);
                }));
    }

    /** All snapshot rows of {@code (pid, sn)} regardless of {@code written_at} — the timestamp-0 wildcard delete. */
    public Mono<Long> deleteSnapshotsBySn(final String pid, final long sequenceNr) {
        return Mono.from(client.executeUpdatePublisher(
                "DELETE FROM " + tables.snapsTable() + " WHERE pid = $1 AND sn = $2",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, sequenceNr);
                }));
    }

    /**
     * Upper-bounded snapshot delete — the {@code deleteAsync(pid, criteria)} overload:
     * {@code sn <= maxSeq AND written_at <= maxTs}, no lower bound.
     */
    public Mono<Long> deleteSnapshotsUpTo(final String pid, final long maxSeq, final Instant maxTs) {
        return Mono.from(client.executeUpdatePublisher(
                "DELETE FROM " + tables.snapsTable() + " WHERE pid = $1 AND sn <= $2 AND written_at <= $3",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, maxSeq);
                    stmt.bind(2, maxTs);
                }));
    }

    // =====================================================================================================
    // read journal
    // =====================================================================================================

    /**
     * One WINDOW of the priority-order pid stream: the next {@code window} distinct pids strictly above
     * {@code lowerBoundPid} in pid order ({@code COLLATE "C"}), whether they qualify or not, each with whether it
     * carries {@code tag} on ANY of its events (an empty {@code tag} qualifies every pid) and its priority. The caller
     * keeps the qualifying pids, continues after the window's last pid, and orders what it collected by priority
     * ({@code PostgresReadJournal#getJournalPidsWithTagOrderedByPriorityTag}).
     * <p>
     * The priority comes from the pid's NEWEST event that carries the tag (Mongo parity: {@code $match} on the tag,
     * then {@code $group} with {@code $last} tags), even when a newer event without the tag exists: the tag filter is
     * part of the per-pid top-1 backward primary-key scan ({@code LEFT JOIN LATERAL … ORDER BY sn DESC LIMIT 1}), so
     * that scan stops at the newest tagged event, and a pid whose scan finds none does not qualify (LEFT JOIN: it
     * still comes back, so it can be the cursor). The highest {@code priority-<n>} of that event counts; the
     * numeric-anchored regex avoids an integer-cast failure on a malformed {@code priority-x} tag, and an event without
     * a priority tag has priority 0.
     * </p>
     * <p>
     * A statement costs what a window of {@link #getPidWindowAbove} costs: an index-only scan of the window's pids and
     * one backward probe per pid on its own primary-key range, which reads every row of a pid without a tagged event
     * and stops at the newest tagged one otherwise, i.e. O(window × events per pid), whatever share of the journal
     * carries the tag. The tag is matched with {@code $2 = ANY(tags)}, which the GIN tag index cannot serve. (A single
     * statement over all tagged rows let the planner choose a sequential scan of the whole journal once about a tenth
     * of its rows carried the tag.)
     * </p>
     *
     * @param lowerBoundPid the exclusive lower bound (the last pid of the previous window, or {@code ""}).
     * @param tag the tag, or {@code ""} for all pids.
     * @param window the number of distinct pids examined by this statement.
     * @return a {@code Source} of the window's pids, in pid order.
     */
    public Source<PrioritizedPid, NotUsed> getPrioritizedPidWindowAbove(final String lowerBoundPid, final String tag,
            final int window) {
        final String sql = "SELECT w.pid AS pid, n.tags IS NOT NULL AS qualifies, COALESCE(pr.prio, 0) AS priority "
                + "FROM (SELECT DISTINCT pid FROM " + tables.journalTable() + " WHERE pid > $1 ORDER BY pid LIMIT $3"
                + ") w LEFT JOIN LATERAL ("
                + "SELECT tags FROM " + tables.journalTable()
                + " WHERE pid = w.pid AND ($2 = '' OR $2 = ANY(tags)) ORDER BY sn DESC LIMIT 1"
                + ") n ON true LEFT JOIN LATERAL ("
                + "SELECT max((substring(tag from '^priority-([0-9]+)$'))::int) AS prio "
                + "FROM unnest(n.tags) AS tag WHERE tag ~ '^priority-[0-9]+$'"
                + ") pr ON true ORDER BY w.pid";
        return pageQuerySource(sql,
                stmt -> {
                    stmt.bind(0, lowerBoundPid);
                    stmt.bind(1, tag);
                    stmt.bind(2, window);
                },
                row -> new PrioritizedPid(getString(row, "pid"),
                        Boolean.TRUE.equals(row.get("qualifies", Boolean.class)), getInt(row, "priority")));
    }

    /**
     * One WINDOW of the pid stream in pid order ({@code COLLATE "C"}): the next {@code window} distinct pids strictly
     * above {@code lowerBoundPid}, whether they carry the tag or not, each flagged with whether it qualifies. The
     * caller keeps the qualifying pids and continues after the window's last pid, so a window without any qualifying
     * pid still moves the walk on. An empty {@code tag} qualifies every pid.
     * <ul>
     *     <li>Without {@code considerOnlyLatest} a pid qualifies if ANY of its events carries the tag.</li>
     *     <li>With it, only if its NEWEST event does: the filter applies AFTER the newest-per-pid pick (Mongo parity:
     *     the newest entry per pid is grouped first, then matched), checked on a per-pid top-1 backward PK scan; it must
     *     not move into that lateral subquery, where it would select the newest TAGGED event instead. (For the empty
     *     tag, the Mongo read journal returns NO pids in this variant: a known divergence.)</li>
     * </ul>
     * <p>
     * Why windows over all pids rather than "the next tagged pids": the cost of a statement must not depend on where
     * the tagged pids lie. Always-alive entities are typically a minority clustered in some part of the key space (one
     * namespace among many). A statement that looked for the next {@code n} tagged pids would read every untagged row
     * between the cursor and them, and, at the end of every walk, every row after the last tagged pid, in one
     * statement that {@code statement_timeout} would cancel on every retry. A window is a primary-key range scan that
     * stops after {@code window} distinct pids (index-only for the window itself), plus one probe per pid on its own
     * primary-key range, so a statement costs O(window × events per pid) whatever the tag's selectivity or layout, and
     * a walk takes (distinct pids / window) statements. The tag is matched with {@code $2 = ANY(tags)}, which the GIN
     * tag index cannot serve: a GIN scan for a frequent tag reads every tagged row of the table, per statement.
     * </p>
     *
     * @param lowerBoundPid the exclusive lower bound (the last pid of the previous window, or {@code ""}).
     * @param tag the tag, or {@code ""} for all pids.
     * @param considerOnlyLatest whether only the newest event of a pid counts.
     * @param window the number of distinct pids examined by this statement.
     * @return a {@code Source} of the window's pids, in pid order.
     */
    public Source<WindowedPid, NotUsed> getPidWindowAbove(final String lowerBoundPid, final String tag,
            final boolean considerOnlyLatest, final int window) {
        final String windowPids = "SELECT DISTINCT pid FROM " + tables.journalTable()
                + " WHERE pid > $1 ORDER BY pid LIMIT $3";
        final String sql;
        if (considerOnlyLatest) {
            sql = "SELECT w.pid AS pid, ($2 = '' OR $2 = ANY(n.tags)) AS qualifies FROM (" + windowPids
                    + ") w CROSS JOIN LATERAL ("
                    + "SELECT tags FROM " + tables.journalTable() + " WHERE pid = w.pid ORDER BY sn DESC LIMIT 1"
                    + ") n ORDER BY w.pid";
        } else {
            sql = "SELECT w.pid AS pid, ($2 = '' OR EXISTS ("
                    + "SELECT 1 FROM " + tables.journalTable() + " t WHERE t.pid = w.pid AND $2 = ANY(t.tags)"
                    + ")) AS qualifies FROM (" + windowPids + ") w ORDER BY w.pid";
        }
        return pageQuerySource(sql,
                stmt -> {
                    stmt.bind(0, lowerBoundPid);
                    stmt.bind(1, tag);
                    stmt.bind(2, window);
                },
                row -> new WindowedPid(getString(row, "pid"), Boolean.TRUE.equals(row.get("qualifies",
                        Boolean.class))));
    }

    /**
     * All distinct pids in pid order, above an (exclusive) lower-bound pid for cursor pagination.
     */
    public Source<String, NotUsed> getPidsAbove(final String lowerBoundPid, final int batchSize) {
        return pageQuerySource("SELECT DISTINCT pid FROM " + tables.journalTable()
                        + " WHERE pid > $1 ORDER BY pid LIMIT $2",
                stmt -> {
                    stmt.bind(0, lowerBoundPid);
                    stmt.bind(1, batchSize);
                },
                row -> getString(row, "pid"));
    }

    /**
     * The tags carried by the most-recent event of a pid (Mongo {@code $last} semantics).
     */
    public Source<String, NotUsed> getMostRecentTagsForPid(final String pid) {
        // LIMIT selects the newest EVENT first; unnest expands ITS tags — LIMIT after unnest kept one
        // arbitrary tag and, on an empty newest-tags array, leaked a tag from an OLDER event.
        return querySourceMany("SELECT unnest(tags) AS tag FROM ("
                        + "SELECT tags FROM " + tables.journalTable()
                        + " WHERE pid = $1 ORDER BY sn DESC LIMIT 1) newest",
                stmt -> stmt.bind(0, pid),
                row -> getString(row, "tag"));
    }

    /**
     * Journal events of a pid between {@code [from, to]} in {@code sn} order (historical recovery).
     */
    public Source<JournalRow, NotUsed> currentEventsByPersistenceId(final String pid, final long fromSequenceNr,
            final long toSequenceNr) {
        return querySource("SELECT pid, sn, seq, manifest, tags, event::text AS event FROM " + tables.journalTable()
                        + " WHERE pid = $1 AND sn >= $2 AND sn <= $3 ORDER BY sn ASC",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, fromSequenceNr);
                    stmt.bind(2, toSequenceNr);
                },
                PostgresPersistenceOperations::mapJournalRow);
    }

    /**
     * Events carrying {@code tag} whose global {@code seq} IDENTITY offset is strictly above {@code fromOffsetExclusive},
     * ordered by {@code seq}.
     */
    public Source<JournalRow, NotUsed> eventsByTag(final String tag, final long fromOffsetExclusive) {
        // Unbounded (no LIMIT) event scan -> stream via a server-side portal (positive fetchSize) for backpressure.
        return streamingQuerySource("SELECT pid, sn, seq, manifest, tags, event::text AS event FROM "
                        + tables.journalTable()
                        + " WHERE tags @> ARRAY[$1]::text[] AND seq > $2 ORDER BY seq ASC",
                stmt -> {
                    stmt.bind(0, tag);
                    stmt.bind(1, fromOffsetExclusive);
                },
                PostgresPersistenceOperations::mapJournalRow);
    }

    /** All distinct pids in pid order. */
    public Source<String, NotUsed> currentPersistenceIds() {
        // Unbounded (no LIMIT) pid scan -> stream via a server-side portal (positive fetchSize) for backpressure.
        return streamingQuerySource("SELECT DISTINCT pid FROM " + tables.journalTable() + " ORDER BY pid",
                null, row -> getString(row, "pid"));
    }

    /**
     * The newest journal event per pid, as one streaming loose-index-scan query (the pattern also used by
     * {@link #getNewestSnapshotsAbove}). Replaces the former N+1 — one bounded replay query per pid
     * — with a single unbounded streaming query backed by a server-side portal for backpressure.
     * <p>
     * Two-phase loose index scan: a distinct-pid phase (index-only PK scan — no payload detoast, no
     * sort) feeds a per-pid top-1 {@code CROSS JOIN LATERAL} backward PK scan, so exactly one event
     * per pid is ever detoasted. The former {@code DISTINCT ON (pid) … ORDER BY pid, sn DESC} shape
     * ordered by a mixed direction no ASC-PK index can satisfy — it externally sorted the ENTIRE
     * journal including event payloads. Still one statement, NOT an N+1 per-pid replay.
     */
    public Source<JournalRow, NotUsed> getLatestJournalEntries() {
        return streamingQuerySource("SELECT p.pid, j.sn, j.seq, j.manifest, j.tags, j.event FROM ("
                        + "SELECT DISTINCT pid FROM " + tables.journalTable()
                        + ") p CROSS JOIN LATERAL ("
                        + "SELECT sn, seq, manifest, tags, event::text AS event FROM " + tables.journalTable()
                        + " WHERE pid = p.pid ORDER BY sn DESC LIMIT 1"
                        + ") j ORDER BY p.pid",
                null, PostgresPersistenceOperations::mapJournalRow);
    }

    /**
     * One PAGE of newest-snapshot-per-pid rows above an (exclusive) lower-bound pid, in pid order —
     * as a two-phase loose index scan: phase 1 selects the page's winner pids touching ONLY
     * PK-index columns (index-only scan + LIMIT, early-stopping like Mongo's IXSCAN), phase 2
     * fetches exactly one row per winner via a backward PK scan ({@code ORDER BY sn DESC,
     * written_at DESC LIMIT 1}), so only the page's winning snapshot payloads are ever detoasted.
     * Callers that need no payload use {@link #getNewestSnapshotHeadsAbove}, the same statement without it.
     * The former single-query {@code DISTINCT ON (pid)} shape seq-scanned and externally sorted
     * every payload above the lower bound per page (bench C1: 21× slower than Mongo at 250k pids).
     * <p>
     * Semantics mirror {@code MongoReadJournal.listNewestActiveSnapshotsByBatch} STAGE ORDER exactly:
     * the pid lower bound, the optional pid regex and the optional min-age filter apply to the ROWS
     * (pre-grouping) — hence the age predicate appears in BOTH phases: in phase 1 so a pid with no
     * qualifying row consumes no page slot, in phase 2 so the newest QUALIFYING row wins. Lifecycle/
     * DELETED filtering intentionally does NOT happen here — the caller filters AFTER the
     * newest-per-pid selection, so a pid whose NEWEST snapshot is DELETED is excluded entirely
     * instead of resurrecting an older live snapshot.
     * <p>
     * Age filter: skipped entirely when {@code minAgeFromNow.isZero()} (Mongo parity) — {@code written_at}
     * is bound from the app-server clock, so an unconditional {@code < now()} would hide fresh snapshots
     * under app-ahead-of-DB clock skew.
     *
     * @param pidFilterRegex POSIX-ERE pid filter, or {@code ""} for no filter.
     */
    public Source<SnapshotRow, NotUsed> getNewestSnapshotsAbove(final String lowerBoundPid, final int batchSize,
            final java.time.Duration minAgeFromNow, final String pidFilterRegex) {
        return pageQuerySource(newestSnapshotsPageSql(true),
                newestSnapshotsPageBinds(lowerBoundPid, batchSize, minAgeFromNow, pidFilterRegex),
                PostgresPersistenceOperations::mapSnapshotRow);
    }

    /**
     * {@link #getNewestSnapshotsAbove} without the snapshot payload: the same statement minus the outer
     * {@code s.snapshot} column and the lateral's {@code snapshot::text}, with the same binds {@code $1}…{@code $5},
     * the same plan (phase 2 still reads {@code lifecycle} and {@code written_at} from the heap, but no TOAST block)
     * and the same semantics (lower bound, pid regex, the age filter in both phases, DELETED filtering left to the
     * caller). {@code PostgresReadJournal} runs it for every newest-snapshot stream that requests no snapshot fields:
     * background cleanup ({@code Cleanup}, in the things, policies and connectivity services) and the connectivity
     * service's retrieval of all connection ids ({@code ConnectionIdsRetrievalActor}).
     *
     * @param pidFilterRegex POSIX-ERE pid filter, or {@code ""} for no filter.
     */
    public Source<SnapshotHead, NotUsed> getNewestSnapshotHeadsAbove(final String lowerBoundPid, final int batchSize,
            final java.time.Duration minAgeFromNow, final String pidFilterRegex) {
        return pageQuerySource(newestSnapshotsPageSql(false),
                newestSnapshotsPageBinds(lowerBoundPid, batchSize, minAgeFromNow, pidFilterRegex),
                PostgresPersistenceOperations::mapSnapshotHead);
    }

    /** The newest-snapshot page of {@link #getNewestSnapshotsAbove}, with or without its two payload columns. */
    private String newestSnapshotsPageSql(final boolean withPayload) {
        return "SELECT p.pid, s.sn, " + (withPayload ? "s.snapshot, " : "") + "s.lifecycle, s.written_at FROM ("
                + "SELECT pid FROM " + tables.snapsTable()
                + " WHERE pid > $1"
                + " AND ($2 = '' OR pid ~ $2)"
                + " AND ($3 OR written_at < now() - $4::interval)"
                + " GROUP BY pid ORDER BY pid LIMIT $5"
                + ") p CROSS JOIN LATERAL ("
                + "SELECT sn, " + (withPayload ? "snapshot::text AS snapshot, " : "") + "lifecycle, written_at FROM "
                + tables.snapsTable()
                + " WHERE pid = p.pid"
                + " AND ($3 OR written_at < now() - $4::interval)"
                + " ORDER BY sn DESC, written_at DESC LIMIT 1"
                + ") s ORDER BY p.pid";
    }

    private static java.util.function.Consumer<Statement> newestSnapshotsPageBinds(final String lowerBoundPid,
            final int batchSize, final java.time.Duration minAgeFromNow, final String pidFilterRegex) {
        final boolean noAgeFilter = minAgeFromNow.isZero();
        final String interval = minAgeFromNow.toSeconds() + " seconds";
        return stmt -> {
            stmt.bind(0, lowerBoundPid);
            stmt.bind(1, pidFilterRegex);
            stmt.bind(2, noAgeFilter);
            stmt.bind(3, interval);
            stmt.bind(4, batchSize);
        };
    }

    /** Smallest journal sequence number for a pid (cleanup bound). */
    public Source<Optional<Long>, NotUsed> getSmallestEventSeqNo(final String pid) {
        return querySource("SELECT MIN(sn) AS sn FROM " + tables.journalTable() + " WHERE pid = $1",
                stmt -> stmt.bind(0, pid), row -> Optional.ofNullable(getNullableLong(row, "sn")));
    }

    /** Smallest snapshot sequence number for a pid (cleanup bound). */
    public Source<Optional<Long>, NotUsed> getSmallestSnapshotSeqNo(final String pid) {
        return querySource("SELECT MIN(sn) AS sn FROM " + tables.snapsTable() + " WHERE pid = $1",
                stmt -> stmt.bind(0, pid), row -> Optional.ofNullable(getNullableLong(row, "sn")));
    }

    /** Highest journal sequence number for a pid — consults the high-water-mark, never bare MAX(sn). */
    public Source<Optional<Long>, NotUsed> getLatestEventSeqNo(final String pid) {
        return Source.fromPublisher(readHighestSequenceNr(pid)
                .map(hwm -> hwm > 0L ? Optional.of(hwm) : Optional.<Long>empty()));
    }

    /** The newest snapshot sn strictly before a timestamp. */
    public Source<Long, NotUsed> getLastSnapshotSequenceNumberBeforeTimestamp(final String pid, final Instant ts) {
        return querySource("SELECT sn FROM " + tables.snapsTable()
                        + " WHERE pid = $1 AND written_at < $2 ORDER BY sn DESC LIMIT 1",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, ts);
                },
                row -> getLong(row, "sn"));
    }

    /** Bounded PK-range event delete (read-journal cleanup path, distinct from SnapshotStore.deleteAsync). */
    public Source<Long, NotUsed> deleteEvents(final String pid, final long minSeqNr, final long maxSeqNr) {
        return client.executeUpdate("DELETE FROM " + tables.journalTable()
                        + " WHERE pid = $1 AND sn >= $2 AND sn <= $3",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, minSeqNr);
                    stmt.bind(2, maxSeqNr);
                });
    }

    /** Bounded PK-range snapshot delete (read-journal cleanup path). */
    public Source<Long, NotUsed> deleteSnapshots(final String pid, final long minSeqNr, final long maxSeqNr) {
        return client.executeUpdate("DELETE FROM " + tables.snapsTable()
                        + " WHERE pid = $1 AND sn >= $2 AND sn <= $3",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, minSeqNr);
                    stmt.bind(2, maxSeqNr);
                });
    }

    // =====================================================================================================
    // namespace / entity purge (persistence-operations actor — namespace-ops / entity-ops)
    // =====================================================================================================

    /**
     * Purges <em>every</em> persisted row of a namespace from this entity's three tables — the {@code <e>_journal}
     * event rows, the {@code <e>_journal_seq} high-water-mark rows and the {@code <e>_snaps} snapshot rows — in a single
     * transaction, selecting by the persistence-id prefix {@code <pidPrefix><namespace>:} (e.g. {@code thing:ns:}).
     * <p>
     * The prefix match is a parameterized {@code LIKE … ESCAPE} whose literal segment ({@code pidPrefix + namespace +
     * ':'}) has its LIKE metacharacters ({@code \ % _}) escaped before a single trailing {@code %} is appended, so a
     * namespace containing an underscore (a {@code \w} namespace character) — or an adversarial value — matches exactly
     * that namespace's pids and NOTHING else. The three deletes share one transaction so a namespace purge is atomic.
     *
     * @param pidPrefix the per-entity persistence-id prefix including the trailing colon (e.g. {@code "thing:"}).
     * @param namespace the namespace to purge.
     * @return a {@code Mono} completing when the transaction commits.
     */
    public Mono<Void> purgeNamespace(final String pidPrefix, final CharSequence namespace) {
        final String likePattern = escapeLike(pidPrefix + namespace + ":") + "%";
        return inTransaction(connection -> {
            final Mono<Void> journal = executeUpdate(connection,
                    "DELETE FROM " + tables.journalTable() + " WHERE pid LIKE $1 ESCAPE '\\'",
                    stmt -> stmt.bind(0, likePattern));
            final Mono<Void> journalSeq = executeUpdate(connection,
                    "DELETE FROM " + tables.journalSeqTable() + " WHERE pid LIKE $1 ESCAPE '\\'",
                    stmt -> stmt.bind(0, likePattern));
            final Mono<Void> snaps = executeUpdate(connection,
                    "DELETE FROM " + tables.snapsTable() + " WHERE pid LIKE $1 ESCAPE '\\'",
                    stmt -> stmt.bind(0, likePattern));
            return journal.then(journalSeq).then(snaps);
        });
    }

    /**
     * Purges <em>every</em> persisted row of a single entity from this entity's three tables — the {@code <e>_journal}
     * event rows, the {@code <e>_journal_seq} high-water-mark rows and the {@code <e>_snaps} snapshot rows — in a single
     * transaction, selecting by the exact persistence id {@code <pidPrefix><entityId>} (e.g. {@code thing:ns:id}).
     * <p>
     * The match is an exact {@code pid = $1} equality (no wildcard), so it deletes exactly that one entity's rows across
     * the three tables and NOTHING else. The three deletes share one transaction so the entity purge is atomic.
     *
     * @param pid the full persistence id of the entity to purge (the prefix already prepended to the entity id).
     * @return a {@code Mono} completing when the transaction commits.
     */
    public Mono<Void> purgeEntity(final String pid) {
        return inTransaction(connection -> {
            final Mono<Void> journal = executeUpdate(connection,
                    "DELETE FROM " + tables.journalTable() + " WHERE pid = $1",
                    stmt -> stmt.bind(0, pid));
            final Mono<Void> journalSeq = executeUpdate(connection,
                    "DELETE FROM " + tables.journalSeqTable() + " WHERE pid = $1",
                    stmt -> stmt.bind(0, pid));
            final Mono<Void> snaps = executeUpdate(connection,
                    "DELETE FROM " + tables.snapsTable() + " WHERE pid = $1",
                    stmt -> stmt.bind(0, pid));
            return journal.then(journalSeq).then(snaps);
        });
    }

    /**
     * Escapes the LIKE metacharacters ({@code \ % _}) in a literal prefix so it can be used verbatim as the fixed part
     * of a {@code LIKE … ESCAPE '\'} pattern. The backslash is escaped first so it does not double-escape the {@code %}
     * / {@code _} replacements.
     */
    private static String escapeLike(final String literal) {
        return literal
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    // =====================================================================================================
    // internal helpers
    // =====================================================================================================

    /**
     * Runs {@code work} on one pooled connection inside one transaction: {@code BEGIN} before, {@code COMMIT} when
     * the returned publisher completes, {@code ROLLBACK} (best effort) when it fails or is cancelled, and the
     * connection goes back to the pool on every path. A missing-table error ({@code 42P01}) with self-heal enabled
     * recreates the schema and re-runs the WHOLE transaction once, on a fresh connection. The write paths of the
     * journal and snapshot plugins and the namespace/entity purges run on it; so does the payload store's batch
     * rewrite, which is why it is public.
     *
     * @param work the statements to run on the transaction's connection; its publisher's value is the result.
     * @param <T> the result type.
     * @return the result of {@code work}, completing when the transaction has committed.
     */
    public <T> Mono<T> inTransaction(final java.util.function.Function<Connection, Mono<T>> work) {
        return inTransactionOnce(work)
                // On a missing-table error (42P01) with self-heal enabled, recreate the schema and re-run the WHOLE
                // transaction ONCE. A 42P01 aborts the current transaction, so the retry must start a FRESH transaction
                // (re-invoke inTransactionOnce, which opens a new connection + beginTransaction) rather than resume the
                // aborted one. The retry is not itself heal-wrapped, so a still-missing table after a heal surfaces the
                // error instead of looping.
                .onErrorResume(error -> PostgresSqlStates.isUndefinedTable(error) && client.isSelfHealEnabled()
                        // If the heal itself fails, surface the ORIGINAL missing-table error (no worse than self-heal
                        // off); the heal failure is WARN-logged by the healer.
                        ? client.healSchema().onErrorResume(healError -> Mono.error(error))
                                .then(Mono.defer(() -> inTransactionOnce(work)))
                        : Mono.error(error));
    }

    private <T> Mono<T> inTransactionOnce(final java.util.function.Function<Connection, Mono<T>> work) {
        // 5-arg usingWhen so the cleanup is signal-specific: completion commits then closes, while BOTH the error
        // and the cancel arms roll back (best-effort) before closing. The 3-arg form closes on cancel WITHOUT rolling
        // back, returning an "idle in transaction" connection to the pool (r2mlh-m1). No inline onErrorResume rollback
        // here — that would double-rollback on the error path now that asyncError owns it.
        return Mono.usingWhen(
                Mono.from(client.getConnectionPool().create()),
                connection -> Mono.from(connection.beginTransaction())
                        .then(work.apply(connection)),
                connection -> Mono.from(connection.commitTransaction())
                        .then(Mono.from(connection.close())),
                (connection, error) -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())),
                connection -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())));
    }

    private static Mono<Void> executeUpdate(final Connection connection, final String sql,
            final java.util.function.Consumer<Statement> binder) {
        final Statement statement = connection.createStatement(sql);
        binder.accept(statement);
        return Flux.from(statement.execute()).flatMap(Result::getRowsUpdated).then();
    }

    private <T> Source<T, NotUsed> querySource(final String sql,
            @Nullable final java.util.function.Consumer<Statement> binder,
            final java.util.function.Function<Row, T> rowMapper) {
        return client.executeSql(sql, binder, (row, meta) -> rowMapper.apply(row));
    }

    /**
     * Like {@link #querySource}, for a statement whose whole result the caller reads at once (a keyset window bounded
     * by its {@code LIMIT}, or the per-walk priority statement): the rows are collected into a list by a Reactor
     * subscriber that requests them all, and the list is handed to the stream when the statement has completed. Client
     * memory is bounded by the statement's result.
     * <p>
     * This matters for correctness, not only for speed. r2dbc-postgresql 1.0.7 can deliver a row out of order when
     * demand for rows is signalled from another thread while rows are being received: the fast path of
     * {@code ReactorNettyClient.BackendMessageSubscriber#onNext} does not take the drain guard, so a newer row can
     * overtake one that the drain loop on the requesting thread has dequeued but not yet emitted (fixed in 1.1.2). A
     * Pekko subscriber signals demand exactly that way, in small batches from the stream's own thread. Here the only
     * subscriber is the collecting one; the driver still receives requests in batches (32, then 24, from the
     * {@code flatMap} in {@code DittoPostgresClient#executeSqlPublisher}), but they are issued on the driver's own
     * event-loop thread while it emits a row, never from another thread. Measured on a 1,000,000-pid journal with
     * 256-row pages: 1 page in about 60,000 to 200,000 had one row up to 26 positions late when read through Pekko or
     * with bounded Reactor demand from another thread, none in 400,000 pages read this way. A window must arrive in the
     * statement's order, because its last row is the cursor of the next one.
     * </p>
     */
    private <T> Source<T, NotUsed> pageQuerySource(final String sql,
            @Nullable final java.util.function.Consumer<Statement> binder,
            final java.util.function.Function<Row, T> rowMapper) {
        return Source.lazyCompletionStage(() -> Flux.from(
                                client.executeSqlPublisher(sql, binder, (row, meta) -> rowMapper.apply(row)))
                        .collectList()
                        .toFuture())
                .mapConcat(rows -> rows);
    }

    /**
     * Like {@link #querySource} but binds a positive {@link Statement#fetchSize(int)} ({@link #STREAMING_FETCH_SIZE}) so
     * an unbounded streaming read-journal query opens a server-side portal and the server produces its rows fetch by
     * fetch as Pekko-stream demand asks for them, instead of producing the whole result in one go (see
     * {@link #STREAMING_FETCH_SIZE}). Use ONLY for read-journal streaming statements with no {@code LIMIT}; never on
     * the write path.
     */
    private <T> Source<T, NotUsed> streamingQuerySource(final String sql,
            @Nullable final java.util.function.Consumer<Statement> binder,
            final java.util.function.Function<Row, T> rowMapper) {
        final java.util.function.Consumer<Statement> withFetchSize = statement -> {
            statement.fetchSize(STREAMING_FETCH_SIZE);
            if (binder != null) {
                binder.accept(statement);
            }
        };
        return client.executeSql(sql, withFetchSize, (row, meta) -> rowMapper.apply(row));
    }

    private <T> Source<T, NotUsed> querySourceMany(final String sql,
            @Nullable final java.util.function.Consumer<Statement> binder,
            final java.util.function.Function<Row, T> rowMapper) {
        return querySource(sql, binder, rowMapper);
    }

    private static JournalRow mapJournalRow(final Row row) {
        final String[] tags = row.get("tags", String[].class);
        return new JournalRow(getString(row, "pid"), getLong(row, "sn"), getLong(row, "seq"),
                row.get("manifest", String.class),
                tags == null ? List.of() : List.of(tags),
                getString(row, "event"));
    }

    private static SnapshotRow mapSnapshotRow(final Row row) {
        return new SnapshotRow(getString(row, "pid"), getLong(row, "sn"),
                row.get("lifecycle", String.class),
                row.get("written_at", Instant.class),
                getString(row, "snapshot"));
    }

    private static SnapshotHead mapSnapshotHead(final Row row) {
        return new SnapshotHead(getString(row, "pid"), getLong(row, "sn"),
                row.get("lifecycle", String.class),
                row.get("written_at", Instant.class));
    }

    private static String getString(final Row row, final String name) {
        return row.get(name, String.class);
    }

    private static long getLong(final Row row, final String name) {
        final Long value = row.get(name, Long.class);
        return value == null ? 0L : value;
    }

    private static int getInt(final Row row, final String name) {
        final Integer value = row.get(name, Integer.class);
        return value == null ? 0 : value;
    }

    @Nullable
    private static Long getNullableLong(final Row row, final String name) {
        return row.get(name, Long.class);
    }

    /**
     * A single event to insert into the journal in one {@code AtomicWrite} transaction.
     *
     * @param pid the persistence id.
     * @param sequenceNr the sequence number.
     * @param manifest the Pekko manifest (event type FQN).
     * @param tags the journal tags.
     * @param eventJson the JSONB event value (JSON text).
     */
    public record JournalInsert(String pid, long sequenceNr, String manifest, List<String> tags, String eventJson) {

        public JournalInsert {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /**
     * A pid of a window of {@link #getPidWindowAbove}.
     *
     * @param pid the persistence id; the last one of a window is the cursor of the next window.
     * @param qualifies whether the pid carries the tag as the stream requires (any event, or the newest event).
     */
    public record WindowedPid(String pid, boolean qualifies) {}

    /**
     * A pid of a window of {@link #getPrioritizedPidWindowAbove}.
     *
     * @param pid the persistence id; the last one of a window is the cursor of the next window.
     * @param qualifies whether one of its events carries the tag.
     * @param priority the priority of its newest event that carries the tag (0 if it does not qualify or that event
     * has no {@code priority-<n>} tag).
     */
    public record PrioritizedPid(String pid, boolean qualifies, int priority) {}

}
