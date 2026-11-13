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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link PostgresThingsSearchUpdaterPersistence} — policy fan-out and namespace purge against a
 * real PostgreSQL (PG 16) via Testcontainers. Things are written through the REAL C2 write engine
 * ({@link PostgresSearchUpdaterFlow}) so the {@code referenced_policies} JSONB it stores is exactly what the fan-out
 * reads (end-to-end).
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresPolicyFanoutIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private static final ThrottlingConfig THROTTLE_DISABLED =
            ThrottlingConfig.of(ConfigFactory.parseString("throttling.enabled = false"));
    private static final ThrottlingConfig THROTTLE_ENABLED =
            ThrottlingConfig.of(ConfigFactory.parseString(
                    "throttling { enabled = true, interval = 1s, limit = 1000 }"));

    private PostgresSearchUpdaterFlow flow;
    private PostgresThingsSearchUpdaterPersistence persistence;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPolicyFanoutIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        system = ActorSystem.create("PostgresPolicyFanoutIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    @Before
    public void bootstrapSchema() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.forConnectionFactory(connectionFactory);
        persistence = PostgresThingsSearchUpdaterPersistence.forConnectionFactory(connectionFactory, THROTTLE_DISABLED);
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================================
    // fan-out: directly referenced + imported policies
    // ============================================================================================================

    @Test
    public void directlyReferencedThingIsFoundCarryingTheMapRevision() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-direct");
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "policy-a");
        writeThing(thingId, policy, 3L, Set.of(PolicyTag.of(policy, 3L)));

        // the incoming map carries the NEW revision 9 (a policy modification) — the emitted tag carries 9, not 3.
        final List<PolicyReferenceTag> tags = fanout(Map.of(policy, 9L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(thingId, PolicyTag.of(policy, 9L)));
    }

    @Test
    public void thingReferencingAPolicyViaImportIsFound() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-import");
        final PolicyId ownPolicy = PolicyId.of("org.eclipse.ditto", "policy-own");
        final PolicyId importedPolicy = PolicyId.of("org.eclipse.ditto", "policy-imported");
        writeThing(thingId, ownPolicy, 3L,
                Set.of(PolicyTag.of(ownPolicy, 3L), PolicyTag.of(importedPolicy, 2L)));

        // a modification of ONLY the imported policy must still find the thing (via referenced_policies containment).
        final List<PolicyReferenceTag> tags = fanout(Map.of(importedPolicy, 5L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(thingId, PolicyTag.of(importedPolicy, 5L)));
    }

    @Test
    public void incomingRevisionsMapSubsetEmitsOnlyMatchingThingsAndPolicies() {
        final ThingId thingA = ThingId.of("org.eclipse.ditto", "thing-a");
        final ThingId thingB = ThingId.of("org.eclipse.ditto", "thing-b");
        final PolicyId policy1 = PolicyId.of("org.eclipse.ditto", "policy-1");
        final PolicyId policy2 = PolicyId.of("org.eclipse.ditto", "policy-2");
        final PolicyId policy3 = PolicyId.of("org.eclipse.ditto", "policy-3");
        writeThing(thingA, policy1, 1L, Set.of(PolicyTag.of(policy1, 1L), PolicyTag.of(policy2, 1L)));
        writeThing(thingB, policy3, 1L, Set.of(PolicyTag.of(policy3, 1L)));

        // map hits policy2 (only thingA references it) and policy3 (only thingB) — policy1 is absent from the map.
        final List<PolicyReferenceTag> tags = fanout(Map.of(policy2, 7L, policy3, 8L));

        assertThat(tags).containsExactlyInAnyOrder(
                PolicyReferenceTag.of(thingA, PolicyTag.of(policy2, 7L)),
                PolicyReferenceTag.of(thingB, PolicyTag.of(policy3, 8L)));
    }

    @Test
    public void enabledThrottleStillDeliversEveryTag() {
        final PostgresThingsSearchUpdaterPersistence throttled =
                PostgresThingsSearchUpdaterPersistence.forConnectionFactory(connectionFactory, THROTTLE_ENABLED);
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "policy-t");
        final List<ThingId> thingIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-thr-" + i);
            thingIds.add(thingId);
            writeThing(thingId, policy, 1L, Set.of(PolicyTag.of(policy, 1L)));
        }

        final List<PolicyReferenceTag> tags = runFanout(throttled, Map.of(policy, 2L));

        assertThat(tags.stream().map(PolicyReferenceTag::getThingId).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(thingIds);
    }

    // ============================================================================================================
    // round-2 Critical: same-revision, policy-only fan-out makes newer AUTH visible (end-to-end via C2's flow)
    // ============================================================================================================

    @Test
    public void sameRevisionPolicyOnlyFanoutMakesNewerAuthVisible() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-fanout");
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "policy-f");

        // 1. index the thing at revision 9 with OLD auth.
        writeThing(thingId, policy, 9L, Set.of(PolicyTag.of(policy, 9L)), List.of("old-subject"));
        assertThat(scalar("SELECT array_to_string(global_read, ',') FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("old-subject");

        // 2. the policy is modified -> fan-out finds the thing (proving detection).
        final List<PolicyReferenceTag> tags = fanout(Map.of(policy, 10L));
        assertThat(tags).containsExactly(PolicyReferenceTag.of(thingId, PolicyTag.of(policy, 10L)));

        // 3. the updater re-writes the thing at the SAME thing revision 9 (only the policy/auth changed) via C2's flow.
        writeThing(thingId, policy, 9L, Set.of(PolicyTag.of(policy, 10L)), List.of("new-subject"));

        // 4. the newer auth is now visible in the index — the unconditional upsert landed despite the equal revision.
        assertThat(scalar("SELECT array_to_string(global_read, ',') FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("new-subject");
    }

    // ============================================================================================================
    // containment probe is GIN-index-served
    // ============================================================================================================

    @Test
    public void containmentProbeIsIndexServed() {
        // seed enough rows that the planner has a real choice, then force it off seqscan and read the plan.
        for (int i = 0; i < 50; i++) {
            final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-idx-" + i);
            final PolicyId policy = PolicyId.of("org.eclipse.ditto", "policy-idx-" + i);
            writeThing(thingId, policy, 1L, Set.of(PolicyTag.of(policy, 1L)));
        }

        final String plan = explainFanout("org.eclipse.ditto:policy-idx-1");
        assertThat(plan)
                .as("the referenced_policies containment probe must be served by the st_referenced_pols GIN index")
                .contains("st_referenced_pols");
    }

    // ============================================================================================================
    // namespace purge: epoch-0 marker; marked rows stay visible to plain SELECTs
    // ============================================================================================================

    @Test
    public void purgeMarksNamespaceRowsWithEpochZeroAndKeepsThemVisible() {
        final ThingId thingA1 = ThingId.of("ns.purge", "thing-1");
        final ThingId thingA2 = ThingId.of("ns.purge", "thing-2");
        final ThingId thingB = ThingId.of("ns.keep", "thing-3");
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "policy-p");
        writeThing(thingA1, policy, 1L, Set.of(PolicyTag.of(policy, 1L)));
        writeThing(thingA2, policy, 1L, Set.of(PolicyTag.of(policy, 1L)));
        writeThing(thingB, policy, 1L, Set.of(PolicyTag.of(policy, 1L)));

        final List<Throwable> errors = runPurge("ns.purge");
        assertThat(errors).as("a successful purge reports no errors").isEmpty();

        // EPOCH 0 exactly — to_timestamp(0), NOT now().
        assertThat(scalar("SELECT bool_and(delete_at = to_timestamp(0)) FROM search_things WHERE namespace = 'ns.purge'"))
                .as("purged rows carry the epoch-0 marker").isEqualTo("true");
        assertThat(scalar("SELECT delete_at AT TIME ZONE 'UTC' FROM search_things WHERE thing_id = '" + thingA1 + "'"))
                .startsWith("1970-01-01T00:00");

        // marked rows are STILL present to plain SELECTs (reads do not filter delete_at).
        assertThat(scalar("SELECT count(*) FROM search_things WHERE namespace = 'ns.purge'")).isEqualTo("2");

        // the other namespace is untouched.
        assertThat(scalar("SELECT delete_at IS NULL FROM search_things WHERE thing_id = '" + thingB + "'"))
                .isEqualTo("true");
    }

    // ============================================================================================================
    // helpers
    // ============================================================================================================

    private List<PolicyReferenceTag> fanout(final Map<PolicyId, Long> policyRevisions) {
        return runFanout(persistence, policyRevisions);
    }

    private List<PolicyReferenceTag> runFanout(final PostgresThingsSearchUpdaterPersistence target,
            final Map<PolicyId, Long> policyRevisions) {
        try {
            return target.getPolicyReferenceTags(policyRevisions)
                    .runWith(Sink.seq(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("getPolicyReferenceTags failed", e);
        }
    }

    private List<Throwable> runPurge(final String namespace) {
        try {
            return persistence.purge(namespace)
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("purge failed", e);
        }
    }

    private void writeThing(final ThingId thingId, final PolicyId policyId, final long revision,
            final Set<PolicyTag> referencedPolicies) {
        writeThing(thingId, policyId, revision, referencedPolicies, List.of("subject"));
    }

    private void writeThing(final ThingId thingId, final PolicyId policyId, final long revision,
            final Set<PolicyTag> referencedPolicies, final List<String> globalRead) {
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", (int) revision)
                .build();
        final SearchIndexDocument document = SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(revision)
                .policyId(policyId)
                .policyRevision(referencedPolicies.stream()
                        .filter(tag -> tag.getEntityId().equals(policyId))
                        .findFirst().map(PolicyTag::getRevision).orElse(revision))
                .referencedPolicies(referencedPolicies)
                .globalRead(globalRead)
                .thing(thing)
                .build();
        final Metadata metadata =
                Metadata.of(thingId, revision, PolicyTag.of(policyId, revision), null, referencedPolicies, null);
        run(ThingWriteModel.of(metadata, document));
    }

    private void run(final AbstractWriteModel model) {
        final UpdaterData data = new UpdaterData(model, model);
        try {
            Source.single(data)
                    .via(flow.create())
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("write flow failed", e);
        }
    }

    /** Force the planner off seqscan and EXPLAIN the fan-out query with a literal probe (same shape as the bound SQL). */
    private static String explainFanout(final String policyId) {
        final String probe = "[{\"id\": \"" + policyId + "\"}]";
        final String explainSql = "EXPLAIN SELECT thing_id, policy_id, referenced_policies::text "
                + "FROM search_things WHERE policy_id = ANY(ARRAY['" + policyId + "']) "
                + "OR referenced_policies @> ANY(ARRAY['" + probe + "']::jsonb[])";
        return Flux.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement("SET enable_seqscan = off").execute())
                                .flatMap(Result::getRowsUpdated)
                                .thenMany(Flux.from(conn.createStatement(explainSql).execute())
                                        .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))),
                        Connection::close)
                .collectList()
                .block()
                .stream()
                .collect(Collectors.joining("\n"));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
