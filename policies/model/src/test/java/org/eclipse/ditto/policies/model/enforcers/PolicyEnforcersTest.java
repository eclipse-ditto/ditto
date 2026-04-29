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
package org.eclipse.ditto.policies.model.enforcers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.Test;

/**
 * Unit tests for {@link PolicyEnforcers}, specifically the optimization that filters out entries with
 * empty subjects or empty resources before building the enforcer.
 */
public final class PolicyEnforcersTest {

    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("ditto:alice");
    private static final AuthorizationSubject BOB = AuthorizationSubject.newInstance("ditto:bob");
    private static final AuthorizationSubject CHARLIE = AuthorizationSubject.newInstance("ditto:charlie");

    private static final ResourceKey THING_ROOT = ResourceKey.newInstance("thing", "/");

    private static AuthorizationContext authContextOf(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }

    @Test
    public void entryWithSubjectsAndResourcesIsIncluded() {
        final PolicyEntry entry = PoliciesModelFactory.newPolicyEntry(Label.of("included"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Collections.singletonList(entry));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isTrue();
    }

    @Test
    public void entryWithSubjectsButNoResourcesIsFiltered() {
        final PolicyEntry subjectsOnly = PoliciesModelFactory.newPolicyEntry(Label.of("subjects-only"),
                Collections.singletonList(Subject.newInstance("ditto:bob", SubjectType.GENERATED)),
                Collections.emptyList());

        final PolicyEntry valid = PoliciesModelFactory.newPolicyEntry(Label.of("valid"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Arrays.asList(subjectsOnly, valid));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isTrue();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(BOB), "READ"))
                .isFalse();
    }

    @Test
    public void entryWithResourcesButNoSubjectsIsFiltered() {
        final PolicyEntry resourcesOnly = PoliciesModelFactory.newPolicyEntry(Label.of("resources-only"),
                Collections.emptyList(),
                Collections.singletonList(Resource.newInstance(
                        PoliciesResourceType.thingResource("/features/secret"),
                        EffectedPermissions.newInstance(
                                new HashSet<>(Arrays.asList("READ", "WRITE")),
                                Collections.emptySet()))));

        final PolicyEntry valid = PoliciesModelFactory.newPolicyEntry(Label.of("valid"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Arrays.asList(resourcesOnly, valid));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isTrue();
        assertThat(enforcer.getSubjectsWithUnrestrictedPermission(
                ResourceKey.newInstance("thing", "/features/secret"), "WRITE"))
                .isEmpty();
    }

    @Test
    public void entryWithNoSubjectsAndNoResourcesIsFiltered() {
        final PolicyEntry empty = PoliciesModelFactory.newPolicyEntry(Label.of("empty"),
                Collections.emptyList(), Collections.emptyList());

        final PolicyEntry valid = PoliciesModelFactory.newPolicyEntry(Label.of("valid"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Arrays.asList(empty, valid));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isTrue();
    }

    @Test
    public void mixOfValidAndEmptyEntriesFiltersCorrectly() {
        final PolicyEntry aliceEntry = PoliciesModelFactory.newPolicyEntry(Label.of("alice"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final PolicyEntry bobNoResources = PoliciesModelFactory.newPolicyEntry(Label.of("bob-no-resources"),
                Collections.singletonList(Subject.newInstance("ditto:bob", SubjectType.GENERATED)),
                Collections.emptyList());

        final PolicyEntry noSubjectsResources = PoliciesModelFactory.newPolicyEntry(Label.of("no-subjects"),
                Collections.emptyList(),
                Collections.singletonList(Resource.newInstance(
                        PoliciesResourceType.messageResource("/inbox"),
                        EffectedPermissions.newInstance(Collections.singleton("WRITE"),
                                Collections.emptySet()))));

        final PolicyEntry charlieEntry = PoliciesModelFactory.newPolicyEntry(Label.of("charlie"),
                Collections.singletonList(Subject.newInstance("ditto:charlie", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("WRITE"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(
                Arrays.asList(aliceEntry, bobNoResources, noSubjectsResources, charlieEntry));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isTrue();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "WRITE"))
                .isFalse();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(BOB), "READ"))
                .isFalse();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(BOB), "WRITE"))
                .isFalse();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(CHARLIE), "WRITE"))
                .isTrue();
        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(CHARLIE), "READ"))
                .isFalse();
        assertThat(enforcer.getSubjectsWithUnrestrictedPermission(
                ResourceKey.newInstance("message", "/inbox"), "WRITE"))
                .isEmpty();
    }

    @Test
    public void entryWithOnlyRevokePermissionsIsNotFiltered() {
        final PolicyEntry grantEntry = PoliciesModelFactory.newPolicyEntry(Label.of("grant"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(
                                new HashSet<>(Arrays.asList("READ", "WRITE")),
                                Collections.emptySet()))));

        final PolicyEntry revokeEntry = PoliciesModelFactory.newPolicyEntry(Label.of("revoke"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.singletonList(Resource.newInstance(
                        PoliciesResourceType.thingResource("/features/secret"),
                        EffectedPermissions.newInstance(Collections.emptySet(),
                                new HashSet<>(Arrays.asList("READ", "WRITE"))))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Arrays.asList(grantEntry, revokeEntry));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isFalse();
        assertThat(enforcer.hasUnrestrictedPermissions(
                ResourceKey.newInstance("thing", "/attributes"), authContextOf(ALICE), "READ"))
                .isTrue();
        assertThat(enforcer.hasUnrestrictedPermissions(
                ResourceKey.newInstance("thing", "/features/secret"), authContextOf(ALICE), "READ"))
                .isFalse();
    }

    @Test
    public void allEntriesFilteredProducesEmptyEnforcer() {
        final PolicyEntry noResources = PoliciesModelFactory.newPolicyEntry(Label.of("no-resources"),
                Collections.singletonList(Subject.newInstance("ditto:alice", SubjectType.GENERATED)),
                Collections.emptyList());

        final PolicyEntry noSubjects = PoliciesModelFactory.newPolicyEntry(Label.of("no-subjects"),
                Collections.emptyList(),
                Collections.singletonList(Resource.newInstance(PoliciesResourceType.thingResource("/"),
                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                Collections.emptySet()))));

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(Arrays.asList(noResources, noSubjects));

        assertThat(enforcer.hasUnrestrictedPermissions(THING_ROOT, authContextOf(ALICE), "READ"))
                .isFalse();
        assertThat(enforcer.getSubjectsWithUnrestrictedPermission(THING_ROOT, "READ"))
                .isEmpty();
    }
}
