/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutablePolicyBuilder}.
 */
public final class ImmutablePolicyBuilderTest {

    private static final String POLICY_NS = "com.test";
    private static final PolicyId POLICY_ID = PolicyId.of(POLICY_NS, "test");
    private static final JsonPointer ATTRIBUTES_POINTER = JsonFactory.newPointer("/attributes");
    private static final JsonPointer FEATURES_POINTER = JsonFactory.newPointer("/features");

    private PolicyBuilder underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutablePolicyBuilder.of(POLICY_ID);
    }

    @Test
    public void createEmptyPolicy() {
        final Policy policy = underTest.build();

        DittoPolicyAssertions.assertThat(policy)
                .hasId(POLICY_ID)
                .hasNamespace(POLICY_NS);
    }

    @Test
    public void tryToSetNullPolicyId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.setId(null))
                .withMessage("The Policy ID must not be null!")
                .withNoCause();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullPolicyEntry() {
        underTest.set(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullPolicyEntries() {
        underTest.setAll(null);
    }

    @Test
    public void buildPolicyFromScratch() {
        final String endUser = "EndUser";
        final String support = "Support";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissionsFor(endUser, "thing", "/", TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissionsFor(support, "thing", FEATURES_POINTER, TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .build();

        final Label endUserLabel = Label.of(endUser);
        final Label supportLabel = Label.of(support);

        DittoPolicyAssertions.assertThat(policy).hasLabel(endUserLabel);
        DittoPolicyAssertions.assertThat(policy).hasLabel(supportLabel);

        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(endUserLabel, TestConstants.Policy.SUBJECT_ID);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(endUserLabel, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE);

        DittoPolicyAssertions.assertThat(policy).hasResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty());
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, ATTRIBUTES_POINTER);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(supportLabel, TestConstants.Policy.RESOURCE_TYPE, FEATURES_POINTER);

        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Arrays.asList(
                        TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE), null));
        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, ATTRIBUTES_POINTER,
                EffectedPermissions.newInstance(null, Collections.singletonList(TestConstants.Policy.PERMISSION_WRITE)));
        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(supportLabel, TestConstants.Policy.RESOURCE_TYPE, FEATURES_POINTER,
                EffectedPermissions.newInstance(null, TestConstants.Policy.PERMISSIONS_ALL));
    }

    @Test
    public void buildPolicyFromScratchWithScopedLabel() {
        final String endUser = "EndUser";
        final String support = "Support";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .forLabel(endUser)
                .setSubject(TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissions("thing", "/", TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissions("thing", ATTRIBUTES_POINTER, TestConstants.Policy.PERMISSION_WRITE)
                .forLabel(support)
                .setRevokedPermissions("thing", FEATURES_POINTER, TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .build();

        final Label endUserLabel = Label.of(endUser);
        final Label supportLabel = Label.of(support);

        DittoPolicyAssertions.assertThat(policy).hasLabel(endUserLabel);
        DittoPolicyAssertions.assertThat(policy).hasLabel(supportLabel);

        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(endUserLabel, TestConstants.Policy.SUBJECT_ID);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(endUserLabel, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE);

        DittoPolicyAssertions.assertThat(policy).hasResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty());
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, ATTRIBUTES_POINTER);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(supportLabel, TestConstants.Policy.RESOURCE_TYPE, FEATURES_POINTER);

        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Arrays.asList(
                        TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE), null));
        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, ATTRIBUTES_POINTER,
                EffectedPermissions.newInstance(null, Collections.singletonList(TestConstants.Policy.PERMISSION_WRITE)));
        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(supportLabel, TestConstants.Policy.RESOURCE_TYPE, FEATURES_POINTER,
                EffectedPermissions.newInstance(null, TestConstants.Policy.PERMISSIONS_ALL));
    }

    @Test
    public void buildPolicyFromExistingPolicy() {
        final String endUser = "EndUser";
        final String support = "Support";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, SubjectIssuer.GOOGLE, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissionsFor(endUser, "thing", "/", TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissionsFor(support, "thing", FEATURES_POINTER, TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .build();

        final PolicyId newPolicyId = PolicyId.of("com.policy", "foobar2000");
        final String newEndUser = "NewEndUser";
        final SubjectId newUserSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "newUserSubjectId");

        final Policy newPolicy = ImmutablePolicyBuilder.of(policy).setId(newPolicyId)
                .remove(endUser)
                .forLabel(newEndUser)
                .setSubject(newUserSubjectId, TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissions("thing", "/", TestConstants.Policy.PERMISSION_READ)
                .setRevokedPermissions("thing", "/", TestConstants.Policy.PERMISSION_WRITE)
                .build();

        final Label newEndUserLabel = Label.of(newEndUser);
        final Label supportLabel = Label.of(support);

        DittoPolicyAssertions.assertThat(newPolicy).hasLabel(newEndUserLabel);
        DittoPolicyAssertions.assertThat(newPolicy).hasLabel(supportLabel);
        DittoPolicyAssertions.assertThat(newPolicy).hasSubjectFor(newEndUserLabel, newUserSubjectId);
        DittoPolicyAssertions.assertThat(newPolicy).hasSubjectTypeFor(newEndUserLabel, newUserSubjectId, TestConstants.Policy.SUBJECT_TYPE);
        DittoPolicyAssertions.assertThat(newPolicy).hasResourceFor(newEndUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty());
        DittoPolicyAssertions.assertThat(newPolicy).hasResourceFor(supportLabel, TestConstants.Policy.RESOURCE_TYPE, FEATURES_POINTER);
        DittoPolicyAssertions.assertThat(newPolicy).hasResourceEffectedPermissionsFor(newEndUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Collections.singletonList(TestConstants.Policy.PERMISSION_READ),
                        Collections.singletonList(TestConstants.Policy.PERMISSION_WRITE)));
    }

    @Test
    public void replaceExistingPolicyEntry() {
        final String endUser = "EndUser";
        final Label endUserLabel = Label.of(endUser);

        final Subject endUserSubject = Subject.newInstance(
                TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE);
        final Subjects endUserSubjects = Subjects.newInstance(endUserSubject);

        final Resource endUserResource = Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, "/",
                EffectedPermissions.newInstance(
                        PoliciesModelFactory.newPermissions(TestConstants.Policy.PERMISSION_READ),
                        PoliciesModelFactory.newPermissions(TestConstants.Policy.PERMISSION_WRITE)));
        final Resources endUserResources = Resources.newInstance(endUserResource);

        final PolicyEntry policyEntry =
                PoliciesModelFactory.newPolicyEntry(endUserLabel, endUserSubjects, endUserResources, ImportableType.NEVER);

        final Policy existingPolicy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, SubjectIssuer.GOOGLE, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE)
                .setGrantedPermissionsFor(endUser, "thing", "/", TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, TestConstants.Policy.PERMISSION_WRITE)
                .build();

        final Policy policy = ImmutablePolicyBuilder.of(existingPolicy).set(policyEntry).build();

        DittoPolicyAssertions.assertThat(policy).hasLabel(endUserLabel);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(endUserLabel, TestConstants.Policy.SUBJECT_ID);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(endUserLabel, TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.SUBJECT_TYPE);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty());
        DittoPolicyAssertions.assertThat(policy).doesNotHaveResourceFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, ATTRIBUTES_POINTER);

        DittoPolicyAssertions.assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Collections.singleton(TestConstants.Policy.PERMISSION_READ),
                        Collections.singleton(TestConstants.Policy.PERMISSION_WRITE)));
    }

    @Test
    public void buildPolicyWithoutPolicyId() {
        final Policy policyWithoutPolicyId = ImmutablePolicyBuilder.newInstance().build();
        DittoPolicyAssertions.assertThat(policyWithoutPolicyId.getEntityId()).isEqualTo(Optional.empty());
    }

}
