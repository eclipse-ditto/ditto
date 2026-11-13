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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchUpdaterPersistence;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL implementation of the backend-neutral {@link ThingsSearchUpdaterPersistence} — the updater-side persistence
 * operations of the Postgres things-search backend (plan §3.4 policy fan-out; §3.6 namespace purge). It mirrors
 * {@code MongoThingsSearchUpdaterPersistence} method-by-method:
 * <ul>
 *     <li>{@link #getPolicyReferenceTags(Map)} — the policy fan-out lookup. A single containment query over
 *     {@code search_things} finds every thing whose OWN {@code policy_id} or whose imported
 *     {@code referenced_policies} references any changed policy, then per row emits one {@link PolicyReferenceTag} per
 *     referenced policy present in the incoming policy-revisions map, carrying the map's NEW revision (transcribing the
 *     Mongo impl's {@code mapConcat} exactly; {@code policy_rev} is NOT part of the projection). The stream is throttled
 *     with the same {@link ThrottlingConfig} the Mongo impl consumes.</li>
 *     <li>{@link #purge(CharSequence)} — the namespace purge. An {@code UPDATE … SET delete_at = to_timestamp(0)} marks
 *     every row of the namespace with EPOCH 0 (mirroring the Mongo {@code BsonDateTime(0)} marker — NOT {@code now()}).
 *     Reads never filter {@code delete_at}, so marked rows stay visible to plain SELECTs; only the in-service reaper
 *     (a later phase) and {@code sudoStreamMetadata} observe the marker.</li>
 * </ul>
 * <p>
 * <strong>referenced_policies storage (C2 review adjudication):</strong> the {@code referenced_policies} JSONB column
 * stores the FULL policy tag json ({@code {"type":"policy","id":…,"revision":N}}), Mongo-faithfully. The fan-out
 * containment probe therefore uses an array-wrapped partial object {@code [{"id":…}]} (GIN-served by the
 * {@code st_referenced_pols jsonb_path_ops} index — verified in Phase 0 and in {@code PostgresPolicyFanoutIT}).
 * </p>
 * <p>
 * <strong>Plancache note (plan §3.5 / brief req 5):</strong> the fan-out query is a single constant-text named prepared
 * statement reused across calls; only the two bind arrays ({@code $1} policy-id {@code text[]}, {@code $2} probe
 * {@code jsonb[]}) vary. Unlike the read path there is no per-value {@code wpath} literal, so the wpath-parameterized
 * generic-plan flip measured in Task 0.2b does not apply here. The probe-array contents do change selectivity between
 * calls, which is recorded for Phase D's plancache-guard decision — no new mechanism is introduced in C3.
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresThingsSearchUpdaterPersistence implements ThingsSearchUpdaterPersistence {

    /** The JSON field naming a policy id inside a stored {@link PolicyTag} (mirrors Mongo {@code FIELD_REFERENCED_POLICY_ID}). */
    private static final String POLICY_ID_FIELD = "id";

    /**
     * Policy fan-out (plan §3.4 corrected projection). Finds every search-index entry affected by a set of changed
     * policies: a thing is affected if its OWN {@code policy_id} is one of them (kept for backwards compatibility with
     * entries written before the {@code referenced_policies} field existed) OR its {@code referenced_policies} array
     * contains any of them. {@code referenced_policies} is cast to {@code text} so the value is read back as a plain
     * JSON string (avoiding jsonb-codec ambiguity). Binds: {@code $1} the changed policy ids as {@code text[]},
     * {@code $2} the array-wrapped containment probes {@code [{"id":…}]} as {@code jsonb[]}.
     */
    static final String FANOUT_SQL =
            "SELECT thing_id, policy_id, referenced_policies::text AS referenced_policies "
                    + "FROM search_things "
                    + "WHERE policy_id = ANY($1) OR referenced_policies @> ANY($2::jsonb[])";

    /**
     * Namespace purge (plan §3.6). Marks every row of the namespace with EPOCH 0 — {@code to_timestamp(0)} =
     * {@code 1970-01-01T00:00:00Z}, mirroring the Mongo {@code BsonDateTime(0)} marker (NOT {@code now()}). Binds:
     * {@code $1} the namespace.
     */
    static final String PURGE_SQL =
            "UPDATE search_things SET delete_at = to_timestamp(0) WHERE namespace = $1";

    private final ConnectionFactory connectionFactory;
    private final ThrottlingConfig throttling;

    private PostgresThingsSearchUpdaterPersistence(final ConnectionFactory connectionFactory,
            final ThrottlingConfig throttling) {
        this.connectionFactory = connectionFactory;
        this.throttling = throttling;
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @param throttling the throttling config applied to the policy-fan-out stream — the same config the Mongo impl
     * consumes ({@code policyModificationCausedSearchIndexUpdateThrottling}), resolved neutrally by the provider.
     * @return the updater persistence.
     */
    public static PostgresThingsSearchUpdaterPersistence of(final DittoPostgresClient client,
            final ThrottlingConfig throttling) {
        return new PostgresThingsSearchUpdaterPersistence(
                Objects.requireNonNull(client, "client").getConnectionPool(),
                Objects.requireNonNull(throttling, "throttling"));
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back the
     * fan-out query and the namespace purge.
     * @param throttling the throttling config applied to the policy-fan-out stream.
     * @return the updater persistence.
     */
    static PostgresThingsSearchUpdaterPersistence forConnectionFactory(final ConnectionFactory connectionFactory,
            final ThrottlingConfig throttling) {
        return new PostgresThingsSearchUpdaterPersistence(
                Objects.requireNonNull(connectionFactory, "connectionFactory"),
                Objects.requireNonNull(throttling, "throttling"));
    }

    @Override
    public Source<PolicyReferenceTag, NotUsed> getPolicyReferenceTags(final Map<PolicyId, Long> policyRevisions) {
        final Set<PolicyId> changedPolicyIds = policyRevisions.keySet();
        final String[] policyIdArray = changedPolicyIds.stream().map(String::valueOf).toArray(String[]::new);
        final String[] probeArray = changedPolicyIds.stream()
                .map(PostgresThingsSearchUpdaterPersistence::containmentProbe)
                .toArray(String[]::new);

        final org.reactivestreams.Publisher<FanoutRow> rowPublisher = Flux.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> {
                    final Statement statement = connection.createStatement(FANOUT_SQL);
                    statement.bind(0, policyIdArray);
                    statement.bind(1, probeArray);
                    return Flux.from(statement.execute())
                            .flatMap(result -> result.map((row, meta) -> new FanoutRow(
                                    row.get("thing_id", String.class),
                                    row.get("policy_id", String.class),
                                    row.get("referenced_policies", String.class))));
                },
                Connection::close);

        final Source<FanoutRow, NotUsed> base = Source.fromPublisher(rowPublisher);
        final Source<FanoutRow, NotUsed> throttled = throttling.isEnabled()
                ? base.throttle(throttling.getLimit(), throttling.getInterval())
                : base;

        return throttled.mapConcat(
                row -> policyReferenceTags(row.thingId(), row.policyId(), row.referencedPolicies(), policyRevisions));
    }

    /**
     * Transcribes {@code MongoThingsSearchUpdaterPersistence#getPolicyReferenceTags}'s {@code mapConcat} as a pure
     * function: for the row's thing, emit one {@link PolicyReferenceTag} per referenced policy (its own {@code policyId}
     * plus every id in {@code referencedPoliciesJson}) that is present in the incoming revisions map, carrying the map's
     * NEW revision. Policies not in the map are dropped. Package-private for direct unit testing (no DB).
     */
    static List<PolicyReferenceTag> policyReferenceTags(final String thingIdString, @Nullable final String policyId,
            @Nullable final String referencedPoliciesJson, final Map<PolicyId, Long> policyRevisions) {
        final ThingId thingId = ThingId.of(thingIdString);
        final Set<PolicyId> referencedPolicyIds = referencedPolicyIds(policyId, referencedPoliciesJson);
        return referencedPolicyIds.stream()
                .map(referencedPolicyId -> Optional.ofNullable(policyRevisions.get(referencedPolicyId))
                        .map(revision -> PolicyTag.of(referencedPolicyId, revision))
                        .map(policyTag -> PolicyReferenceTag.of(thingId, policyTag))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * The set of policies referenced by a search-index entry: its OWN {@code policy_id} (when present) plus the id of
     * every element of the stored {@code referenced_policies} array — mirroring the Mongo impl's
     * {@code referencedPolicyIds(Document)}. A {@code null}/absent {@code policy_id} is tolerated (the Postgres column is
     * nullable and a tombstone/emptied-out row may match purely via {@code referenced_policies}); the Mongo impl assumes
     * it is always present.
     */
    private static Set<PolicyId> referencedPolicyIds(@Nullable final String policyId,
            @Nullable final String referencedPoliciesJson) {
        final Set<PolicyId> referencedPolicyIds = new LinkedHashSet<>();
        if (policyId != null && !policyId.isEmpty()) {
            referencedPolicyIds.add(PolicyId.of(policyId));
        }
        if (referencedPoliciesJson != null && !referencedPoliciesJson.isBlank()) {
            final JsonArray array = JsonFactory.newArray(referencedPoliciesJson);
            array.forEach(value -> {
                if (value.isObject()) {
                    value.asObject().getValue(POLICY_ID_FIELD)
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .map(PolicyId::of)
                            .ifPresent(referencedPolicyIds::add);
                }
            });
        }
        return referencedPolicyIds;
    }

    /**
     * The array-wrapped partial-object containment probe {@code [{"id":"<policyId>"}]} for one changed policy, built via
     * the JSON model (never string concatenation) so a policy id is never interpolated unescaped. Matched against a
     * stored {@code referenced_policies} array via {@code @>} — GIN-served.
     */
    private static String containmentProbe(final PolicyId policyId) {
        return JsonArray.newBuilder()
                .add(JsonObject.newBuilder().set(POLICY_ID_FIELD, policyId.toString()).build())
                .build()
                .toString();
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        final String ns = namespace.toString();
        final Mono<List<Throwable>> result = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(PURGE_SQL);
                            statement.bind(0, ns);
                            return Flux.from(statement.execute()).flatMap(Result::getRowsUpdated);
                        },
                        Connection::close)
                .then(Mono.<List<Throwable>>just(List.of()))
                .onErrorResume(throwable -> Mono.just(List.of(throwable)));
        return Source.fromPublisher(result);
    }

    /** One projected fan-out row: the thing id, its own policy id (nullable), and its referenced_policies JSON (nullable). */
    private record FanoutRow(String thingId, @Nullable String policyId, @Nullable String referencedPolicies) {}

}
