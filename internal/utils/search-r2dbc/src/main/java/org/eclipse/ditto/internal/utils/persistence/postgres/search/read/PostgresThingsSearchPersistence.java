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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresSortClause;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SearchQueryAssembler;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SelectiveLegs;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceResultEntry;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultListImpl;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL implementation of the backend-neutral {@link ThingsSearchPersistence} read persistence — the Task&nbsp;D4
 * completion of the Phase-C skeleton. Every neutral read method (count / find / namespace report / recovery) is now
 * assembled from the D2 criteria translation, the D3 sort/cursor pieces and the {@link SearchQueryAssembler} (which owns
 * the §3.5 selective-leg rescue CTE), and executed over the shared connection pool with r2dbc. {@code sudoStreamMetadata}
 * (the C3 background-sync source) is unchanged.
 * <p>
 * <b>findAll / cursor.</b> Mirroring {@code MongoThingsSearchPersistence}, {@code findAll} fetches {@code pageLimit + 1}
 * rows (the extra row is the has-next-page probe), projecting {@code thing_id}, {@code t_modified} (for
 * {@link TimestampedThingId}) and the D3 cursor-projection columns. {@link #toResultList} then trims to the page and, on
 * a full page, extracts the last row's sort values (via {@link PostgresSortClause#toSortValues}) into the
 * {@link ResultListImpl} the backend-neutral {@code ThingsSearchCursor} encodes. The resume side is Mongo-parity because
 * the cursor now feeds plain-Java sort values (not BSON) into the resume criteria — see the D4 report's cursor-seam
 * section.
 * </p>
 * <p>
 * <b>Reads never filter {@code delete_at}</b> (plan §3.6): a namespace-purge-marked row stays visible until the reaper
 * physically removes it, matching Mongo (its TTL-marked docs remain queryable until deleted).
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresThingsSearchPersistence implements ThingsSearchPersistence {

    /** Default server-side cursor batch size for the metadata / unlimited streams. */
    static final int DEFAULT_FETCH_SIZE = 100;

    /** Default idle timeout for the {@code findAllUnlimited} stream (mirrors Mongo's {@code idleTimeout(maxQueryTime)}). */
    private static final Duration DEFAULT_STREAM_IDLE_TIMEOUT = Duration.ofMinutes(1);

    /** The JSON field naming a policy id inside a stored {@link PolicyTag}. */
    private static final String POLICY_ID_FIELD = "id";

    private static final String METADATA_SQL_ALL =
            "SELECT thing_id, revision, policy_id, policy_rev, referenced_policies::text AS referenced_policies, "
                    + "t_modified FROM search_things WHERE delete_at IS NULL ORDER BY thing_id";

    private static final String METADATA_SQL_FROM_LOWER =
            "SELECT thing_id, revision, policy_id, policy_rev, referenced_policies::text AS referenced_policies, "
                    + "t_modified FROM search_things WHERE delete_at IS NULL AND thing_id > $1 ORDER BY thing_id";

    /**
     * Recovery projection (inverse of the C2 upsert). {@code emptied_out} is derived from {@code referenced_policies IS
     * NULL} — the Postgres counterpart of the Mongo "missing {@code __referencedPolicies} field" tombstone discriminator
     * (the write path stores {@code NULL} for an emptied-out row and {@code []} for a normal one). JSONB columns are cast
     * to text so they are read back as plain JSON strings.
     */
    private static final String RECOVER_SQL =
            "SELECT namespace, revision, policy_id, policy_rev, "
                    + "referenced_policies::text AS referenced_policies, global_read, thing::text AS thing, "
                    + "policy_auth::text AS policy_auth, features_auth::text AS features_auth, t_modified, "
                    + "(referenced_policies IS NULL) AS emptied_out "
                    + "FROM search_things WHERE thing_id = $1";

    private final ConnectionFactory connectionFactory;
    private final int fetchSize;
    private final Duration streamIdleTimeout;
    private final BooleanSupplier rangeLegsAvailable;

    private PostgresThingsSearchPersistence(final ConnectionFactory connectionFactory, final int fetchSize,
            final Duration streamIdleTimeout, final BooleanSupplier rangeLegsAvailable) {
        this.connectionFactory = connectionFactory;
        this.fetchSize = fetchSize;
        this.streamIdleTimeout = streamIdleTimeout;
        this.rangeLegsAvailable = Objects.requireNonNull(rangeLegsAvailable, "rangeLegsAvailable");
    }

    /**
     * Point legs only (no range legs): the read path as before search schema v3.
     *
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @return the read persistence.
     */
    public static PostgresThingsSearchPersistence of(final DittoPostgresClient client) {
        return of(client, () -> false);
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @param rangeLegsAvailable whether the system index {@code sf_multi} is valid (the service passes its
     * {@code SystemIndexProbe}); read once per statement, so range legs start and stop with the index.
     * @return the read persistence.
     */
    public static PostgresThingsSearchPersistence of(final DittoPostgresClient client,
            final BooleanSupplier rangeLegsAvailable) {
        return new PostgresThingsSearchPersistence(
                Objects.requireNonNull(client, "client").getConnectionPool(), DEFAULT_FETCH_SIZE,
                DEFAULT_STREAM_IDLE_TIMEOUT, rangeLegsAvailable);
    }

    /**
     * Point legs only (no range legs).
     *
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back the
     * read statements.
     * @param fetchSize the server-side cursor batch size for the streaming reads.
     * @return the read persistence.
     */
    static PostgresThingsSearchPersistence forConnectionFactory(final ConnectionFactory connectionFactory,
            final int fetchSize) {
        return forConnectionFactory(connectionFactory, fetchSize, () -> false);
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back the
     * read statements.
     * @param fetchSize the server-side cursor batch size for the streaming reads.
     * @param rangeLegsAvailable whether the system index {@code sf_multi} is valid; read once per statement.
     * @return the read persistence.
     */
    static PostgresThingsSearchPersistence forConnectionFactory(final ConnectionFactory connectionFactory,
            final int fetchSize, final BooleanSupplier rangeLegsAvailable) {
        return new PostgresThingsSearchPersistence(Objects.requireNonNull(connectionFactory, "connectionFactory"),
                fetchSize, DEFAULT_STREAM_IDLE_TIMEOUT, rangeLegsAvailable);
    }

    private SelectiveLegs selectiveLegs() {
        return SelectiveLegs.of(rangeLegsAvailable.getAsBoolean());
    }

    // =================================================================================================================
    // count / find (Phase D read path)
    // =================================================================================================================

    @Override
    public Source<Long, NotUsed> count(final Query query, final List<String> authorizationSubjectIds,
            final DittoHeaders dittoHeaders) {
        final RenderedSql rendered = SearchQueryAssembler.count(query.getCriteria(),
                Objects.requireNonNull(authorizationSubjectIds, "authorizationSubjectIds"), query.getSkip(),
                query.getLimit(), selectiveLegs());
        return countSource(rendered);
    }

    @Override
    public Source<Long, NotUsed> sudoCount(final Query query, final DittoHeaders dittoHeaders) {
        final RenderedSql rendered =
                SearchQueryAssembler.count(query.getCriteria(), null, query.getSkip(), query.getLimit(),
                        selectiveLegs());
        return countSource(rendered);
    }

    private Source<Long, NotUsed> countSource(final RenderedSql rendered) {
        final Mono<Long> count = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> Flux.from(rendered.applyTo(connection.createStatement(rendered.sql())).execute())
                                .flatMap(result -> result.map((row, meta) -> row.get(0, Long.class))),
                        Connection::close)
                .next()
                .defaultIfEmpty(0L);
        return Source.fromPublisher(count);
    }

    @Override
    public Source<ResultList<TimestampedThingId>, NotUsed> findAll(final Query query,
            @Nullable final List<String> authorizationSubjectIds, @Nullable final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        // NOTE: `namespaces` is a Mongo index-hint selector only (the namespace restriction is already folded into the
        // criteria upstream); PostgreSQL chooses its own index, so it is ignored here — consistent with the provider's
        // Mongo-only-knob warning policy.
        final PostgresSortClause sortClause = PostgresSortClause.of(query.getSortOptions());
        final int skip = query.getSkip();
        final int limit = query.getLimit();
        final long limitPlusOne = (long) limit + 1L;
        final RenderedSql rendered = SearchQueryAssembler.findAll(query.getCriteria(), authorizationSubjectIds,
                sortClause, limitPlusOne, skip, selectiveLegs());
        final Mono<ResultList<TimestampedThingId>> result = executeRows(rendered,
                (row, meta) -> new Hit(
                        ThingId.of(row.get("thing_id", String.class)),
                        Optional.ofNullable(row.get("t_modified", Instant.class)),
                        sortClause.toSortValues(row::get)))
                .collectList()
                .map(hits -> toResultList(hits, skip, limit));
        return Source.fromPublisher(result);
    }

    @Override
    public Source<ThingId, NotUsed> findAllUnlimited(final Query query, final List<String> authorizationSubjectIds,
            @Nullable final Set<String> namespaces, final DittoHeaders headers) {
        final Long limit = toRowLimit(query.getLimit());
        final PostgresSortClause sortClause = PostgresSortClause.of(query.getSortOptions());
        final RenderedSql rendered = SearchQueryAssembler.findAllUnlimited(query.getCriteria(),
                Objects.requireNonNull(authorizationSubjectIds, "authorizationSubjectIds"), sortClause, limit,
                query.getSkip(), selectiveLegs());
        final Flux<ThingId> ids =
                executeRows(rendered, (row, meta) -> ThingId.of(row.get("thing_id", String.class)));
        return Source.fromPublisher(ids).idleTimeout(streamIdleTimeout);
    }

    /**
     * Normalizes the neutral {@link Query#getLimit()} value to the row limit {@link SearchQueryAssembler#findAllUnlimited}
     * expects. The unlimited query builder's default limit is the Mongo-transcribed 0-sentinel (MongoDB
     * {@code cursor.limit(0)} == "no limit"); unlike Mongo, SQL {@code LIMIT 0} means zero rows, so both that
     * 0-sentinel (any {@code <= 0} value) and the neutral {@code Integer.MAX_VALUE} unbounded ceiling normalize to
     * {@code null} here. Package-private and pure/static so it is unit-testable without a database.
     *
     * @param queryLimit the neutral {@link Query#getLimit()} value.
     * @return {@code null} for unbounded, otherwise the boxed row limit.
     */
    @Nullable
    static Long toRowLimit(final int queryLimit) {
        return (queryLimit == Integer.MAX_VALUE || queryLimit <= 0) ? null : (long) queryLimit;
    }

    private ResultList<TimestampedThingId> toResultList(final List<Hit> hits, final int skip, final int limit) {
        if (hits.size() <= limit || limit <= 0) {
            return new ResultListImpl<>(timestamped(hits), ResultList.NO_NEXT_PAGE);
        }
        final List<Hit> page = hits.subList(0, limit);
        final Hit last = page.get(limit - 1);
        final long nextPageOffset = (long) skip + limit;
        return new ResultListImpl<>(timestamped(page), nextPageOffset, last.sortValues());
    }

    private static List<TimestampedThingId> timestamped(final List<Hit> hits) {
        final List<TimestampedThingId> out = new ArrayList<>(hits.size());
        for (final Hit hit : hits) {
            out.add(new TimestampedThingId(hit.thingId(), hit.modified()));
        }
        return out;
    }

    // =================================================================================================================
    // namespace report
    // =================================================================================================================

    @Override
    public Source<SearchNamespaceReportResult, NotUsed> generateNamespaceCountReport() {
        final Mono<SearchNamespaceReportResult> report = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> Flux.from(
                                        connection.createStatement(SearchQueryAssembler.Fixed.NAMESPACE_REPORT_SQL)
                                                .execute())
                                .flatMap(result -> result.map((row, meta) -> {
                                    final String namespace = Optional.ofNullable(row.get("namespace", String.class))
                                            .orElse("NOT_MIGRATED");
                                    final Long count = row.get("count", Long.class);
                                    return new SearchNamespaceResultEntry(namespace, count != null ? count : 0L);
                                })),
                        Connection::close)
                .collectList()
                .map(SearchNamespaceReportResult::new);
        return Source.fromPublisher(report);
    }

    // =================================================================================================================
    // recoverLastWriteModel — inverse of the C2 upsert
    // =================================================================================================================

    @Override
    public Source<AbstractWriteModel, NotUsed> recoverLastWriteModel(final ThingId thingId) {
        final Mono<AbstractWriteModel> model = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(RECOVER_SQL);
                            statement.bind(0, thingId.toString());
                            return Flux.from(statement.execute())
                                    .flatMap(result -> result.map((row, meta) -> toWriteModel(thingId, row)));
                        },
                        Connection::close)
                .next()
                // Absent row: the thing is not in the index -> a delete model on a deleted-metadata (Mongo parity).
                .defaultIfEmpty(ThingDeleteModel.of(Metadata.ofDeleted(thingId)));
        return Source.fromPublisher(model);
    }

    private static AbstractWriteModel toWriteModel(final ThingId thingId, final Row row) {
        final Metadata metadata = recoveryMetadata(thingId, row);
        if (Boolean.TRUE.equals(row.get("emptied_out", Boolean.class))) {
            // referenced_policies IS NULL -> the emptied-out tombstone (Mongo: missing __referencedPolicies field).
            return ThingWriteModel.ofEmptiedOut(metadata);
        }
        return ThingWriteModel.of(metadata, recoveredDocument(thingId, row));
    }

    private static Metadata recoveryMetadata(final ThingId thingId, final Row row) {
        final Long revisionValue = row.get("revision", Long.class);
        final long thingRevision = revisionValue != null ? revisionValue : 0L;
        final PolicyId policyId = policyIdOf(row);
        final Long policyRevisionValue = row.get("policy_rev", Long.class);
        final long policyRevision = policyRevisionValue != null ? policyRevisionValue : 0L;
        final Instant modified = row.get("t_modified", Instant.class);
        final PolicyTag thingPolicyTag =
                Optional.ofNullable(policyId).map(id -> PolicyTag.of(id, policyRevision)).orElse(null);
        final Set<PolicyTag> referencedPolicies =
                parseReferencedPolicyTags(row.get("referenced_policies", String.class));
        return Metadata.of(thingId, thingRevision, thingPolicyTag, null, referencedPolicies, modified, null);
    }

    private static SearchIndexDocument recoveredDocument(final ThingId thingId, final Row row) {
        final JsonObject thing = parseObject(row.get("thing", String.class));
        final JsonObject policyAuth = parseObject(row.get("policy_auth", String.class));
        final Long policyRevisionValue = row.get("policy_rev", Long.class);
        final String[] globalReadArray = row.get("global_read", String[].class);
        final List<String> globalRead = globalReadArray != null ? List.of(globalReadArray) : List.of();
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(row.get("namespace", String.class))
                .revision(Optional.ofNullable(row.get("revision", Long.class)).orElse(0L))
                .policyId(policyIdOf(row))
                .policyRevision(policyRevisionValue != null ? policyRevisionValue : 0L)
                .referencedPolicies(parseReferencedPolicyTags(row.get("referenced_policies", String.class)))
                .globalRead(globalRead)
                .thing(thing)
                .policyAuth(policyAuth)
                .features(reconstructFeatures(thing, row.get("features_auth", String.class)))
                .build();
    }

    /**
     * Reconstructs the per-feature index entries from the stored {@code features_auth} map (feature-id -> auth tree) and
     * the {@code thing} JSONB (the source of truth for content, per {@link SearchIndexDocument.FeatureEntry}'s "derive
     * from thing()" contract). {@code content} is taken from {@code thing.features.<id>}; a feature present in the auth
     * map but absent from the thing (should not happen) reconstructs empty content.
     */
    private static List<SearchIndexDocument.FeatureEntry> reconstructFeatures(final JsonObject thing,
            @Nullable final String featuresAuthJson) {
        if (featuresAuthJson == null || featuresAuthJson.isBlank()) {
            return List.of();
        }
        final JsonObject featuresAuth = JsonFactory.newObject(featuresAuthJson);
        final JsonObject thingFeatures = thing.getValue("features")
                .filter(JsonValue::isObject).map(JsonValue::asObject).orElseGet(JsonFactory::newObject);
        final List<SearchIndexDocument.FeatureEntry> features = new ArrayList<>();
        featuresAuth.forEach(field -> {
            final String featureId = field.getKeyName();
            final JsonObject auth = field.getValue().isObject() ? field.getValue().asObject() : JsonFactory.newObject();
            final JsonObject content = thingFeatures.getValue(featureId)
                    .filter(JsonValue::isObject).map(JsonValue::asObject).orElseGet(JsonFactory::newObject);
            features.add(SearchIndexDocument.FeatureEntry.of(featureId, content, auth));
        });
        return features;
    }

    @Nullable
    private static PolicyId policyIdOf(final Row row) {
        final String policyIdInPersistence = row.get("policy_id", String.class);
        return (policyIdInPersistence == null || policyIdInPersistence.isEmpty())
                ? null : PolicyId.of(policyIdInPersistence);
    }

    private static JsonObject parseObject(@Nullable final String json) {
        return (json == null || json.isBlank()) ? JsonFactory.newObject() : JsonFactory.newObject(json);
    }

    // =================================================================================================================
    // background-sync metadata stream (C3 — unchanged)
    // =================================================================================================================

    @Override
    public Source<Metadata, NotUsed> sudoStreamMetadata(final EntityId lowerBound) {
        final boolean fromStart = LowerBound.emptyEntityId(lowerBound.getEntityType()).equals(lowerBound);
        final String sql = fromStart ? METADATA_SQL_ALL : METADATA_SQL_FROM_LOWER;

        final org.reactivestreams.Publisher<Metadata> publisher = Flux.usingWhen(
                reactor.core.publisher.Mono.from(connectionFactory.create()),
                connection -> {
                    final Statement statement = connection.createStatement(sql);
                    if (!fromStart) {
                        statement.bind(0, lowerBound.toString());
                    }
                    statement.fetchSize(fetchSize);
                    return Flux.from(statement.execute())
                            .flatMap(result -> result.map((row, meta) -> readAsMetadata(row)));
                },
                Connection::close);

        return Source.fromPublisher(publisher);
    }

    private static Metadata readAsMetadata(final Row row) {
        final ThingId thingId = ThingId.of(row.get("thing_id", String.class));
        final Long revisionValue = row.get("revision", Long.class);
        final long thingRevision = revisionValue != null ? revisionValue : 0L;
        final PolicyId policyId = policyIdOf(row);
        final Long policyRevisionValue = row.get("policy_rev", Long.class);
        final long policyRevision = policyRevisionValue != null ? policyRevisionValue : 0L;
        final Instant modified = row.get("t_modified", Instant.class);
        final PolicyTag thingPolicyTag =
                Optional.ofNullable(policyId).map(id -> PolicyTag.of(id, policyRevision)).orElse(null);
        final Set<PolicyTag> referencedPolicies = parseReferencedPolicyTags(row.get("referenced_policies", String.class));
        return Metadata.of(thingId, thingRevision, thingPolicyTag, null, referencedPolicies, modified, null);
    }

    private static Set<PolicyTag> parseReferencedPolicyTags(@Nullable final String referencedPoliciesJson) {
        final Set<PolicyTag> referencedPolicies = new LinkedHashSet<>();
        if (referencedPoliciesJson != null && !referencedPoliciesJson.isBlank()) {
            final JsonArray array = JsonFactory.newArray(referencedPoliciesJson);
            array.forEach(value -> {
                if (value.isObject() && value.asObject().contains(POLICY_ID_FIELD)) {
                    referencedPolicies.add(PolicyTag.fromJson(value.asObject()));
                }
            });
        }
        return referencedPolicies;
    }

    // =================================================================================================================
    // execution
    // =================================================================================================================

    private <T> Flux<T> executeRows(final RenderedSql rendered, final BiFunction<Row, RowMetadata, T> mapper) {
        return Flux.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> {
                    final Statement statement = rendered.applyTo(connection.createStatement(rendered.sql()));
                    statement.fetchSize(fetchSize);
                    return Flux.from(statement.execute()).flatMap(result -> result.map(mapper));
                },
                Connection::close);
    }

    /** One projected result row: the thing id, its modified timestamp and the encoded cursor sort values. */
    private record Hit(ThingId thingId, Optional<Instant> modified, JsonArray sortValues) {}

}
