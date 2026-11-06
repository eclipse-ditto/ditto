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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresTableNames;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The PostgreSQL {@link PersistedPayloadStore}: streams and rewrites the rows of the {@code <prefix>_snaps} and
 * {@code <prefix>_journal} tables of one entity type's table set, and keeps the progress record as the single row
 * {@code id = 'current'} of {@code <prefix>_encryption_migration}. The entity type is resolved to its table prefix the
 * way {@link PostgresPersistenceOperationsFactory#operations(String)} does ({@code connection} &rarr;
 * {@code connections}); the schema has a progress table for {@code connections} only, the one entity type whose
 * payloads are rewritten (connectivity's connection-secret encryption migration).
 * <p>
 * <strong>Streams</strong> are keyset-paginated: a fresh statement per page of {@code batchSize} rows, pages until a
 * short page, each page read completely before its rows are emitted (as the read journal's windows, see
 * {@code PostgresPersistenceOperations#pageQuerySource}), so a long, throttled migration holds no server-side portal
 * and no transaction across its pauses. Snapshots are ordered by their primary key {@code (pid, sn, written_at)} and
 * the cursor of a snapshot is that key ({@link SnapshotKey}); journal rows are ordered by {@code seq}, the monotonic
 * identity column, and the cursor is the {@code seq} as a decimal string. A cursor the store cannot parse fails the
 * stream with an {@link IllegalArgumentException} naming the cursor.
 * <p>
 * The <strong>required-field filter</strong> is {@code (col #> $k::text[] IS NOT NULL OR …)}, one bound
 * {@code text[]} path per JSON pointer ({@code /connection/credentials/password} &rarr;
 * {@code {connection,credentials,password}}, parsed by {@link JsonPointer#of}). Its semantics equal MongoDB's
 * {@code $exists}: a field present with a JSON
 * {@code null} value counts as present ({@code #>} yields a jsonb {@code null}, which IS NOT NULL); a missing path, or
 * a path leading through a non-object, does not match, and such a row is not streamed. Table names come from
 * {@link PostgresTableNames}; no value is ever concatenated into SQL.
 * <p>
 * <strong>Rewrites</strong> run the whole list in ONE transaction on ONE pooled connection
 * ({@link PostgresPersistenceOperations#inTransaction}): {@code UPDATE <snaps> SET snapshot = $1::jsonb WHERE pid = $2
 * AND sn = $3 AND written_at = $4} per snapshot, {@code UPDATE <journal> SET event = $1::jsonb WHERE pid = $2 AND
 * sn = $3} per changed journal entry; the stage completes with the sum of the updated rows, or fails as a whole
 * (nothing of a failed batch is written, which is what the caller's failure accounting assumes). A row deleted
 * between streaming and rewriting updates nothing and lowers the count; the stage does not fail for it. A journal
 * entry whose payload equals the stored one issues no statement and counts 0, as an unchanged MongoDB document is not
 * counted as modified. An empty list completes with 0 without a round trip.
 * <p>
 * The <strong>progress record</strong> is stored as JSONB as it is (jsonb normalises key order; the reader looks keys
 * up by name): an upsert on {@code id}, a {@code SELECT} by {@code id} and a {@code DELETE} by {@code id}.
 * <p>
 * The store reads and writes through the single shared pool owned by {@link PostgresClientExtension}, like every other
 * PostgreSQL collaborator of a service; {@link #close()} is therefore a no-op — closing the shared pool when the one
 * actor that uses the store stops would tear it out from under the journal, snapshot-store and read-journal plugins
 * that share it. The statements are observed by the pool's r2dbc-proxy metrics listener (command duration per
 * {@code op_kind}: {@code select}, {@code update}, {@code insert}, {@code delete}), so the store records no metrics of
 * its own.
 *
 * @since 4.0.0
 */
public final class PostgresPersistedPayloadStore implements PersistedPayloadStore {

    /** The suffix of the progress table; the schema creates {@code connections_encryption_migration} only. */
    static final String PROGRESS_TABLE_SUFFIX = "_encryption_migration";

    /** The {@code id} of the one progress row. */
    static final String PROGRESS_ID = "current";

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresPersistedPayloadStore.class);

    private final DittoPostgresClient client;
    private final PostgresPersistenceOperations operations;
    private final PostgresTableNames tables;
    private final String progressTable;

    private PostgresPersistedPayloadStore(final DittoPostgresClient client, final String tablePrefix) {
        this.client = client;
        operations = PostgresPersistenceOperations.of(client, tablePrefix);
        tables = operations.tables();
        progressTable = tables.entityPrefix() + PROGRESS_TABLE_SUFFIX;
    }

    /**
     * Creates a store over the table set of the given entity type on the actor system's single shared PostgreSQL pool.
     *
     * @param entityType the entity type, e.g. {@code "connection"}.
     * @param actorSystem the actor system whose {@link PostgresClientExtension} owns the shared pool.
     * @return the store.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if no PostgreSQL table set is known for the entity type.
     */
    public static PostgresPersistedPayloadStore newInstance(final String entityType, final ActorSystem actorSystem) {
        checkNotNull(entityType, "entityType");
        checkNotNull(actorSystem, "actorSystem");
        final String tablePrefix = PostgresPersistenceOperationsFactory.tablePrefixForEntityType(entityType);
        return new PostgresPersistedPayloadStore(PostgresClientExtension.get(actorSystem).getClient(), tablePrefix);
    }

    /**
     * Creates a store over the table set with the given prefix on the given client (for tests with their own pool).
     *
     * @param client the client.
     * @param tablePrefix the table-set prefix, e.g. {@code "connections"}.
     * @return the store.
     */
    static PostgresPersistedPayloadStore of(final DittoPostgresClient client, final String tablePrefix) {
        return new PostgresPersistedPayloadStore(checkNotNull(client, "client"), checkNotNull(tablePrefix,
                "tablePrefix"));
    }

    /**
     * @return the journal, journal_seq and snapshot table names of the table set this store reads and writes.
     */
    public PostgresTableNames tables() {
        return tables;
    }

    /**
     * @return the name of the progress table of this store's table set ({@code <prefix>_encryption_migration}).
     */
    public String progressTable() {
        return progressTable;
    }

    DittoPostgresClient client() {
        return client;
    }

    @Override
    public Source<PersistedSnapshot, NotUsed> streamSnapshots(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {

        final List<String[]> paths = requiredPaths(requiredFieldPointers);
        final SnapshotKey after;
        try {
            after = afterCursor == null ? null : SnapshotKey.fromCursor(afterCursor);
        } catch (final IllegalArgumentException e) {
            return Source.failed(e);
        }
        // the first page may start from the beginning; every later page starts behind the previous page's last row
        final String firstPageSql = snapshotPageSql(tables.snapsTable(), after != null, paths.size());
        final String nextPageSql = snapshotPageSql(tables.snapsTable(), true, paths.size());
        return PostgresPersistedPayloadStore.<PostgresPersistedSnapshot, SnapshotKey>pages(after, batchSize,
                        PostgresPersistedSnapshot::key,
                        key -> page(key == null ? firstPageSql : nextPageSql, statement -> {
                            int next = 0;
                            if (key != null) {
                                statement.bind(next++, key.pid());
                                statement.bind(next++, key.sn());
                                statement.bind(next++, key.writtenAt());
                            }
                            next = bindPaths(statement, next, paths);
                            statement.bind(next, batchSize);
                        }, PostgresPersistedPayloadStore::mapSnapshot))
                .map(snapshot -> snapshot);
    }

    @Override
    public Source<PersistedJournalEntry, NotUsed> streamJournalEntries(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {

        final List<String[]> paths = requiredPaths(requiredFieldPointers);
        final Long after;
        try {
            after = afterCursor == null ? null : parseJournalCursor(afterCursor);
        } catch (final IllegalArgumentException e) {
            return Source.failed(e);
        }
        final String firstPageSql = journalPageSql(tables.journalTable(), after != null, paths.size());
        final String nextPageSql = journalPageSql(tables.journalTable(), true, paths.size());
        return PostgresPersistedPayloadStore.<PostgresPersistedJournalEntry, Long>pages(after, batchSize,
                        PostgresPersistedJournalEntry::seq,
                        seq -> page(seq == null ? firstPageSql : nextPageSql, statement -> {
                            int next = 0;
                            if (seq != null) {
                                statement.bind(next++, seq);
                            }
                            next = bindPaths(statement, next, paths);
                            statement.bind(next, batchSize);
                        }, PostgresPersistedPayloadStore::mapJournalEntry))
                .map(entry -> entry);
    }

    @Override
    public CompletionStage<Long> rewriteSnapshots(final List<PersistedSnapshot> snapshots) {
        checkNotNull(snapshots, "snapshots");
        final List<PostgresPersistedSnapshot> own = snapshots.stream()
                .map(snapshot -> ownEntry(snapshot, PostgresPersistedSnapshot.class))
                .toList();
        if (own.isEmpty()) {
            return CompletableFuture.completedFuture(0L);
        }
        final String sql = updateSnapshotSql(tables.snapsTable());
        return operations.inTransaction(connection -> Flux.fromIterable(own)
                        .concatMap(snapshot -> rowsUpdated(connection, sql, statement -> {
                            statement.bind(0, snapshot.snapshotJson());
                            statement.bind(1, snapshot.pid());
                            statement.bind(2, snapshot.sn());
                            statement.bind(3, snapshot.writtenAt());
                        }))
                        .reduce(0L, Long::sum))
                .toFuture();
    }

    @Override
    public CompletionStage<Long> rewriteJournalEntries(final List<PersistedJournalEntry> entries) {
        checkNotNull(entries, "entries");
        final List<PostgresPersistedJournalEntry> changed = entries.stream()
                .map(entry -> ownEntry(entry, PostgresPersistedJournalEntry.class))
                // an entry whose payload equals the stored one leaves its row untouched: no statement, counted 0
                .filter(entry -> entry.rewrittenEventJson().isPresent())
                .toList();
        if (changed.isEmpty()) {
            return CompletableFuture.completedFuture(0L);
        }
        final String sql = updateJournalEventSql(tables.journalTable());
        return operations.inTransaction(connection -> Flux.fromIterable(changed)
                        .concatMap(entry -> rowsUpdated(connection, sql, statement -> {
                            statement.bind(0, entry.rewrittenEventJson().orElseThrow());
                            statement.bind(1, entry.pid());
                            statement.bind(2, entry.sn());
                        }))
                        .reduce(0L, Long::sum))
                .toFuture();
    }

    @Override
    public CompletionStage<Optional<JsonObject>> loadProgress() {
        return Flux.from(client.executeSqlPublisher(loadProgressSql(progressTable),
                        statement -> statement.bind(0, PROGRESS_ID),
                        (row, meta) -> row.get("progress", String.class)))
                .next()
                .map(text -> Optional.of(JsonFactory.newObject(text)))
                .defaultIfEmpty(Optional.empty())
                .toFuture();
    }

    @Override
    public CompletionStage<Void> saveProgress(final JsonObject progress) {
        checkNotNull(progress, "progress");
        try {
            final String json = flatRecordText(progress);
            return Mono.from(client.executeUpdatePublisher(saveProgressSql(progressTable), statement -> {
                        statement.bind(0, PROGRESS_ID);
                        statement.bind(1, json);
                    }))
                    .doOnNext(rows -> LOGGER.info("Progress saved: rows={}", rows))
                    .then()
                    .toFuture();
        } catch (final RuntimeException e) {
            // the SPI promises a failed stage for a record that cannot be saved (e.g. not flat), never a synchronous
            // throw: the caller only handles the stage
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<Void> deleteProgress() {
        return Mono.from(client.executeUpdatePublisher(deleteProgressSql(progressTable),
                        statement -> statement.bind(0, PROGRESS_ID)))
                .then()
                .toFuture();
    }

    /**
     * A no-op: the store reads and writes through the single shared pool of the actor system, which
     * {@link PostgresClientExtension} owns and disposes on coordinated shutdown. Closing that pool here, when the one
     * actor using this store stops, would tear it out from under the journal, snapshot-store and read-journal plugins.
     */
    @Override
    public void close() {
        // the shared pool is owned by PostgresClientExtension
    }

    // ---- SQL, built from table names and parameter counts only; every value is a bound parameter ----

    static String snapshotPageSql(final String snapsTable, final boolean afterCursor, final int pointerCount) {
        final StringBuilder sql = new StringBuilder("SELECT pid, sn, written_at, snapshot::text AS snapshot FROM ")
                .append(snapsTable)
                .append(" WHERE ");
        int next = 1;
        if (afterCursor) {
            sql.append("(pid, sn, written_at) > ($1, $2, $3) AND ");
            next = 4;
        }
        sql.append(requiredFieldsPredicate("snapshot", next, pointerCount));
        next += pointerCount;
        return sql.append(" ORDER BY pid, sn, written_at LIMIT $").append(next).toString();
    }

    static String journalPageSql(final String journalTable, final boolean afterCursor, final int pointerCount) {
        final StringBuilder sql = new StringBuilder("SELECT pid, sn, seq, event::text AS event FROM ")
                .append(journalTable)
                .append(" WHERE ");
        int next = 1;
        if (afterCursor) {
            sql.append("seq > $1 AND ");
            next = 2;
        }
        sql.append(requiredFieldsPredicate("event", next, pointerCount));
        next += pointerCount;
        return sql.append(" ORDER BY seq LIMIT $").append(next).toString();
    }

    /**
     * {@code (col #> $k::text[] IS NOT NULL OR col #> $k+1::text[] IS NOT NULL …)}: MongoDB's {@code $exists} — a
     * JSON {@code null} value is present, a missing path is not.
     */
    static String requiredFieldsPredicate(final String column, final int firstParameter, final int pointerCount) {
        return IntStream.range(firstParameter, firstParameter + pointerCount)
                .mapToObj(i -> column + " #> $" + i + "::text[] IS NOT NULL")
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    static String updateSnapshotSql(final String snapsTable) {
        return "UPDATE " + snapsTable + " SET snapshot = $1::jsonb WHERE pid = $2 AND sn = $3 AND written_at = $4";
    }

    static String updateJournalEventSql(final String journalTable) {
        return "UPDATE " + journalTable + " SET event = $1::jsonb WHERE pid = $2 AND sn = $3";
    }

    static String saveProgressSql(final String progressTable) {
        return "INSERT INTO " + progressTable + " (id, progress, updated_at) VALUES ($1, $2::jsonb, clock_timestamp()) "
                + "ON CONFLICT (id) DO UPDATE SET progress = EXCLUDED.progress, updated_at = EXCLUDED.updated_at";
    }

    static String loadProgressSql(final String progressTable) {
        return "SELECT progress::text AS progress FROM " + progressTable + " WHERE id = $1";
    }

    static String deleteProgressSql(final String progressTable) {
        return "DELETE FROM " + progressTable + " WHERE id = $1";
    }

    /**
     * Turns the required-field pointers into the {@code text[]} paths bound to the filter: {@code /a/b} &rarr;
     * {@code {a, b}}.
     *
     * @throws NullPointerException if the list is {@code null}.
     * @throws IllegalArgumentException if the list is empty (an empty {@code OR} matches nothing, which is never what
     * a caller means; failing here keeps the SPI contract).
     */
    static List<String[]> requiredPaths(final List<String> requiredFieldPointers) {
        checkNotNull(requiredFieldPointers, "requiredFieldPointers");
        checkArgument(requiredFieldPointers, pointers -> !pointers.isEmpty(),
                () -> "The requiredFieldPointers must not be empty: at least one field must be required.");
        return requiredFieldPointers.stream()
                .map(PostgresPersistedPayloadStore::pathOf)
                .toList();
    }

    static String[] pathOf(final String pointer) {
        final JsonPointer jsonPointer = JsonPointer.of(checkNotNull(pointer, "pointer"));
        return StreamSupport.stream(jsonPointer.spliterator(), false)
                .map(JsonKey::toString)
                .toArray(String[]::new);
    }

    static long parseJournalCursor(final String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("The journal cursor <" + cursor + "> is not one the PostgreSQL payload "
                    + "store produced (expected the decimal seq of a journal row).", e);
        }
    }

    /**
     * Renders the progress record as the JSON text to store, after checking that it is flat (strings, numbers,
     * booleans and nulls only), so every backend refuses the same records.
     *
     * @throws IllegalArgumentException if a value is a JSON object or array.
     */
    static String flatRecordText(final JsonObject progress) {
        for (final JsonField field : progress) {
            final JsonValue value = field.getValue();
            // a JSON null answers isObject() and isArray() with true in Ditto's JSON model; it is a scalar here
            if (!value.isNull() && (value.isObject() || value.isArray())) {
                throw new IllegalArgumentException("The progress record field <" + field.getKeyName()
                        + "> is not a scalar: " + field.getValue());
            }
        }
        return progress.toString();
    }

    // ---- execution ----

    private static int bindPaths(final Statement statement, final int first, final List<String[]> paths) {
        int next = first;
        for (final String[] path : paths) {
            statement.bind(next++, path);
        }
        return next;
    }

    /**
     * Pages until a short page: one statement per page, the page collected completely by a Reactor subscriber before
     * its rows are emitted (the driver can deliver a row out of order when demand is signalled from another thread
     * while rows arrive; a page's last row is the cursor of the next page, so it must arrive in statement order), the
     * next page keyed by the last row of the previous one.
     */
    private static <E, K> Source<E, NotUsed> pages(@Nullable final K start, final int batchSize,
            final Function<E, K> keyOf, final Function<K, CompletionStage<List<E>>> fetchPage) {

        checkArgument(batchSize, size -> size > 0, () -> "The batchSize must be positive, was: " + batchSize);
        return Source.unfoldAsync(new PageState<>(start, false), state -> {
                    if (state.done()) {
                        return CompletableFuture.completedFuture(Optional.<Pair<PageState<K>, List<E>>>empty());
                    }
                    return fetchPage.apply(state.after()).thenApply(rows -> {
                        if (rows.isEmpty()) {
                            return Optional.<Pair<PageState<K>, List<E>>>empty();
                        }
                        final PageState<K> next = new PageState<>(keyOf.apply(rows.get(rows.size() - 1)),
                                rows.size() < batchSize);
                        return Optional.of(Pair.create(next, rows));
                    });
                })
                .mapConcat(rows -> rows);
    }

    private <E> CompletionStage<List<E>> page(final String sql, final Consumer<Statement> binder,
            final Function<Row, E> rowMapper) {
        return Flux.from(client.executeSqlPublisher(sql, binder, (row, meta) -> rowMapper.apply(row)))
                .collectList()
                .toFuture();
    }

    private static Mono<Long> rowsUpdated(final Connection connection, final String sql,
            final Consumer<Statement> binder) {
        final Statement statement = connection.createStatement(sql);
        binder.accept(statement);
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .reduce(0L, Long::sum);
    }

    private static PostgresPersistedSnapshot mapSnapshot(final Row row) {
        return PostgresPersistedSnapshot.of(row.get("pid", String.class), requireLong(row, "sn"),
                row.get("written_at", Instant.class), row.get("snapshot", String.class));
    }

    private static PostgresPersistedJournalEntry mapJournalEntry(final Row row) {
        return PostgresPersistedJournalEntry.of(row.get("pid", String.class), requireLong(row, "sn"),
                requireLong(row, "seq"), row.get("event", String.class));
    }

    private static long requireLong(final Row row, final String column) {
        return checkNotNull(row.get(column, Long.class), column);
    }

    private static <T> T ownEntry(final Object entry, final Class<T> type) {
        if (!type.isInstance(entry)) {
            throw new IllegalArgumentException("Expected a " + type.getSimpleName() + " streamed by this store, got <"
                    + (entry == null ? "null" : entry.getClass().getName()) + ">.");
        }
        return type.cast(entry);
    }

    /** Where the next page starts, and whether the previous one was short (no further page is fetched). */
    private record PageState<K>(@Nullable K after, boolean done) {}

}
