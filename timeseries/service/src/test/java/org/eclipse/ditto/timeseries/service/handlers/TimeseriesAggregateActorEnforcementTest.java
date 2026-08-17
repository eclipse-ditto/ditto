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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.TimeseriesAggregationForbiddenException;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseries;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseriesResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link TimeseriesAggregateActor}'s two-stage enforcement.
 *
 * <h2>Stage 1 — the namespace gate</h2>
 * A namespace-wide grant from a namespace-root policy is the entry condition. It bounds the work an
 * unauthorized caller can provoke: no discovery query, no per-Thing policy loads.
 *
 * <h2>Stage 2 — the per-Thing narrowing</h2>
 * Passing the gate proves the root <em>grants</em>; it does not prove no Thing <em>revokes</em>.
 * Namespace-root entries merge additively, and a local revoke beats an injected grant — so a Thing can
 * be unreadable through the single-Thing endpoint while its measurements would still shape an
 * unfiltered aggregate. Every contributing Thing is therefore verified against live policy before any
 * value is aggregated, and the response states how many were excluded so a partial answer cannot pass
 * for a complete one.
 * <p>
 * Nothing about authorization is read from storage: the decision is taken against current policy state
 * on every request, which is why a subject granted access today sees older history and a revoked one
 * immediately loses it.
 */
public final class TimeseriesAggregateActorEnforcementTest {

    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final PolicyId ROOT_POLICY_ID = PolicyId.of(NAMESPACE, "namespace-root");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/circuit/properties/flowTemperature");
    private static final JsonPointer OTHER_PATH =
            JsonPointer.of("/features/circuit/properties/returnTemperature");
    private static final String SUBJECT_ID = "integration:ditto";
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-02T00:00:00Z");

    private static final ThingId THING_A = ThingId.of(NAMESPACE, "heatsource-a");
    private static final ThingId THING_B = ThingId.of(NAMESPACE, "heatsource-b");

    private static ActorSystem actorSystem;
    private static int actorCounter;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("TimeseriesAggregateActorEnforcementTest",
                ConfigFactory.load("test.conf"));
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    // =============================================================================================
    // Stage 1 — namespace gate
    // =============================================================================================

    @Test
    public void namespaceWideGrantIsAuthorized() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.start("gate-granted");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(response.getNamespace()).isEqualTo(NAMESPACE);
            assertThat(f.adapter.invoked).isTrue();
            assertThat(response.getContributingThings()).isEqualTo(2);
            assertThat(response.getExcludedThings()).isZero();
            assertThat(response.isPartial()).isFalse();
        }};
    }

    @Test
    public void grantOnParentPathCoversNestedRequestedPath() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ_TS, "thing:/features", "implicit");
            f.start("gate-parent");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.invoked).isTrue();
        }};
    }

    @Test
    public void missingNamespaceRootPolicyIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.namespacePolicies = DefaultNamespacePoliciesConfig.of(ConfigFactory.empty());
            f.start("gate-no-root");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            // The gate must reject before touching storage — that is what bounds the work an
            // unauthorized caller can provoke.
            assertThat(f.adapter.discoveryInvoked).isFalse();
            assertThat(f.adapter.invoked).isFalse();
        }};
    }

    @Test
    public void rootPolicyWithoutReadTsIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ, "thing:/", "implicit");
            f.start("gate-read-only");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    @Test
    public void plainReadSufficesInSimplifiedMode() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ, "thing:/", "implicit");
            f.thingPolicy = grantingPolicy(Permission.READ, "thing:/", "never");
            f.simplifiedReadPermission = true;
            f.start("gate-simplified");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.invoked).isTrue();
        }};
    }

    @Test
    public void grantCoveringOnlySomePathsIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ_TS, "thing:" + PATH, "implicit");
            f.start("gate-partial-paths");
            f.ask(this, List.of(PATH, OTHER_PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    @Test
    public void revokeBelowGrantedRootIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = PoliciesModelFactory.newPolicy(policyJson(ROOT_POLICY_ID, "DEFAULT",
                    Map.of("thing:/", grant(Permission.READ_TS),
                            "thing:" + PATH, revoke(Permission.READ_TS)),
                    "implicit"));
            f.start("gate-revoked");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
        }};
    }

    @Test
    public void unloadableRootPolicyIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.enforcers = policyId -> CompletableFuture.completedFuture(Optional.empty());
            f.start("gate-unloadable");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
        }};
    }

    @Test
    public void unrelatedSubjectIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.start("gate-other-subject");
            f.actor.tell(command(List.of(PATH), headersFor("integration:someone-else", "other")),
                    getRef());

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    @Test
    public void wildcardNamespacePatternResolves() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.namespacePolicies = namespacePolicies("io.beyonnex.*", ROOT_POLICY_ID);
            f.start("gate-wildcard");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
        }};
    }

    /**
     * Only {@link ImportableType#IMPLICIT} entries are merged into the namespace's Thing policies, so a
     * grant carried by an {@code explicit} or {@code never} entry confers nothing on any Thing and must
     * not pass the gate either.
     */
    @Test
    public void nonImplicitRootEntryDoesNotGrantNamespaceWide() {
        for (final String importable : List.of("never", "explicit")) {
            new TestKit(actorSystem) {{
                final Fixture f = new Fixture();
                f.rootPolicy = grantingPolicy(Permission.READ_TS, "thing:/", importable);
                f.start("gate-importable-" + importable);
                f.ask(this, List.of(PATH));

                expectMsgClass(TimeseriesAggregationForbiddenException.class);
                assertThat(f.adapter.discoveryInvoked).isFalse();
            }};
        }
    }

    /**
     * A policy entry may be scoped to a subset of namespaces, and
     * {@code PolicyImporter.mergeImplicitNamespaceRootEntries} <em>preserves</em> that scope when it
     * injects the entry into a Thing's policy. So an implicit root entry scoped to another namespace
     * confers nothing here, and the gate must not honour it — otherwise this endpoint reads data the
     * single-Thing path denies.
     */
    @Test
    public void rootEntryScopedToAnotherNamespaceDoesNotGrantNamespaceWide() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = scopedGrantingPolicy(List.of("some.other.namespace"));
            f.start("gate-ns-scoped-elsewhere");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    @Test
    public void rootEntryScopedToTheQueriedNamespaceStillGrants() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = scopedGrantingPolicy(List.of(NAMESPACE));
            f.start("gate-ns-scoped-here");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.invoked).isTrue();
        }};
    }

    /**
     * The same scoping has to be honoured at the per-Thing stage, not just at the gate: a Thing whose
     * own policy grants only for another namespace must be excluded rather than trusted.
     */
    @Test
    public void thingPolicyEntryScopedToAnotherNamespaceExcludesTheThing() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.perThingPolicies.put(policyIdOf(THING_B),
                    PoliciesModelFactory.newPolicy(scopedPolicyJson(policyIdOf(THING_B), "owner",
                            Map.of("thing:/", grant(Permission.READ_TS)), "implicit",
                            List.of("some.other.namespace"))));
            f.start("perthing-ns-scoped-elsewhere");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.allowList.get()).containsExactly(Map.entry(PATH, List.of(THING_A)));
        }};
    }

    @Test
    public void implicitEntryAlongsideNonImportableStillGrants() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = PoliciesModelFactory.newPolicy("{"
                    + "\"policyId\":\"" + ROOT_POLICY_ID + "\",\"entries\":{"
                    + "\"LOCAL\":{\"subjects\":{\"" + SUBJECT_ID + "\":{\"type\":\"t\"}},"
                    + "\"resources\":{\"policy:/\":{\"grant\":[\"READ\"],\"revoke\":[]}},"
                    + "\"importable\":\"never\"},"
                    + "\"SHARED\":{\"subjects\":{\"" + SUBJECT_ID + "\":{\"type\":\"t\"}},"
                    + "\"resources\":{\"thing:/\":{\"grant\":[\"" + Permission.READ_TS
                    + "\"],\"revoke\":[]}},"
                    + "\"importable\":\"implicit\"}}}");
            f.start("gate-mixed-importable");
            f.ask(this, List.of(PATH));

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
        }};
    }

    /**
     * A {@code PolicyEnforcer} built via {@code embed(...)} carries no {@link Policy}, so importability
     * cannot be verified — fail closed rather than honour the grant.
     */
    @Test
    public void enforcerWithoutPolicyFailsClosed() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            final PolicyEnforcer withoutPolicy = PolicyEnforcer
                    .embed(Entry.of(1L, PolicyEnforcers.defaultEvaluator(f.rootPolicy)))
                    .getValueOrThrow();
            f.enforcers = policyId -> CompletableFuture.completedFuture(Optional.of(withoutPolicy));
            f.start("gate-no-policy");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
        }};
    }

    @Test
    public void backendWithoutCrossThingSupportIsRejected() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.adapter.capabilities = Capabilities.minimal();
            f.start("gate-no-capability");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesQueryInvalidException.class);
            assertThat(f.adapter.invoked).isFalse();
        }};
    }

    // =============================================================================================
    // Stage 2 — per-Thing narrowing (the data-leak dimension)
    // =============================================================================================

    /**
     * The core regression: a Thing whose own policy revokes the permission must be dropped from the
     * aggregation even though the namespace root grants it.
     */
    @Test
    public void thingRevokingPermissionIsExcludedFromAggregate() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.perThingPolicies.put(policyIdOf(THING_B),
                    PoliciesModelFactory.newPolicy(policyJson(policyIdOf(THING_B), "owner",
                            Map.of("thing:/", revoke(Permission.READ_TS)), "never")));
            f.start("narrow-revoked");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.allowList.get()).containsExactly(Map.entry(PATH, List.of(THING_A)));
            assertThat(response.getContributingThings()).isEqualTo(1);
            assertThat(response.getExcludedThings()).isEqualTo(1);
            assertThat(response.isPartial()).isTrue();
            assertThat(response.getWithheldByPath()).containsExactly(Map.entry(PATH.toString(), 1));
        }};
    }

    @Test
    public void allThingsRevokingYieldsEmptyAllowListNotUnfilteredScan() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            for (final ThingId thingId : List.of(THING_A, THING_B)) {
                f.perThingPolicies.put(policyIdOf(thingId),
                        PoliciesModelFactory.newPolicy(policyJson(policyIdOf(thingId), "owner",
                                Map.of("thing:/", revoke(Permission.READ_TS)), "never")));
            }
            f.start("narrow-all-revoked");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            // A non-null, path-empty allow-list must reach the adapter — null would mean "no filter"
            // and leak the whole namespace.
            assertThat(f.adapter.allowList.get()).isNotNull().isEmpty();
            assertThat(response.getExcludedThings()).isEqualTo(2);
            assertThat(response.isPartial()).isTrue();
            assertThat(response.getWithheldByPath()).containsExactly(Map.entry(PATH.toString(), 2));
        }};
    }

    @Test
    public void thingWithoutPolicyIdIsExcluded() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.thingsWithoutPolicy = Set.of(THING_B);
            f.start("narrow-no-policyid");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.allowList.get()).containsExactly(Map.entry(PATH, List.of(THING_A)));
            assertThat(response.getExcludedThings()).isEqualTo(1);
        }};
    }

    @Test
    public void unresolvableThingIsExcluded() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.unknownThings = Set.of(THING_B);
            f.start("narrow-unresolvable");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.allowList.get()).containsExactly(Map.entry(PATH, List.of(THING_A)));
            assertThat(response.getExcludedThings()).isEqualTo(1);
        }};
    }

    @Test
    public void thingWithoutLoadableEnforcerIsExcluded() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.unloadablePolicies = Set.of(policyIdOf(THING_B));
            f.start("narrow-unloadable-thing");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(f.adapter.allowList.get()).containsExactly(Map.entry(PATH, List.of(THING_A)));
            assertThat(response.getExcludedThings()).isEqualTo(1);
        }};
    }

    /**
     * Verifying a truncated contributor set would silently authorize whatever fell off the end, so
     * exceeding the ceiling fails the request — the same discipline as the group cap.
     */
    @Test
    public void tooManyContributingThingsFailsLoudly() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.maxVerifiedThings = 1;
            f.start("narrow-cap");
            f.ask(this, List.of(PATH));

            expectMsgClass(TimeseriesQueryInvalidException.class);
            assertThat(f.adapter.invoked).isFalse();
        }};
    }

    @Test
    public void contributorsExactlyAtCapAreVerified() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.maxVerifiedThings = 2;
            f.start("narrow-cap-exact");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            assertThat(response.getContributingThings()).isEqualTo(2);
        }};
    }

    @Test
    public void nothingMatchingTheQueryNeverRunsUnfiltered() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.adapter.defaultContributors = List.of();
            f.start("narrow-nothing");
            f.ask(this, List.of(PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            // An earlier version signalled "nothing matched" with an allow-all marker, which made the
            // aggregation run with NO filter — safe only because discovery and the aggregation share
            // the same predicate, and still open to anything ingested between the two round trips.
            // The allow-list must therefore be present and empty, never null.
            assertThat(f.adapter.allowList.get()).isNotNull();
            assertThat(f.adapter.allowList.get()).isEmpty();
            assertThat(response.getExcludedThings()).isZero();
            assertThat(response.isPartial()).isFalse();
        }};
    }

    @Test
    public void verificationHappensBeforeAggregation() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ, "thing:/", "implicit");
            f.adapter.failOnQuery = true;
            f.start("narrow-order");
            f.ask(this, List.of(PATH));

            // If the adapter ran first its failure would surface instead of the 403.
            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.invoked).isFalse();
        }};
    }

    // =============================================================================================
    // Stage 2b — path-granular permission (a Thing readable on one path, withheld from another)
    // =============================================================================================

    /**
     * The regression for path-granular access: {@code READ_TS} can be granted per property, so a Thing
     * denied one requested path must still contribute to the paths it is entitled to. Excluding it from
     * the whole query would silently drop data the caller may read.
     */
    @Test
    public void thingDeniedOnOnePathStillContributesToTheOther() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            // THING_B may read PATH but not OTHER_PATH; THING_A may read both.
            f.perThingPolicies.put(policyIdOf(THING_B),
                    PoliciesModelFactory.newPolicy(policyJson(policyIdOf(THING_B), "owner",
                            Map.of("thing:/", grant(Permission.READ_TS),
                                    "thing:" + OTHER_PATH, revoke(Permission.READ_TS)), "never")));
            f.start("path-granular");
            f.ask(this, List.of(PATH, OTHER_PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            final Map<JsonPointer, Collection<ThingId>> allow = f.adapter.allowList.get();
            assertThat(allow).isNotNull();
            assertThat(allow.get(PATH)).containsExactly(THING_A, THING_B);
            assertThat(allow.get(OTHER_PATH)).containsExactly(THING_A);
            // Nothing is fully excluded — THING_B still contributes to PATH.
            assertThat(response.getExcludedThings()).isZero();
            assertThat(response.isPartial()).isTrue();
            assertThat(response.getWithheldByPath())
                    .containsExactly(Map.entry(OTHER_PATH.toString(), 1));
        }};
    }

    /** A Thing denied on every requested path is fully excluded and counted as such. */
    @Test
    public void thingDeniedOnAllPathsIsFullyExcluded() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.perThingPolicies.put(policyIdOf(THING_B),
                    PoliciesModelFactory.newPolicy(policyJson(policyIdOf(THING_B), "owner",
                            Map.of("thing:/", revoke(Permission.READ_TS)), "never")));
            f.start("path-granular-all");
            f.ask(this, List.of(PATH, OTHER_PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            final Map<JsonPointer, Collection<ThingId>> allow = f.adapter.allowList.get();
            assertThat(allow.get(PATH)).containsExactly(THING_A);
            assertThat(allow.get(OTHER_PATH)).containsExactly(THING_A);
            assertThat(response.getExcludedThings()).isEqualTo(1);
            assertThat(response.getWithheldByPath())
                    .containsOnly(Map.entry(PATH.toString(), 1), Map.entry(OTHER_PATH.toString(), 1));
        }};
    }

    /**
     * A Thing is only counted as withheld from a path it would actually have contributed to — which is
     * why discovery is grouped per path rather than flattened.
     */
    @Test
    public void withheldCountIgnoresPathsTheThingHasNoDataFor() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            // THING_B has data only for PATH; it is denied everything.
            f.adapter.contributors = Map.of(PATH, List.of(THING_A, THING_B),
                    OTHER_PATH, List.of(THING_A));
            f.perThingPolicies.put(policyIdOf(THING_B),
                    PoliciesModelFactory.newPolicy(policyJson(policyIdOf(THING_B), "owner",
                            Map.of("thing:/", revoke(Permission.READ_TS)), "never")));
            f.start("path-granular-nodata");
            f.ask(this, List.of(PATH, OTHER_PATH));

            final RetrieveAggregatedTimeseriesResponse response =
                    expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
            // Only PATH counts it: THING_B would have contributed nothing to OTHER_PATH anyway.
            assertThat(response.getWithheldByPath())
                    .containsExactly(Map.entry(PATH.toString(), 1));
        }};
    }

    // =============================================================================================
    // Fixture
    // =============================================================================================

    /** Assembles the actor with configurable policies, Thing resolution and adapter behaviour. */
    private static final class Fixture {

        private final RecordingAdapter adapter = new RecordingAdapter();
        private final Map<PolicyId, Policy> perThingPolicies = new LinkedHashMap<>();
        private Policy rootPolicy = PoliciesModelFactory.newPolicy(policyJson(ROOT_POLICY_ID,
                "DEFAULT", Map.of("thing:/", "{\"grant\":[\"READ\",\"READ_TS\"],\"revoke\":[]}"),
                "implicit"));
        private Policy thingPolicy = grantingPolicy(Permission.READ_TS, "thing:/", "never");
        private NamespacePoliciesConfig namespacePolicies =
                namespacePolicies(NAMESPACE, ROOT_POLICY_ID);
        private boolean simplifiedReadPermission = false;
        private int maxVerifiedThings = 100;
        private Set<ThingId> unknownThings = Set.of();
        private Set<ThingId> thingsWithoutPolicy = Set.of();
        private Set<PolicyId> unloadablePolicies = Set.of();
        @Nullable private PolicyEnforcerProvider enforcers;
        private ActorRef actor;

        void start(final String name) {
            final int seq = actorCounter++;
            final Set<ThingId> unknown = unknownThings;
            final Set<ThingId> noPolicy = thingsWithoutPolicy;
            final ActorRef things = actorSystem.actorOf(
                    Props.create(FakeThings.class, () -> new FakeThings(unknown, noPolicy)),
                    "things-" + name + "-" + seq);
            final PolicyEnforcerProvider provider = enforcers != null ? enforcers : policyId -> {
                if (unloadablePolicies.contains(policyId)) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                final Policy policy = ROOT_POLICY_ID.equals(policyId)
                        ? rootPolicy
                        : perThingPolicies.getOrDefault(policyId, thingPolicy);
                return CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy)));
            };
            actor = actorSystem.actorOf(TimeseriesAggregateActor.props(adapter, things, provider,
                            namespacePolicies, simplifiedReadPermission, maxVerifiedThings),
                    "aggregate-" + name + "-" + seq);
        }

        void ask(final TestKit kit, final List<JsonPointer> paths) {
            actor.tell(command(paths, headersFor(SUBJECT_ID, "cid")), kit.getRef());
        }
    }

    /** Stands in for the things shard region, answering {@code SudoRetrieveThing}. */
    private static final class FakeThings extends AbstractActor {

        private final Set<ThingId> unknown;
        private final Set<ThingId> withoutPolicy;

        private FakeThings(final Set<ThingId> unknown, final Set<ThingId> withoutPolicy) {
            this.unknown = unknown;
            this.withoutPolicy = withoutPolicy;
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(SudoRetrieveThing.class, sudo -> {
                        final ThingId thingId = ThingId.of(sudo.getEntityId());
                        if (unknown.contains(thingId)) {
                            getSender().tell(ThingNotAccessibleException.newBuilder(thingId)
                                    .dittoHeaders(sudo.getDittoHeaders()).build(), getSelf());
                            return;
                        }
                        final Thing thing = withoutPolicy.contains(thingId)
                                ? ThingsModelFactory.newThingBuilder().setId(thingId).build()
                                : ThingsModelFactory.newThingBuilder().setId(thingId)
                                        .setPolicyId(policyIdOf(thingId)).build();
                        getSender().tell(SudoRetrieveThingResponse.of(thing.toJson(),
                                sudo.getDittoHeaders()), getSelf());
                    })
                    .matchAny(m -> { })
                    .build();
        }
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    /** Convention used by {@link FakeThings}: a Thing's policy id mirrors its Thing id. */
    private static PolicyId policyIdOf(final ThingId thingId) {
        return PolicyId.of(thingId);
    }



    // ---------------------------------------------------------------------------------------------
    // Authorization layer 1: READ on the fields the query filters on / groups by
    // ---------------------------------------------------------------------------------------------

    /**
     * Grouping by a tag discloses the tag's distinct values, so it requires {@code READ} on the Thing
     * field the tag is keyed by — independently of {@code READ_TS} on the data paths. A root policy
     * granting only {@code READ_TS} is therefore not enough to slice by {@code attributes/building}.
     */
    @Test
    public void groupByTagFieldWithoutReadIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ_TS, "thing:/", "implicit");
            f.start("layer1-groupby-denied");
            f.actor.tell(commandWithFields(List.of(PATH),
                    List.of(GroupBy.tag("attributes/building")), null, headersFor(SUBJECT_ID, "cid")), getRef());

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            // Rejected before any backend work is provoked.
            assertThat(f.adapter.discoveryInvoked).isFalse();
            assertThat(f.adapter.invoked).isFalse();
        }};
    }

    @Test
    public void groupByTagFieldWithReadIsAllowed() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.start("layer1-groupby-allowed");
            f.actor.tell(commandWithFields(List.of(PATH),
                    List.of(GroupBy.tag("attributes/building")), null, headersFor(SUBJECT_ID, "cid")), getRef());

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
        }};
    }

    /** Same rule for an RQL filter: filtering by a tag discloses which points carry which value. */
    @Test
    public void filterFieldWithoutReadIsForbidden() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.rootPolicy = grantingPolicy(Permission.READ_TS, "thing:/", "implicit");
            f.start("layer1-filter-denied");
            f.actor.tell(commandWithFields(List.of(PATH), List.of(GroupBy.thingId()),
                    "eq(attributes/building,'A')", headersFor(SUBJECT_ID, "cid")), getRef());

            expectMsgClass(TimeseriesAggregationForbiddenException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    @Test
    public void filterFieldWithReadIsAllowed() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.start("layer1-filter-allowed");
            f.actor.tell(commandWithFields(List.of(PATH), List.of(GroupBy.thingId()),
                    "and(eq(attributes/building,'A'),ge(attributes/floor,2))", headersFor(SUBJECT_ID, "cid")), getRef());

            expectMsgClass(RetrieveAggregatedTimeseriesResponse.class);
        }};
    }

    @Test
    public void unparseableFilterIsRejectedAsInvalid() {
        new TestKit(actorSystem) {{
            final Fixture f = new Fixture();
            f.start("layer1-filter-garbage");
            f.actor.tell(commandWithFields(List.of(PATH), List.of(GroupBy.thingId()),
                    "this is not rql", headersFor(SUBJECT_ID, "cid")), getRef());

            expectMsgClass(TimeseriesQueryInvalidException.class);
            assertThat(f.adapter.discoveryInvoked).isFalse();
        }};
    }

    /** A command whose groupBy and filter reference tag fields, for authorization layer 1. */
    private static RetrieveAggregatedTimeseries commandWithFields(final List<JsonPointer> paths,
            final List<GroupBy> groupBy, @Nullable final String filter, final DittoHeaders headers) {

        final CrossThingTimeseriesQuery query = CrossThingTimeseriesQuery.of(NAMESPACE, paths, FROM,
                TO, Duration.ofHours(1), Aggregation.AVG, groupBy, filter, null, null, null);
        return RetrieveAggregatedTimeseries.of(query, headers);
    }

    private static RetrieveAggregatedTimeseries command(final List<JsonPointer> paths,
            final DittoHeaders headers) {

        final CrossThingTimeseriesQuery query = CrossThingTimeseriesQuery.of(NAMESPACE, paths, FROM,
                TO, Duration.ofHours(1), Aggregation.AVG,
                List.of(GroupBy.thingId()), null, null, null, null);
        return RetrieveAggregatedTimeseries.of(query, headers);
    }

    private static DittoHeaders headersFor(final String subjectId, final String correlationId) {
        return DittoHeaders.newBuilder()
                .correlationId(correlationId + "-" + subjectId)
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(subjectId)))
                .build();
    }

    private static NamespacePoliciesConfig namespacePolicies(final String pattern,
            final PolicyId rootPolicyId) {

        return DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies { \"" + pattern + "\" = [\"" + rootPolicyId + "\"] }"));
    }

    private static String grant(final String permission) {
        return "{\"grant\":[\"" + permission + "\"],\"revoke\":[]}";
    }

    private static String revoke(final String permission) {
        return "{\"grant\":[],\"revoke\":[\"" + permission + "\"]}";
    }

    private static String policyJson(final PolicyId policyId, final String label,
            final Map<String, String> resources, final String importable) {

        final StringBuilder res = new StringBuilder();
        for (final Map.Entry<String, String> entry : resources.entrySet()) {
            if (res.length() > 0) {
                res.append(',');
            }
            res.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        return "{\"policyId\":\"" + policyId + "\",\"entries\":{\"" + label + "\":{"
                + "\"subjects\":{\"" + SUBJECT_ID + "\":{\"type\":\"t\"}},"
                + "\"resources\":{" + res + "},"
                + "\"importable\":\"" + importable + "\"}}}";
    }

    private static Policy grantingPolicy(final String permission, final String resource,
            final String importable) {

        return PoliciesModelFactory.newPolicy(policyJson(ROOT_POLICY_ID, "DEFAULT",
                Map.of(resource, grant(permission)), importable));
    }

    /**
     * Same as {@link #policyJson} but with a per-entry {@code namespaces} scope, which
     * {@code PolicyImporter} preserves when it merges a root entry into a Thing's policy.
     */
    private static String scopedPolicyJson(final PolicyId policyId, final String label,
            final Map<String, String> resources, final String importable,
            final List<String> namespaces) {

        final StringBuilder res = new StringBuilder();
        for (final Map.Entry<String, String> entry : resources.entrySet()) {
            if (res.length() > 0) {
                res.append(',');
            }
            res.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        final StringBuilder ns = new StringBuilder();
        for (final String namespace : namespaces) {
            if (ns.length() > 0) {
                ns.append(',');
            }
            ns.append('"').append(namespace).append('"');
        }
        return "{\"policyId\":\"" + policyId + "\",\"entries\":{\"" + label + "\":{"
                + "\"subjects\":{\"" + SUBJECT_ID + "\":{\"type\":\"t\"}},"
                + "\"resources\":{" + res + "},"
                + "\"namespaces\":[" + ns + "],"
                + "\"importable\":\"" + importable + "\"}}}";
    }

    private static Policy scopedGrantingPolicy(final List<String> namespaces) {
        return PoliciesModelFactory.newPolicy(scopedPolicyJson(ROOT_POLICY_ID, "DEFAULT",
                Map.of("thing:/", grant(Permission.READ_TS)), "implicit", namespaces));
    }

    /** Adapter double recording discovery, the allow-list handed down, and whether the query ran. */
    private static final class RecordingAdapter implements TimeseriesAdapter {

        private volatile boolean invoked = false;
        private volatile boolean discoveryInvoked = false;
        private final AtomicReference<Map<JsonPointer, Collection<ThingId>>> allowList =
                new AtomicReference<>();
        /** path -> Things having data for it. Defaults to both Things on every requested path. */
        private volatile Map<JsonPointer, List<ThingId>> contributors = null;
        private volatile List<ThingId> defaultContributors = List.of(THING_A, THING_B);
        private volatile Capabilities capabilities = Capabilities.builder()
                .supportsNativeQuery(true)
                .supportsNativeCrossThingQuery(true)
                .build();
        private volatile boolean failOnQuery = false;

        @Override
        public Capabilities capabilities() {
            return capabilities;
        }

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
        public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public CompletionStage<Map<JsonPointer, List<ThingId>>> discoverContributors(
                final CrossThingTimeseriesQuery query, final int limit) {

            discoveryInvoked = true;
            if (contributors != null) {
                return CompletableFuture.completedFuture(contributors);
            }
            final Map<JsonPointer, List<ThingId>> perPath = new LinkedHashMap<>();
            for (final JsonPointer path : query.getPaths()) {
                final List<ThingId> all = defaultContributors;
                // Mirror the contract: at most limit + 1 distinct, so an overflow stays detectable.
                perPath.put(path, all.size() > limit + 1 ? all.subList(0, limit + 1) : all);
            }
            return CompletableFuture.completedFuture(perPath);
        }

        @Override
        public CompletionStage<List<AggregatedTimeseriesResult>> queryCrossThing(
                final CrossThingTimeseriesQuery query,
                @Nullable final Map<JsonPointer, Collection<ThingId>> permittedThingsPerPath) {

            invoked = true;
            allowList.set(permittedThingsPerPath);
            if (failOnQuery) {
                return CompletableFuture.failedFuture(new IllegalStateException("must not run"));
            }
            return CompletableFuture.completedFuture(List.of(AggregatedTimeseriesResult.of(
                    Map.of("building", "A"), query.getPaths().get(0),
                    TimeseriesResultMeta.of(0, "cel", "number"), List.of())));
        }
    }
}
