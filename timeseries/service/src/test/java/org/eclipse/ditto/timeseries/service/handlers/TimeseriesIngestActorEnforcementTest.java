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
package org.eclipse.ditto.timeseries.service.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for the {@link TimeseriesIngestActor} read-path enforcement chain
 * — {@link RetrieveTimeseries} → {@code SudoRetrieveThing} → {@link PolicyEnforcer} lookup →
 * per-path permission verification. The persistent ingest path is covered separately by
 * {@link TimeseriesIngestActorTest}; here we only exercise the production-wiring branch where
 * both {@code thingsShardRegion} and {@code policyEnforcerProvider} are non-null.
 * <p>
 * Each scenario is one row of the truth table the enforcement code traverses
 * ({@code TimeseriesIngestActor#authorizeAndQuery} → {@code enforce} →
 * {@code resolveSudoReply} / {@code loadEnforcer} / {@code verifyPaths}).
 */
public final class TimeseriesIngestActorEnforcementTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto", "sensor-policy");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");
    private static final String SUBJECT_ID = "integration:ditto";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("TimeseriesIngestActorEnforcementTest",
                ConfigFactory.load("test.conf"));
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void grantedSubjectReceivesResponse() {
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            final PolicyEnforcerProvider provider = stubProvider(policyEnforcerWithGrant(Permission.READ_TS));
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ_TS, "granted-thing");

            actor.tell(retrieveTimeseries(authHeaders("granted-cid")), getRef());

            // Respond to the SudoRetrieveThing ask with a Thing that references the test policy.
            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            thingsShard.reply(SudoRetrieveThingResponse.of(
                    thingWithPolicy(THING_ID, POLICY_ID).toJson(), DittoHeaders.empty()));

            // The adapter returns empty results; what matters is that enforcement let us through.
            final RetrieveTimeseriesResponse response = expectMsgClass(RetrieveTimeseriesResponse.class);
            assertThat((Object) response.getEntityId()).isEqualTo(THING_ID);
            assertThat(response.getDittoHeaders().getCorrelationId()).contains("granted-cid");
        }};
    }

    @Test
    public void subjectWithoutRequiredPermissionGets404() {
        // The hard rule (auth-authz.md): authorization failure surfaces as 404 ThingNotAccessibleException,
        // never 403 — 403 would leak existence to a subject that can't read the resource.
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            // Policy grants READ but not READ_TS — under the default required-permission this is denial.
            final PolicyEnforcerProvider provider = stubProvider(policyEnforcerWithGrant(Permission.READ));
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ_TS, "denied-thing");

            actor.tell(retrieveTimeseries(authHeaders("denied-cid")), getRef());

            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            thingsShard.reply(SudoRetrieveThingResponse.of(
                    thingWithPolicy(THING_ID, POLICY_ID).toJson(), DittoHeaders.empty()));

            final ThingNotAccessibleException denial = expectMsgClass(ThingNotAccessibleException.class);
            assertThat(denial.getMessage()).contains(THING_ID.toString());
            assertThat(denial.getDittoHeaders().getCorrelationId()).contains("denied-cid");
        }};
    }

    @Test
    public void simplifiedReadPermissionUnlocksTimeseriesWhenConfigured() {
        // When the deployment opts into required-permission=READ, a plain READ grant suffices —
        // documented as the "simplified" mode in Permission.READ_TS Javadoc. Verifies that the
        // configured permission name actually drives the check (no hard-coded READ_TS).
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            final PolicyEnforcerProvider provider = stubProvider(policyEnforcerWithGrant(Permission.READ));
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ, "simplified-thing");

            actor.tell(retrieveTimeseries(authHeaders("simplified-cid")), getRef());

            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            thingsShard.reply(SudoRetrieveThingResponse.of(
                    thingWithPolicy(THING_ID, POLICY_ID).toJson(), DittoHeaders.empty()));

            expectMsgClass(RetrieveTimeseriesResponse.class);
        }};
    }

    @Test
    public void thingNotFoundProducesNotAccessible() {
        // SudoRetrieveThing reply is the not-accessible exception itself — the actor must surface it
        // back to the caller as-is (with the right correlation id).
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            final PolicyEnforcerProvider provider = stubProvider(policyEnforcerWithGrant(Permission.READ_TS));
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ_TS, "not-found-thing");

            actor.tell(retrieveTimeseries(authHeaders("nf-cid")), getRef());

            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            thingsShard.reply(ThingNotAccessibleException.newBuilder(THING_ID)
                    .dittoHeaders(authHeaders("nf-cid")).build());

            final ThingNotAccessibleException reply = expectMsgClass(ThingNotAccessibleException.class);
            assertThat(reply.getDittoHeaders().getCorrelationId()).contains("nf-cid");
        }};
    }

    @Test
    public void thingWithoutPolicyIdDeniesAccess() {
        // Configuration anomaly — but it must not leak that the thing exists. Deny with 404 in line
        // with the 404-not-403 discipline.
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            final PolicyEnforcerProvider provider = stubProvider(policyEnforcerWithGrant(Permission.READ_TS));
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ_TS, "no-policy-thing");

            actor.tell(retrieveTimeseries(authHeaders("nopol-cid")), getRef());

            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            // Thing without policyId — the loadEnforcer branch denies on the empty Optional.
            thingsShard.reply(SudoRetrieveThingResponse.of(
                    ThingsModelFactory.newThingBuilder().setId(THING_ID).build().toJson(),
                    DittoHeaders.empty()));

            expectMsgClass(ThingNotAccessibleException.class);
        }};
    }

    @Test
    public void enforcerProviderReturnsEmptyDeniesAccess() {
        // Cache miss + load failure on the policies-service side. The actor must not surface
        // "policy can't be loaded" — that's an existence leak; instead deny with 404.
        new TestKit(actorSystem) {{
            final TestProbe thingsShard = new TestProbe(actorSystem);
            // Provider returns Optional.empty() — same shape as a policy that no longer exists.
            final PolicyEnforcerProvider provider =
                    policyId -> CompletableFuture.completedFuture(Optional.empty());
            final ActorRef actor = startEntity(thingsShard.ref(), provider, recordingAdapter(),
                    Permission.READ_TS, "empty-enforcer-thing");

            actor.tell(retrieveTimeseries(authHeaders("empty-cid")), getRef());

            thingsShard.expectMsgClass(SudoRetrieveThing.class);
            thingsShard.reply(SudoRetrieveThingResponse.of(
                    thingWithPolicy(THING_ID, POLICY_ID).toJson(), DittoHeaders.empty()));

            expectMsgClass(ThingNotAccessibleException.class);
        }};
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private ActorRef startEntity(final ActorRef thingsShard,
            final PolicyEnforcerProvider provider,
            final TimeseriesAdapter adapter,
            final String requiredPermission,
            final String actorNameSuffix) {

        // Cluster sharding sets the entity actor name to the entityId, and the actor URL-decodes
        // its own name to recover the ThingId — so the actor name must be a valid namespaced ID.
        // Each scenario gets a unique suffix to keep the in-memory persistence journal isolated.
        final ThingId entityId = ThingId.of("org.eclipse.ditto", "sensor-" + actorNameSuffix);
        return actorSystem.actorOf(
                Props.create(TimeseriesIngestActor.class, adapter, thingsShard, provider, requiredPermission),
                entityId.toString());
    }

    private static RetrieveTimeseries retrieveTimeseries(final DittoHeaders headers) {
        final TimeseriesQuery query = TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO,
                null, null, null, null, null);
        return RetrieveTimeseries.of(query, headers);
    }

    private static DittoHeaders authHeaders(final String correlationId) {
        return DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(SUBJECT_ID)))
                .build();
    }

    private static Thing thingWithPolicy(final ThingId thingId, final PolicyId policyId) {
        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(policyId)
                .build();
    }

    private static PolicyEnforcerProvider stubProvider(final PolicyEnforcer enforcer) {
        return policyId -> CompletableFuture.completedFuture(Optional.of(enforcer));
    }

    /**
     * Builds a {@link PolicyEnforcer} whose evaluated policy grants {@code permission} on
     * {@code thing:/} for {@code integration:ditto}. Use the produced enforcer with
     * {@link #stubProvider} to drive the production code path of {@code loadEnforcer}.
     */
    private static PolicyEnforcer policyEnforcerWithGrant(final String permission) {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "org.eclipse.ditto:sensor-policy",
                  "entries": {
                    "DEFAULT": {
                      "subjects": { "%s": { "type": "test" } },
                      "resources": {
                        "thing:/": { "grant": ["%s"], "revoke": [] }
                      }
                    }
                  }
                }
                """.formatted(SUBJECT_ID, permission));
        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(policy);
        // PolicyEnforcer's public ctor is private; embed(Entry) is the documented factory for
        // wrapping a pre-built Enforcer without going through the import-resolution machinery.
        return PolicyEnforcer.embed(Entry.of(1L, enforcer)).getValueOrThrow();
    }

    private static TimeseriesAdapter recordingAdapter() {
        return new TimeseriesAdapter() {
            @Override
            public CompletionStage<Void> initialize(final TimeseriesAdapterConfig config) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> shutdown() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public HealthStatus getHealth() {
                return HealthStatus.UP;
            }

            @Override
            public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> writeBatch(@Nullable final List<TimeseriesDataPoint> dataPoints) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
        };
    }
}
