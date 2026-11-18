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
package org.eclipse.ditto.things.service.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.junit.Test;
import org.mockito.Mockito;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit tests for {@link RevokedAccessPathCalculator}, run against both policy evaluators.
 */
@RunWith(Parameterized.class)
public final class RevokedAccessPathCalculatorTest {

    private static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto.test", "policy");
    private static final JsonObject THING = JsonObject.of("""
            {
              "thingId": "org.eclipse.ditto.test:thing",
              "policyId": "org.eclipse.ditto.test:policy",
              "attributes": {"pub": "p", "secret": "s", "other": {"a": 1, "b": 2}},
              "features": {"f": {"properties": {"x": 1}}}
            }
            """);

    @Parameterized.Parameter
    public String evaluatorName;

    @Parameterized.Parameter(1)
    public Function<Iterable<PolicyEntry>, Enforcer> evaluator;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> evaluators() {
        return List.of(
                new Object[]{"memory-optimized", (Function<Iterable<PolicyEntry>, Enforcer>)
                        PolicyEnforcers::memoryOptimizedEvaluator},
                new Object[]{"throughput-optimized", (Function<Iterable<PolicyEntry>, Enforcer>)
                        PolicyEnforcers::throughputOptimizedEvaluator});
    }

    @Test
    public void revokedPathsArePerSubjectAndIgnoreGrantsOfOtherSubjects() {
        final Policy policy = policyBuilder()
                .forLabel("ALICE").setSubject(subject("alice"))
                .setGrantedPermissions("thing", "/", "READ")
                .forLabel("GROUP_X").setSubject(subject("group-x"))
                .setRevokedPermissions("thing", "/attributes/secret", "READ")
                .forLabel("BOB").setSubject(subject("bob"))
                .setGrantedPermissions("thing", "/", "READ")
                .setRevokedPermissions("thing", "/attributes/secret", "READ")
                .build();

        final Map<String, List<JsonPointer>> result = calculate(policy);

        assertThat(result).containsOnlyKeys("test:group-x", "test:bob");
        assertThat(result.get("test:group-x")).containsExactly(JsonPointer.of("/attributes/secret"));
        assertThat(result.get("test:bob")).containsExactly(JsonPointer.of("/attributes/secret"));
    }

    @Test
    public void ownMoreSpecificGrantLiftsTheRevoke() {
        final Policy policy = policyBuilder()
                .forLabel("CAROL").setSubject(subject("carol"))
                .setRevokedPermissions("thing", "/attributes", "READ")
                .setGrantedPermissions("thing", "/attributes/pub", "READ")
                .build();

        final Map<String, List<JsonPointer>> result = calculate(policy);

        assertThat(result.get("test:carol"))
                .containsExactlyInAnyOrder(JsonPointer.of("/attributes/secret"), JsonPointer.of("/attributes/other"));
    }

    @Test
    public void fullyRevokedSubtreeIsCollapsed() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_Y").setSubject(subject("group-y"))
                .setRevokedPermissions("thing", "/features", "READ")
                .build();

        assertThat(calculate(policy).get("test:group-y")).containsExactly(JsonPointer.of("/features"));
    }

    @Test
    public void revokeOfThingRootYieldsAllTopLevelFields() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_Z").setSubject(subject("group-z"))
                .setRevokedPermissions("thing", "/", "READ")
                .build();

        assertThat(calculate(policy).get("test:group-z")).containsExactlyInAnyOrder(
                JsonPointer.of("/thingId"), JsonPointer.of("/policyId"),
                JsonPointer.of("/attributes"), JsonPointer.of("/features"));
    }

    @Test
    public void revokeOfAbsentPathYieldsNothing() {
        final Policy policy = policyBuilder()
                .forLabel("DAVE").setSubject(subject("dave"))
                .setRevokedPermissions("thing", "/attributes/missing", "READ")
                .build();

        assertThat(calculate(policy)).isEmpty();
    }

    @Test
    public void revokesOfOtherPermissionsAndResourceTypesAreIgnored() {
        final Policy policy = policyBuilder()
                .forLabel("ALICE").setSubject(subject("alice"))
                .setGrantedPermissions("thing", "/", "READ", "WRITE")
                .setRevokedPermissions("thing", "/attributes/secret", "WRITE")
                .setRevokedPermissions("policy", "/", "READ")
                .build();
        final PolicyEnforcer policyEnforcer = enforcer(policy);

        assertThat(policyEnforcer.getReadRevokedThingResourcePathsBySubject()).isEmpty();
        assertThat(calculate(policy)).isEmpty();
    }

    @Test
    public void ownGrantBelowOwnRevokeLiftsTheRevokeOnlyThere() {
        final Policy policy = policyBuilder()
                .forLabel("ERIN").setSubject(subject("erin"))
                .setGrantedPermissions("thing", "/", "READ")
                .setRevokedPermissions("thing", "/attributes/other", "READ")
                .setGrantedPermissions("thing", "/attributes/other/a", "READ")
                .build();

        assertThat(calculate(policy)).containsOnlyKeys("test:erin");
        assertThat(calculate(policy).get("test:erin")).containsExactly(JsonPointer.of("/attributes/other/b"));
    }

    @Test
    public void deeperGrantOfAnotherSubjectDoesNotLiftTheRevoke() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_X").setSubject(subject("group-x"))
                .setRevokedPermissions("thing", "/attributes/other", "READ")
                .forLabel("ALICE").setSubject(subject("alice"))
                .setGrantedPermissions("thing", "/attributes/other/a", "READ")
                .build();

        final Map<String, List<JsonPointer>> result = calculate(policy);

        assertThat(result).containsOnlyKeys("test:group-x");
        assertThat(result.get("test:group-x")).containsExactly(JsonPointer.of("/attributes/other"));
    }

    @Test
    public void revokesOfEntriesForOtherNamespacesAreIgnored() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_X").setSubject(subject("group-x"))
                .setRevokedPermissions("thing", "/attributes/secret", "READ")
                .build();
        final Policy scoped = policy.toBuilder()
                .setNamespacesFor("GROUP_X", List.of("some.other.namespace"))
                .build();

        assertThat(RevokedAccessPathCalculator.calculateRevokedAccessPaths(THING,
                enforcer(scoped).forNamespace("org.eclipse.ditto.test"), true)).isEmpty();
        assertThat(RevokedAccessPathCalculator.calculateRevokedAccessPaths(THING,
                enforcer(scoped).forNamespace("some.other.namespace"), true)).containsOnlyKeys("test:group-x");
    }

    @Test
    public void leavesAreNotCollapsedOnRequest() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_Y").setSubject(subject("group-y"))
                .setRevokedPermissions("thing", "/attributes/other", "READ")
                .build();

        assertThat(RevokedAccessPathCalculator.calculateRevokedAccessPaths(THING, enforcer(policy), false)
                .get("test:group-y"))
                .containsExactlyInAnyOrder(JsonPointer.of("/attributes/other/a"), JsonPointer.of("/attributes/other/b"));
    }

    @Test
    public void headerValueMarksSubjectsWithRevokedThingRoot() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_Z").setSubject(subject("group-z"))
                .setRevokedPermissions("thing", "/", "READ")
                .setGrantedPermissions("thing", "/attributes/pub", "READ")
                .forLabel("GROUP_X").setSubject(subject("group-x"))
                .setRevokedPermissions("thing", "/attributes/secret", "READ")
                .build();
        final PolicyEnforcer policyEnforcer = enforcer(policy);

        final JsonObject headerValue = RevokedAccessPathCalculator.toHeaderValue(
                RevokedAccessPathCalculator.calculateRevokedAccessPaths(JsonObject.of("{\"attributes\":{\"pub\":1}}"),
                        policyEnforcer, true),
                RevokedAccessPathCalculator.getRootRevokedSubjects(policyEnforcer));

        // nothing of this Thing JSON is revoked for group-z, but it is still listed with its root marker
        assertThat(headerValue).isEqualTo(
                JsonObject.of("{\"subjects\":[\"test:group-z\"],\"paths\":{},\"root\":[0]}"));
    }

    @Test
    public void headerValueHasNoRootMarkerWithoutRootRevokes() {
        final JsonObject headerValue = RevokedAccessPathCalculator.toHeaderValue(
                Map.of("test:a", List.of(JsonPointer.of("/attributes/secret"))), java.util.Set.of());

        assertThat(headerValue.contains("root")).isFalse();
    }

    @Test
    public void doesNotLookUpEffectedSubjectsPerLeaf() {
        final Policy policy = policyBuilder()
                .forLabel("GROUP_Z").setSubject(subject("group-z"))
                .setRevokedPermissions("thing", "/", "READ")
                .build();
        final Enforcer enforcer = Mockito.spy(evaluator.apply(policy));

        RevokedAccessPathCalculator.calculateRevokedAccessPaths(THING, PolicyEnforcer.of(policy, enforcer), true);

        Mockito.verify(enforcer, Mockito.never())
                .getSubjectsWithPermission(Mockito.any(), Mockito.any(org.eclipse.ditto.policies.model.Permissions.class));
    }

    private Map<String, List<JsonPointer>> calculate(final Policy policy) {
        final PolicyEnforcer policyEnforcer = enforcer(policy);
        return RevokedAccessPathCalculator.calculateRevokedAccessPaths(THING, policyEnforcer, true);
    }

    private PolicyEnforcer enforcer(final Policy policy) {
        return PolicyEnforcer.of(policy, evaluator.apply(policy));
    }

    private static PolicyBuilder policyBuilder() {
        return Policy.newBuilder(POLICY_ID);
    }

    private static Subject subject(final String name) {
        return Subject.newInstance("test:" + name, SubjectType.GENERATED);
    }

}
