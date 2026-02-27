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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;
import org.junit.Test;

/**
 * Unit test for {@link ClassifySubjectsVisitor}.
 * <p>
 * Verifies that the combined single-pass visitor produces the same results as
 * the three individual visitors ({@code CollectPartialGrantedSubjectsVisitor},
 * {@code CollectUnrestrictedSubjectsVisitor}, {@code CollectEffectedSubjectsVisitor}).
 * </p>
 */
public final class ClassifySubjectsVisitorTest {

    private static final Permissions READ = Permissions.newInstance("READ");
    private static final Permissions READ_WRITE = Permissions.newInstance("READ", "WRITE");

    @Test
    public void emptyPolicyReturnsEmptyClassification() {
        final PolicyId policyId = PolicyId.of("ns", "empty");
        final TreeBasedPolicyEnforcer enforcer =
                TreeBasedPolicyEnforcer.createInstance(Policy.newBuilder(policyId).build());

        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");
        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);

        assertThat(classification.getUnrestricted()).isEmpty();
        assertThat(classification.getPartialOnly()).isEmpty();
        assertThat(classification.getEffectedGranted()).isEmpty();
    }

    @Test
    public void singleSubjectFullGrantMatchesIndividualVisitors() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:full");
        final PolicyId policyId = PolicyId.of("ns", "single-full");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("grant-all")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        assertThat(classification.getUnrestricted()).containsExactly(subject);
        assertThat(classification.getPartialOnly()).isEmpty();
        assertThat(classification.getEffectedGranted()).containsExactly(subject);
    }

    @Test
    public void subjectWithRevokedChildIsNotUnrestricted() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:revoked-child");
        final PolicyId policyId = PolicyId.of("ns", "revoked-child");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("grant")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("revoke")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/secret"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        // Has grants at root + below, so partial. But has revoke below -> not unrestricted.
        assertThat(classification.getUnrestricted()).doesNotContain(subject);
        assertThat(classification.getPartialOnly()).contains(subject);
        assertThat(classification.getEffectedGranted()).contains(subject);
    }

    @Test
    public void multipleSubjectsWithMixedPermissions() {
        final AuthorizationSubject fullAccess = AuthorizationSubject.newInstance("test:full");
        final AuthorizationSubject partialAccess = AuthorizationSubject.newInstance("test:partial");
        final AuthorizationSubject noAccess = AuthorizationSubject.newInstance("test:none");

        final PolicyId policyId = PolicyId.of("ns", "multi");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("full-grant")
                .setSubject(fullAccess.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("partial-grant")
                .setSubject(partialAccess.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/location"), READ)
                .forLabel("no-access")
                .setSubject(noAccess.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), Permissions.newInstance("WRITE"))
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        assertThat(classification.getUnrestricted()).containsExactly(fullAccess);
        assertThat(classification.getPartialOnly()).containsExactly(partialAccess);
        assertThat(classification.getEffectedGranted()).contains(fullAccess);
        assertThat(classification.getEffectedGranted()).doesNotContain(partialAccess);
        assertThat(classification.getEffectedGranted()).doesNotContain(noAccess);
    }

    @Test
    public void subjectWithOnlyBelowGrantIsPartialOnly() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:below-only");
        final PolicyId policyId = PolicyId.of("ns", "below-only");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("below-grant")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(
                        PoliciesResourceType.thingResource("/features/sensor/properties/value"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        assertThat(classification.getUnrestricted()).isEmpty();
        assertThat(classification.getPartialOnly()).containsExactly(subject);
        assertThat(classification.getEffectedGranted()).isEmpty();
    }

    @Test
    public void revokedAtRootMeansNotPartialAndNotUnrestricted() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:revoked-root");
        final PolicyId policyId = PolicyId.of("ns", "revoked-root");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("revoke")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setRevokedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        assertThat(classification.getUnrestricted()).isEmpty();
        assertThat(classification.getPartialOnly()).isEmpty();
        assertThat(classification.getEffectedGranted()).isEmpty();
    }

    @Test
    public void nestedResourceQueryMatchesIndividualVisitors() {
        final AuthorizationSubject subjectA = AuthorizationSubject.newInstance("test:a");
        final AuthorizationSubject subjectB = AuthorizationSubject.newInstance("test:b");

        final PolicyId policyId = PolicyId.of("ns", "nested");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("a-full")
                .setSubject(subjectA.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("b-features")
                .setSubject(subjectB.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), READ)
                .setRevokedPermissions(
                        PoliciesResourceType.thingResource("/features/private"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);

        // Query at /features level
        final ResourceKey featuresKey = PoliciesResourceType.thingResource("/features");
        final SubjectClassification classification = enforcer.classifySubjects(featuresKey, READ);
        assertMatchesIndividualVisitors(enforcer, featuresKey, READ, classification);
    }

    @Test
    public void multiplePermissionsReadWriteMatchesIndividualVisitors() {
        final AuthorizationSubject rwSubject = AuthorizationSubject.newInstance("test:rw");
        final AuthorizationSubject rOnlySubject = AuthorizationSubject.newInstance("test:r-only");

        final PolicyId policyId = PolicyId.of("ns", "multi-perm");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("rw-full")
                .setSubject(rwSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ_WRITE)
                .forLabel("r-only")
                .setSubject(rOnlySubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        // Check READ_WRITE: rOnlySubject should NOT appear as it only has READ
        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ_WRITE);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ_WRITE, classification);

        assertThat(classification.getUnrestricted()).containsExactly(rwSubject);
        assertThat(classification.getEffectedGranted()).containsExactly(rwSubject);
    }

    @Test
    public void complexScenarioWithGrantRevokeReGrant() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:complex");

        final PolicyId policyId = PolicyId.of("ns", "complex");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("root-grant")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("attributes-revoke")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes"), READ)
                .forLabel("location-re-grant")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(
                        PoliciesResourceType.thingResource("/attributes/location"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        // Has revoke below root -> not unrestricted, but partial (has grants at/below root)
        assertThat(classification.getUnrestricted()).doesNotContain(subject);
        assertThat(classification.getPartialOnly()).contains(subject);
    }

    @Test
    public void differentResourceTypesAreIsolated() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("test:policy-only");

        final PolicyId policyId = PolicyId.of("ns", "type-isolation");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("policy-grant")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"), READ)
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);

        // Query thing resource -> should be empty
        final ResourceKey thingKey = PoliciesResourceType.thingResource("/");
        final SubjectClassification thingClassification = enforcer.classifySubjects(thingKey, READ);
        assertMatchesIndividualVisitors(enforcer, thingKey, READ, thingClassification);

        assertThat(thingClassification.getUnrestricted()).isEmpty();
        assertThat(thingClassification.getPartialOnly()).isEmpty();
        assertThat(thingClassification.getEffectedGranted()).isEmpty();

        // Query policy resource -> should have the subject
        final ResourceKey policyKey = PoliciesResourceType.policyResource("/");
        final SubjectClassification policyClassification = enforcer.classifySubjects(policyKey, READ);
        assertMatchesIndividualVisitors(enforcer, policyKey, READ, policyClassification);

        assertThat(policyClassification.getUnrestricted()).containsExactly(subject);
    }

    @Test
    public void manySubjectsWithOverlappingPermissions() {
        final AuthorizationSubject s1 = AuthorizationSubject.newInstance("test:s1");
        final AuthorizationSubject s2 = AuthorizationSubject.newInstance("test:s2");
        final AuthorizationSubject s3 = AuthorizationSubject.newInstance("test:s3");
        final AuthorizationSubject s4 = AuthorizationSubject.newInstance("test:s4");

        final PolicyId policyId = PolicyId.of("ns", "many");
        final Policy policy = Policy.newBuilder(policyId)
                // s1: full unrestricted
                .forLabel("s1")
                .setSubject(s1.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                // s2: partial only (grant below)
                .forLabel("s2")
                .setSubject(s2.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(
                        PoliciesResourceType.thingResource("/features/public"), READ)
                // s3: grant at root but revoked below -> partial
                .forLabel("s3-grant")
                .setSubject(s3.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("s3-revoke")
                .setSubject(s3.getId(), SubjectType.GENERATED)
                .setRevokedPermissions(
                        PoliciesResourceType.thingResource("/features/private"), READ)
                // s4: only WRITE, no READ
                .forLabel("s4")
                .setSubject(s4.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        Permissions.newInstance("WRITE"))
                .build();

        final TreeBasedPolicyEnforcer enforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        final ResourceKey resourceKey = PoliciesResourceType.thingResource("/");

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, READ);
        assertMatchesIndividualVisitors(enforcer, resourceKey, READ, classification);

        assertThat(classification.getUnrestricted()).containsExactly(s1);
        assertThat(classification.getPartialOnly()).containsExactlyInAnyOrder(s2, s3);
        assertThat(classification.getEffectedGranted()).containsExactlyInAnyOrder(s1, s3);
    }

    /**
     * Asserts that the classification result matches what the three individual visitor methods return.
     */
    private static void assertMatchesIndividualVisitors(
            final TreeBasedPolicyEnforcer enforcer,
            final ResourceKey resourceKey,
            final Permissions permissions,
            final SubjectClassification classification) {

        final Set<AuthorizationSubject> partial =
                enforcer.getSubjectsWithPartialPermission(resourceKey, permissions);
        final Set<AuthorizationSubject> unrestricted =
                enforcer.getSubjectsWithUnrestrictedPermission(resourceKey, permissions);
        final EffectedSubjects effected = enforcer.getSubjectsWithPermission(resourceKey, permissions);

        // partialOnly = partial - unrestricted
        final Set<AuthorizationSubject> expectedPartialOnly = new HashSet<>(partial);
        expectedPartialOnly.removeAll(unrestricted);

        assertThat(classification.getUnrestricted())
                .as("unrestricted subjects")
                .containsExactlyInAnyOrderElementsOf(unrestricted);
        assertThat(classification.getPartialOnly())
                .as("partialOnly subjects")
                .containsExactlyInAnyOrderElementsOf(expectedPartialOnly);
        assertThat(classification.getEffectedGranted())
                .as("effectedGranted subjects")
                .containsExactlyInAnyOrderElementsOf(effected.getGranted());
    }

}
