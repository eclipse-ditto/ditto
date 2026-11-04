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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.FlatRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL implementation of the backend-neutral {@link SearchUpdaterFlow} seam — the write engine of the Postgres
 * things-search backend (plan §3.4 as amended). Each incoming {@link UpdaterData} carries exactly ONE write model
 * (today's "bulk" is one thing — the same per-thing framing {@code MongoSearchUpdaterFlow} keeps); this flow applies it
 * in ONE transaction on a pooled connection and emits exactly one {@link UpdaterResult}. Per-thing ordering is
 * guaranteed upstream by {@code ThingUpdater} serialization, so cross-thing parallelism (independent actor streams) is
 * safe; within a single stream the writes stay ordered ({@code flatMapConcat}), mirroring the Mongo flow's shape.
 * <p>
 * <strong>Transaction semantics per model type (plan §3.4):</strong>
 * </p>
 * <ul>
 *     <li><strong>Full write</strong> ({@link ThingWriteModel}, not emptied-out, not no-op): an
 *     <em>UNCONDITIONAL</em> {@code INSERT … ON CONFLICT (thing_id) DO UPDATE SET &lt;every column&gt;} of the doc row
 *     (NO revision predicate — a policy fan-out writes the same thing revision with newer auth data and MUST land;
 *     ordering safety comes from {@code ThingUpdater} + {@code isNextWriteModelOutDated}, not the DB — round-2
 *     Critical), followed IN THE SAME TRANSACTION by the v1.5 anti-join flat maintenance (delete-changed /
 *     insert-changed against the freshly flattened row set staged via {@code unnest}, one bind per COLUMN).</li>
 *     <li><strong>Emptied-out</strong> ({@link ThingWriteModel#isEmptiedOut()}): upsert the (emptied) doc row exactly
 *     like a full write but with {@code referenced_policies = NULL} (mirroring the Mongo tombstone, which omits
 *     {@code __referencedPolicies}), and delete ALL flat rows of the thing.</li>
 *     <li><strong>Delete</strong> ({@link ThingDeleteModel}): {@code DELETE FROM search_things} (the FK cascade clears
 *     the flat rows). Deleting a non-existent thing is a success (Mongo delete semantics).</li>
 *     <li><strong>No-op</strong> ({@link ThingWriteModel#isNoop()}): no DB operation; an immediate success result.</li>
 * </ul>
 * <p>
 * Results feed {@link SearchWriteResult#classify()} through {@link PostgresSearchWriteResults}: a unique violation
 * (23505) is a success (Mongo duplicate-key rule), an FK violation (23503) or any other SQLSTATE is a per-thing
 * WRITE_ERROR (retry resolves), and every failure is caught per element so one thing's failure never aborts the stream
 * (unordered-bulk parity). The transaction is rolled back on any error.
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresSearchUpdaterFlow implements SearchUpdaterFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSearchUpdaterFlow.class);

    /** The {@code _modified} ISO-8601 timestamp field of a thing payload, parsed by the writer into {@code t_modified}. */
    private static final String MODIFIED_FIELD = "_modified";

    /**
     * Unconditional doc-row upsert (plan §3.4 step 1). NO revision predicate anywhere. {@code updated_at} is set by the
     * writer via {@code now()}; {@code delete_at} is written NULL (tombstone TTL / reaper is a later phase). The
     * {@code RETURNING (xmax = 0) AS inserted} idiom reports whether the row was inserted ({@code xmax = 0}) or updated.
     * Binds: 1 thing_id, 2 namespace, 3 revision, 4 policy_id, 5 policy_rev, 6 referenced_policies(jsonb),
     * 7 global_read(text[]), 8 thing(jsonb), 9 policy_auth(jsonb), 10 features_auth(jsonb), 11 t_modified,
     * 12 delete_at.
     */
    static final String UPSERT_DOC_SQL =
            "INSERT INTO search_things "
                    + "(thing_id, namespace, revision, policy_id, policy_rev, referenced_policies, global_read, "
                    + "thing, policy_auth, features_auth, t_modified, delete_at, updated_at) "
                    + "VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8::jsonb, $9::jsonb, $10::jsonb, $11, $12, now()) "
                    + "ON CONFLICT (thing_id) DO UPDATE SET "
                    + "namespace = EXCLUDED.namespace, revision = EXCLUDED.revision, "
                    + "policy_id = EXCLUDED.policy_id, policy_rev = EXCLUDED.policy_rev, "
                    + "referenced_policies = EXCLUDED.referenced_policies, global_read = EXCLUDED.global_read, "
                    + "thing = EXCLUDED.thing, policy_auth = EXCLUDED.policy_auth, "
                    + "features_auth = EXCLUDED.features_auth, t_modified = EXCLUDED.t_modified, "
                    + "delete_at = EXCLUDED.delete_at, updated_at = EXCLUDED.updated_at "
                    + "RETURNING (xmax = 0) AS inserted";

    /**
     * v1.5 delete-changed / insert-changed flat maintenance (plan §3.4 step 2, adopted in Task 0.3): ONE statement,
     * two writable CTEs sharing one {@code MATERIALIZED} unnest of the freshly flattened rows. PostgreSQL evaluates all
     * data-modifying CTEs in a statement against the SAME pre-statement snapshot (verified on PG 16), so the
     * {@code inserted} CTE's {@code NOT EXISTS} is unaffected by the {@code deleted} CTE's concurrent removals: an
     * UNCHANGED row (identical path/wpath/f_id/ord/type_rank/value) is excluded from BOTH the delete and the insert and
     * so is never rewritten (its {@code ctid} is stable — ~99% churn reduction on typical few-leaf updates); only
     * changed leaves are deleted (old value) and re-inserted (new value). Full-row identity is matched on EVERY column
     * via {@code IS NOT DISTINCT FROM} on the nullable ones. The unnest binds ONE array per COLUMN (never per row),
     * sidestepping the 65 535 extended-protocol bind-parameter cap.
     * Binds: 1-8 the eight unnest arrays (paths, wpaths, f_ids, ords, type_ranks, val_bools, val_nums, val_texts),
     * 9 thing_id (delete filter), 10 thing_id (insert SELECT literal), 11 thing_id (insert NOT EXISTS filter).
     */
    static final String ANTIJOIN_FLAT_SQL =
            "WITH new_rows AS MATERIALIZED ("
                    + "  SELECT * FROM unnest($1::text[], $2::text[], $3::text[], $4::int[], $5::smallint[], "
                    + "$6::boolean[], $7::numeric[], $8::text[])"
                    + "    AS u(path, wpath, f_id, ord, type_rank, val_bool, val_num, val_text)"
                    + "), "
                    + "deleted AS ("
                    + "  DELETE FROM search_flat sf"
                    + "  WHERE sf.thing_id = $9"
                    + "    AND NOT EXISTS ("
                    + "      SELECT 1 FROM new_rows n"
                    + "      WHERE n.path = sf.path AND n.wpath = sf.wpath AND n.ord = sf.ord"
                    + "        AND n.type_rank = sf.type_rank"
                    + "        AND n.f_id IS NOT DISTINCT FROM sf.f_id"
                    + "        AND n.val_bool IS NOT DISTINCT FROM sf.val_bool"
                    + "        AND n.val_num IS NOT DISTINCT FROM sf.val_num"
                    + "        AND n.val_text IS NOT DISTINCT FROM sf.val_text)"
                    + "  RETURNING 1"
                    + "), "
                    + "inserted AS ("
                    + "  INSERT INTO search_flat "
                    + "(thing_id, path, wpath, f_id, ord, type_rank, val_bool, val_num, val_text)"
                    + "  SELECT $10, n.path, n.wpath, n.f_id, n.ord, n.type_rank, n.val_bool, n.val_num, n.val_text"
                    + "  FROM new_rows n"
                    + "  WHERE NOT EXISTS ("
                    + "    SELECT 1 FROM search_flat sf"
                    + "    WHERE sf.thing_id = $11 AND sf.path = n.path AND sf.wpath = n.wpath AND sf.ord = n.ord"
                    + "      AND sf.type_rank = n.type_rank"
                    + "      AND sf.f_id IS NOT DISTINCT FROM n.f_id"
                    + "      AND sf.val_bool IS NOT DISTINCT FROM n.val_bool"
                    + "      AND sf.val_num IS NOT DISTINCT FROM n.val_num"
                    + "      AND sf.val_text IS NOT DISTINCT FROM n.val_text)"
                    + "  RETURNING 1"
                    + ") "
                    + "SELECT (SELECT count(*) FROM deleted) AS deleted_count, "
                    + "(SELECT count(*) FROM inserted) AS inserted_count";

    /** Emptied-out tombstone: drop every flat row of the thing (its doc row is upserted separately). */
    static final String DELETE_ALL_FLAT_SQL = "DELETE FROM search_flat WHERE thing_id = $1";

    /** Delete a thing: the FK {@code ON DELETE CASCADE} clears its flat rows. */
    static final String DELETE_THING_SQL = "DELETE FROM search_things WHERE thing_id = $1";

    private final ConnectionFactory connectionFactory;
    private final Function<SearchIndexDocument, List<FlatRow>> flattener;

    private PostgresSearchUpdaterFlow(final ConnectionFactory connectionFactory,
            final Function<SearchIndexDocument, List<FlatRow>> flattener) {
        this.connectionFactory = connectionFactory;
        this.flattener = flattener;
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @return the write flow.
     */
    public static PostgresSearchUpdaterFlow of(final DittoPostgresClient client) {
        return new PostgresSearchUpdaterFlow(Objects.requireNonNull(client, "client").getConnectionPool(),
                ThingFlattener::flatten);
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections the flow
     * runs each per-thing transaction on.
     * @return the write flow.
     */
    static PostgresSearchUpdaterFlow forConnectionFactory(final ConnectionFactory connectionFactory) {
        return forConnectionFactory(connectionFactory, ThingFlattener::flatten);
    }

    /**
     * Test-only seam: builds the flow with an alternate {@code document -> rows} function in place of
     * {@link ThingFlattener#flatten(SearchIndexDocument)}. {@link SearchIndexDocument} is a {@code final} class with
     * no throwing public construction path, so a real DB-free unit test of the eager-flatten error boundary (see
     * {@link #fullWrite}) injects a deliberately-throwing flattener here instead of mocking the document itself.
     *
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections the flow
     * runs each per-thing transaction on.
     * @param flattener the {@code document -> rows} function {@code fullWrite} calls; production callers must pass
     * {@link ThingFlattener#flatten(SearchIndexDocument)} (or use {@link #forConnectionFactory(ConnectionFactory)},
     * which does so by default).
     * @return the write flow.
     */
    static PostgresSearchUpdaterFlow forConnectionFactory(final ConnectionFactory connectionFactory,
            final Function<SearchIndexDocument, List<FlatRow>> flattener) {
        return new PostgresSearchUpdaterFlow(Objects.requireNonNull(connectionFactory, "connectionFactory"),
                Objects.requireNonNull(flattener, "flattener"));
    }

    @Override
    public Flow<UpdaterData, UpdaterResult, NotUsed> create() {
        return Flow.<UpdaterData>create()
                .flatMapConcat(updaterData -> {
                    final AbstractWriteModel model = updaterData.writeModel();
                    return Source.fromPublisher(applyModel(model))
                            .map(result -> new UpdaterResult(model, result));
                });
    }

    private Publisher<SearchWriteResult> applyModel(final AbstractWriteModel model) {
        if (model instanceof ThingDeleteModel) {
            final String thingId = model.getMetadata().getThingId().toString();
            return deleteThing(thingId)
                    .onErrorResume(error -> Mono.just(PostgresSearchWriteResults.fromThrowable(error, true)));
        } else if (model instanceof ThingWriteModel writeModel) {
            if (writeModel.isNoop()) {
                return Mono.just(PostgresSearchWriteResults.noopSuccess());
            }
            final Mono<SearchWriteResult> write = writeModel.isEmptiedOut()
                    ? emptiedOut(writeModel.getDocument())
                    : fullWrite(writeModel.getDocument());
            return write.onErrorResume(
                    error -> Mono.just(PostgresSearchWriteResults.fromThrowable(error, false)));
        } else {
            // No other neutral write-model type exists; never fail the stream if one is ever introduced.
            return Mono.just(SearchWriteResult.unexpectedError(1,
                    new IllegalArgumentException("Unsupported neutral write model: " + model)));
        }
    }

    private Mono<SearchWriteResult> fullWrite(final SearchIndexDocument document) {
        // The whole branch body is deferred (incl. thingId() and, in particular, the flattener call) so that ANY
        // synchronous throw -- not only a failure inside the lazily-built SQL/bind stages -- surfaces as a Mono error
        // reaching applyModel's onErrorResume, rather than escaping eagerly from the Mono-construction call site
        // (which would fail flatMapConcat's mapping function and abort the WHOLE stream instead of just this thing;
        // round-2 review's per-element resilience contract).
        return Mono.defer(() -> {
            final String thingId = document.thingId().toString();
            final List<FlatRow> rows = flattener.apply(document);
            return inTransaction(connection ->
                    upsertDocRow(connection, document, false)
                            .flatMap(inserted -> antiJoinFlat(connection, thingId, rows).thenReturn(inserted)));
        }).map(PostgresSearchWriteResults::writeSuccess);
    }

    private Mono<SearchWriteResult> emptiedOut(final SearchIndexDocument document) {
        // Deferred for the same reason as fullWrite above (no flattener call on this branch today, but any future
        // synchronous computation added here inherits the same per-element error boundary for free).
        return Mono.defer(() -> {
            final String thingId = document.thingId().toString();
            return inTransaction(connection ->
                    upsertDocRow(connection, document, true)
                            .flatMap(inserted -> deleteAllFlat(connection, thingId).thenReturn(inserted)));
        }).map(PostgresSearchWriteResults::writeSuccess);
    }

    private Mono<SearchWriteResult> deleteThing(final String thingId) {
        return inTransaction(connection -> {
            final Statement statement = connection.createStatement(DELETE_THING_SQL);
            statement.bind(0, thingId);
            return rowsUpdated(statement);
        }).map(ignored -> PostgresSearchWriteResults.deleteSuccess());
    }

    private Mono<Boolean> upsertDocRow(final Connection connection, final SearchIndexDocument document,
            final boolean emptiedOut) {
        final Statement statement = connection.createStatement(UPSERT_DOC_SQL);
        bindDocRow(statement, document, emptiedOut);
        return Flux.from(statement.execute())
                .flatMap(result -> result.map((row, meta) -> {
                    final Boolean inserted = row.get("inserted", Boolean.class);
                    return Boolean.TRUE.equals(inserted);
                }))
                .next()
                .defaultIfEmpty(Boolean.FALSE);
    }

    private Mono<Void> antiJoinFlat(final Connection connection, final String thingId, final List<FlatRow> rows) {
        final Statement statement = connection.createStatement(ANTIJOIN_FLAT_SQL);
        bindFlatArrays(statement, rows);
        statement.bind(8, thingId);   // $9  delete filter
        statement.bind(9, thingId);   // $10 insert SELECT literal
        statement.bind(10, thingId);  // $11 insert NOT EXISTS filter
        // The top-level statement is a SELECT of the two counts; consuming the row drives the CTE DML to completion.
        return Flux.from(statement.execute())
                .flatMap(result -> Flux.from(result.map((row, meta) -> Boolean.TRUE)))
                .then();
    }

    private Mono<Void> deleteAllFlat(final Connection connection, final String thingId) {
        final Statement statement = connection.createStatement(DELETE_ALL_FLAT_SQL);
        statement.bind(0, thingId);
        return rowsUpdated(statement).then();
    }

    private static Mono<Long> rowsUpdated(final Statement statement) {
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .map(Number::longValue)
                .reduce(0L, Long::sum);
    }

    /**
     * Runs {@code work} inside a single transaction on a pooled connection, releasing the connection on EVERY terminal
     * path: completion commits then closes; both the error and the cancel arms roll back (best-effort) then close — so
     * a failed write never leaks an "idle in transaction" connection back to the pool. Mirrors persistence-r2dbc's
     * {@code PostgresPersistenceOperations.inTransaction}.
     */
    private <T> Mono<T> inTransaction(final Function<Connection, Mono<T>> work) {
        return Mono.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> Mono.from(connection.beginTransaction()).then(work.apply(connection)),
                connection -> Mono.from(connection.commitTransaction()).then(Mono.from(connection.close())),
                (connection, error) -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())),
                connection -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())));
    }

    // =================================================================================================================
    // binding
    // =================================================================================================================

    private void bindDocRow(final Statement statement, final SearchIndexDocument document, final boolean emptiedOut) {
        statement.bind(0, document.thingId().toString());
        statement.bind(1, document.namespace());
        statement.bind(2, document.revision());
        document.policyId().ifPresentOrElse(
                policyId -> statement.bind(3, policyId.toString()),
                () -> statement.bindNull(3, String.class));
        statement.bind(4, document.policyRevision());
        if (emptiedOut) {
            // Mongo tombstone omits __referencedPolicies -> store NULL (plan §3.4 step 3 / A4 report §3-D3).
            statement.bindNull(5, String.class);
        } else {
            statement.bind(5, referencedPoliciesJson(document));
        }
        statement.bind(6, document.globalRead().toArray(new String[0]));
        statement.bind(7, document.thing().toString());
        statement.bind(8, document.policyAuth().toString());
        statement.bind(9, featuresAuthJson(document));
        final Instant modified = parseModified(document.thing());
        if (modified != null) {
            statement.bind(10, modified);
        } else {
            statement.bindNull(10, Instant.class);
        }
        // delete_at: written NULL by the writer; tombstone-TTL / reaper is a later phase (C3/C4).
        statement.bindNull(11, Instant.class);
    }

    /**
     * The {@code referenced_policies} JSONB column, mirroring Mongo's {@code __referencedPolicies}: an array of the
     * full {@link PolicyTag} JSON ({@code {"type":"policy","id":"…","revision":N}}) so the policy fan-out's containment
     * query ({@code referenced_policies @> '[{"id":…}]'}) is GIN-served and background-sync can read the referenced
     * revisions. An empty set is stored as {@code []} (a normal document), NOT null — null is reserved for the
     * emptied-out tombstone.
     */
    private static String referencedPoliciesJson(final SearchIndexDocument document) {
        final org.eclipse.ditto.json.JsonArrayBuilder builder = JsonArray.newBuilder();
        document.referencedPolicies().forEach(policyTag -> builder.add(policyTag.toJson()));
        return builder.build().toString();
    }

    /** The {@code features_auth} JSONB column: a map {@code feature-id -> per-feature read-auth tree}. */
    private static String featuresAuthJson(final SearchIndexDocument document) {
        final org.eclipse.ditto.json.JsonObjectBuilder builder = JsonObject.newBuilder();
        document.features().forEach(feature -> builder.set(feature.featureId(), feature.auth()));
        return builder.build().toString();
    }

    /**
     * Parse {@code t_modified} from the thing payload's {@code _modified} ISO-8601 string. Returns {@code null} (and
     * logs at debug) when the field is absent or unparseable — a missing/bad timestamp must NOT fail the write.
     */
    private static Instant parseModified(final JsonObject thing) {
        return thing.getValue(MODIFIED_FIELD)
                .filter(org.eclipse.ditto.json.JsonValue::isString)
                .map(org.eclipse.ditto.json.JsonValue::asString)
                .map(PostgresSearchUpdaterFlow::tryParseInstant)
                .orElse(null);
    }

    private static Instant tryParseInstant(final String isoTimestamp) {
        try {
            return Instant.parse(isoTimestamp);
        } catch (final DateTimeParseException e) {
            LOGGER.debug("Ignoring unparseable thing _modified timestamp <{}> for t_modified.", isoTimestamp);
            return null;
        }
    }

    /**
     * Bind the eight per-column arrays for the {@code unnest} of {@link #ANTIJOIN_FLAT_SQL} (indices 0-7 → {@code $1}-
     * {@code $8}), one bind per column, in {@link FlatRow} / {@code search_flat} column order. Nullable columns
     * (f_id, val_bool, val_num, val_text) carry per-element nulls.
     */
    private static void bindFlatArrays(final Statement statement, final List<FlatRow> rows) {
        final int n = rows.size();
        final String[] paths = new String[n];
        final String[] wpaths = new String[n];
        final String[] fIds = new String[n];
        final Integer[] ords = new Integer[n];
        final Short[] typeRanks = new Short[n];
        final Boolean[] valBools = new Boolean[n];
        final BigDecimal[] valNums = new BigDecimal[n];
        final String[] valTexts = new String[n];
        for (int i = 0; i < n; i++) {
            final FlatRow row = rows.get(i);
            paths[i] = row.path();
            wpaths[i] = row.wpath();
            fIds[i] = row.fId();
            ords[i] = row.ord();
            typeRanks[i] = row.typeRank();
            valBools[i] = row.valBool();
            valNums[i] = row.valNum();
            valTexts[i] = row.valText();
        }
        statement.bind(0, paths);
        statement.bind(1, wpaths);
        statement.bind(2, fIds);
        statement.bind(3, ords);
        statement.bind(4, typeRanks);
        statement.bind(5, valBools);
        statement.bind(6, valNums);
        statement.bind(7, valTexts);
    }

}
