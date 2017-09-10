/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.PERMISSION_READ;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.PERMISSION_WRITE;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.RESOURCE_TYPE;
import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutablePolicyBuilder}.
 */
public final class ImmutablePolicyBuilderTest {

    private static final String POLICY_NS = "com.test";
    private static final String POLICY_ID = POLICY_NS + ":test";
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

        assertThat(policy)
                .hasId(POLICY_ID)
                .hasNamespace(POLICY_NS);
    }

    @Test
    public void tryToSetNullPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> underTest.setId(null))
                .withMessage("The ID is not valid because it was 'null'!")
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

        final String im3UserSid = "uuid-of-im-user";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, SubjectIssuer.GOOGLE_URL, im3UserSid, SubjectType.JWT)
                .setGrantedPermissionsFor(endUser, "thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, PERMISSION_WRITE)
                .setRevokedPermissionsFor(support, "thing", FEATURES_POINTER, PERMISSION_READ, PERMISSION_WRITE)
                .build();

        final Label endUserLabel = Label.of(endUser);
        final Label supportLabel = Label.of(support);
        final SubjectId im3UserSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, im3UserSid);

        assertThat(policy).hasLabel(endUserLabel);
        assertThat(policy).hasLabel(supportLabel);

        assertThat(policy).hasSubjectFor(endUserLabel, im3UserSubjectId);
        assertThat(policy).hasSubjectTypeFor(endUserLabel, im3UserSubjectId, SubjectType.JWT);

        assertThat(policy).hasResourceFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty());
        assertThat(policy).hasResourceFor(endUserLabel, RESOURCE_TYPE, ATTRIBUTES_POINTER);
        assertThat(policy).hasResourceFor(supportLabel, RESOURCE_TYPE, FEATURES_POINTER);

        assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Arrays.asList(PERMISSION_READ, PERMISSION_WRITE), null));
        assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, RESOURCE_TYPE, ATTRIBUTES_POINTER,
                EffectedPermissions.newInstance(null, Collections.singletonList(PERMISSION_WRITE)));
        assertThat(policy).hasResourceEffectedPermissionsFor(supportLabel, RESOURCE_TYPE, FEATURES_POINTER,
                EffectedPermissions.newInstance(null, TestConstants.Policy.PERMISSIONS_ALL));

        System.out.println(policy.toJsonString());
    }

    @Test
    public void buildPolicyFromScratchWithScopedLabel() {
        final String endUser = "EndUser";
        final String support = "Support";

        final String im3UserSid = "uuid-of-im-user";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .forLabel(endUser)
                .setSubject(SubjectIssuer.GOOGLE_URL, im3UserSid, SubjectType.JWT)
                .setGrantedPermissions("thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                .setRevokedPermissions("thing", ATTRIBUTES_POINTER, PERMISSION_WRITE)
                .forLabel(support)
                .setRevokedPermissions("thing", FEATURES_POINTER, PERMISSION_READ, PERMISSION_WRITE)
                .build();

        final Label endUserLabel = Label.of(endUser);
        final Label supportLabel = Label.of(support);
        final SubjectId im3UserSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, im3UserSid);

        assertThat(policy).hasLabel(endUserLabel);
        assertThat(policy).hasLabel(supportLabel);

        assertThat(policy).hasSubjectFor(endUserLabel, im3UserSubjectId);
        assertThat(policy).hasSubjectTypeFor(endUserLabel, im3UserSubjectId, SubjectType.JWT);

        assertThat(policy).hasResourceFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty());
        assertThat(policy).hasResourceFor(endUserLabel, RESOURCE_TYPE, ATTRIBUTES_POINTER);
        assertThat(policy).hasResourceFor(supportLabel, RESOURCE_TYPE, FEATURES_POINTER);

        assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Arrays.asList(PERMISSION_READ, PERMISSION_WRITE), null));
        assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, RESOURCE_TYPE, ATTRIBUTES_POINTER,
                EffectedPermissions.newInstance(null, Collections.singletonList(PERMISSION_WRITE)));
        assertThat(policy).hasResourceEffectedPermissionsFor(supportLabel, RESOURCE_TYPE, FEATURES_POINTER,
                EffectedPermissions.newInstance(null, TestConstants.Policy.PERMISSIONS_ALL));

        System.out.println(policy.toJsonString());
    }

    @Test
    public void buildPolicyFromExistingPolicy() {
        final String endUser = "EndUser";
        final String support = "Support";

        final String im3UserSid = "uuid-of-im-user";

        final Policy policy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, SubjectIssuer.GOOGLE_URL, im3UserSid, SubjectType.JWT)
                .setGrantedPermissionsFor(endUser, "thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, PERMISSION_WRITE)
                .setRevokedPermissionsFor(support, "thing", FEATURES_POINTER, PERMISSION_READ, PERMISSION_WRITE)
                .build();

        final String newPolicyId = "com.policy:foobar2000";
        final String newEndUser = "NewEndUser";
        final String newIm3UserSid = "new-uuid-of-im-user";

        final Policy newPolicy = ImmutablePolicyBuilder.of(policy).setId(newPolicyId)
                .remove(endUser)
                .forLabel(newEndUser)
                .setSubject(SubjectIssuer.GOOGLE_URL, newIm3UserSid, SubjectType.JWT)
                .setGrantedPermissions("thing", "/", PERMISSION_READ)
                .setRevokedPermissions("thing", "/", PERMISSION_WRITE)
                .build();

        final Label newEndUserLabel = Label.of(newEndUser);
        final Label supportLabel = Label.of(support);
        final SubjectId newIm3UserSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, newIm3UserSid);

        assertThat(newPolicy).hasLabel(newEndUserLabel);
        assertThat(newPolicy).hasLabel(supportLabel);
        assertThat(newPolicy).hasSubjectFor(newEndUserLabel, newIm3UserSubjectId);
        assertThat(newPolicy).hasSubjectTypeFor(newEndUserLabel, newIm3UserSubjectId, SubjectType.JWT);
        assertThat(newPolicy).hasResourceFor(newEndUserLabel, RESOURCE_TYPE, JsonPointer.empty());
        assertThat(newPolicy).hasResourceFor(supportLabel, RESOURCE_TYPE, FEATURES_POINTER);
        assertThat(newPolicy).hasResourceEffectedPermissionsFor(newEndUserLabel, RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Collections.singletonList(PERMISSION_READ),
                        Collections.singletonList(PERMISSION_WRITE)));

        System.out.println(newPolicy.toJsonString());
    }

    @Test
    public void replaceExistingPolicyEntry() {
        final String endUser = "EndUser";
        final Label endUserLabel = Label.of(endUser);

        final String im3UserSid = "uuid-of-im-user";
        final SubjectId im3UserSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, im3UserSid);
        final Subject endUserSubject = Subject.newInstance(im3UserSubjectId, SubjectType.JWT);
        final Subjects endUserSubjects = Subjects.newInstance(endUserSubject);

        final Resource endUserResource = Resource.newInstance(RESOURCE_TYPE, "/",
                EffectedPermissions.newInstance(
                        PoliciesModelFactory.newPermissions(PERMISSION_READ),
                        PoliciesModelFactory.newPermissions(PERMISSION_WRITE)));
        final Resources endUserResources = Resources.newInstance(endUserResource);

        final PolicyEntry policyEntry =
                PoliciesModelFactory.newPolicyEntry(endUserLabel, endUserSubjects, endUserResources);

        final Policy existingPolicy = ImmutablePolicyBuilder.of(POLICY_ID)
                .setSubjectFor(endUser, SubjectIssuer.GOOGLE_URL, im3UserSid, SubjectType.JWT)
                .setGrantedPermissionsFor(endUser, "thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                .setRevokedPermissionsFor(endUser, "thing", ATTRIBUTES_POINTER, PERMISSION_WRITE)
                .build();

        final Policy policy = ImmutablePolicyBuilder.of(existingPolicy).set(policyEntry).build();

        assertThat(policy).hasLabel(endUserLabel);
        assertThat(policy).hasSubjectFor(endUserLabel, im3UserSubjectId);
        assertThat(policy).hasSubjectTypeFor(endUserLabel, im3UserSubjectId, SubjectType.JWT);
        assertThat(policy).hasResourceFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty());
        assertThat(policy).doesNotHaveResourceFor(endUserLabel, RESOURCE_TYPE, ATTRIBUTES_POINTER);

        assertThat(policy).hasResourceEffectedPermissionsFor(endUserLabel, RESOURCE_TYPE, JsonPointer.empty(),
                EffectedPermissions.newInstance(Collections.singleton(PERMISSION_READ),
                        Collections.singleton(PERMISSION_WRITE)));

        System.out.println(policy.toJsonString());
    }

}
