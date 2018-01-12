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

import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.FieldType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicy}.
 */
public final class ImmutablePolicyTest {

    private static final Label END_USER_LABEL = Label.of("EndUser");
    private static final JsonPointer END_USER_RESOURCE_1 = JsonPointer.of("foo/bar");
    private static final JsonPointer END_USER_RESOURCE_2 = JsonPointer.of("/attributes");
    private static final SubjectId END_USER_SUBJECT_ID_1 =
            SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself");
    private static final SubjectType END_USER_SUBJECT_TYPE_1 = SubjectType.newInstance("endUserSubjectType1");
    private static final SubjectId END_USER_SUBJECT_ID_2 =
            SubjectId.newInstance(SubjectIssuer.GOOGLE, "others");
    private static final SubjectType END_USER_SUBJECT_TYPE_2 = SubjectType.newInstance("endUserSubjectType2");
    private static final EffectedPermissions END_USER_EFFECTED_PERMISSIONS_1 =
            EffectedPermissions.newInstance(Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                    Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE));
    private static final Label SUPPORT_LABEL = Label.of("SupportGroup");
    private static final String POLICY_ID = "com.example:myPolicy";

    private static Policy createPolicy() {
        final List<PolicyEntry> policyEntries = Arrays.asList(createPolicyEntry1(), createPolicyEntry2());
        return ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null,
                policyEntries);
    }

    private static PolicyEntry createPolicyEntry2() {
        return ImmutablePolicyEntry.of(SUPPORT_LABEL,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "someGroup")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE),
                                        Permissions.none()))));
    }

    private static PolicyEntry createPolicyEntry1() {
        return ImmutablePolicyEntry.of(END_USER_LABEL,
                Subjects.newInstance(Subject.newInstance(END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1,
                        END_USER_EFFECTED_PERMISSIONS_1)));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicy.class,
                areImmutable(),
                provided(Label.class, PolicyRevision.class, PolicyEntry.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicy.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = PolicyIdInvalidException.class)
    public void testInvalidPolicyId() {
        ImmutablePolicy.of("foo bar", PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(0), null,
                Collections.emptySet());
    }

    @Test(expected = PolicyIdInvalidException.class)
    public void testEmptyPolicyId() {
        ImmutablePolicy.of("", PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(0), null, Collections.emptySet());
    }

    @Test
    public void testToAndFromJson() {
        final PolicyEntry policyEntry1 = createPolicyEntry1();
        final PolicyEntry policyEntry2 = createPolicyEntry2();

        final Policy policy = ImmutablePolicy.of(POLICY_ID, null, null, null, Arrays.asList(policyEntry1,
                policyEntry2));

        final JsonObject policyJson = policy.toJson();
        System.out.println(policyJson.toString());
        final Policy policy1 = ImmutablePolicy.fromJson(policyJson);

        assertThat(policy1).isEqualTo(policy);
    }

    @Test
    public void testToAndFromJsonWithSpecialFields() {
        final PolicyEntry policyEntry1 = createPolicyEntry1();
        final PolicyEntry policyEntry2 = createPolicyEntry2();

        final Policy policy = ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null,
                Arrays.asList(policyEntry1, policyEntry2));

        final JsonObject policyJson = policy.toJson(FieldType.regularOrSpecial());
        System.out.println(policyJson.toString());
        final Policy policy1 = ImmutablePolicy.fromJson(policyJson);

        assertThat(policy1).isEqualTo(policy);
    }

    @Test
    public void removeEntryWorks() {
        final Policy policy = createPolicy();
        final Policy policyModified = policy.removeEntry(SUPPORT_LABEL);

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasLabel(SUPPORT_LABEL);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).doesNotHaveLabel(SUPPORT_LABEL);
    }

    @Test
    public void setSubjectsForWorks() {
        final Policy policy = createPolicy();

        final Subject NEW_SUBJECT_1 =
                Subject.newInstance(SubjectIssuer.GOOGLE, "newSubject1");
        final Subject NEW_SUBJECT_2 =
                Subject.newInstance(SubjectIssuer.GOOGLE, "newSubject2");
        final Subjects NEW_SUBJECTS = Subjects.newInstance(NEW_SUBJECT_1, NEW_SUBJECT_2);

        final Policy policyModified = policy.setSubjectsFor(END_USER_LABEL, NEW_SUBJECTS);

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, NEW_SUBJECT_1.getId());
        assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, NEW_SUBJECT_2.getId());
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, NEW_SUBJECT_1.getId());
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, NEW_SUBJECT_2.getId());
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void setSubjectForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.setSubjectFor(END_USER_LABEL,
                        Subject.newInstance(END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2));

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_2);
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_2);
        assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2);
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void setSubjectForShouldAddNewEntry() {
        final Label label = Label.of("setSubjectForShouldAddNewEntry");
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.setSubjectFor(label, Subject.newInstance(END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2));

        assertThat(policy).doesNotHaveLabel(label);
        assertThat(policyModified).hasLabel(label);
    }

    @Test
    public void removeSubjectForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified = policy.removeSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void setResourcesForWorks() {
        final Policy policy = createPolicy();

        final EffectedPermissions DUMMY_PERMISSIONS = END_USER_EFFECTED_PERMISSIONS_1;
        final Resource NEW_RESOURCE_1 = Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, "/attributes",
                DUMMY_PERMISSIONS);
        final Resource NEW_RESOURCE_2 = Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, "/features",
                DUMMY_PERMISSIONS);
        final Resources NEW_RESOURCES = Resources.newInstance(NEW_RESOURCE_1, NEW_RESOURCE_2);

        final Policy policyModified = policy.setResourcesFor(END_USER_LABEL, NEW_RESOURCES);

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_1.getPath());
        assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_2.getPath());
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policyModified).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_1.getPath());
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_2.getPath());
    }

    @Test
    public void setResourceForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified = policy.setResourceFor(END_USER_LABEL,
                Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_2,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))));

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_2);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
        assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_2);
    }

    @Test
    public void removeResourceForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.removeResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);

        assertThat(policy).hasLabel(END_USER_LABEL);
        assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        assertThat(policyModified).hasLabel(END_USER_LABEL);
        assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        assertThat(policyModified).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void getEffectedPermissionsForWorks() {
        final Policy policy = createPolicy();
        final Optional<EffectedPermissions> actualPermissions =
                policy.getEffectedPermissionsFor(END_USER_LABEL, END_USER_SUBJECT_ID_1,
                        TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1.toString());
        assertThat(actualPermissions).contains(END_USER_EFFECTED_PERMISSIONS_1);
    }

    @Test
    public void getEffectedPermissionsForNonExistingShouldBeEmpty() {
        final Policy policy = createPolicy();
        final Optional<EffectedPermissions> actualPermissions =
                policy.getEffectedPermissionsFor(Label.of("foo"),
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, "bar"),
                        ResourceKey.newInstance("thing:/foo/bar"));
        assertThat(actualPermissions).isEmpty();
    }

    @Test
    public void newPolicyIsEmpty() {
        final Policy policy = Policy.newBuilder(POLICY_ID).build();
        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.getSize()).isEqualTo(0);
    }

    @Test
    public void removeAllEntriesPolicyIsEmpty() {
        final Policy policy = createPolicy().removeEntry(END_USER_LABEL).removeEntry(SUPPORT_LABEL);
        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.getSize()).isEqualTo(0);
    }

    @Test
    public void modifyingTheEntrySetDoesNotModifyThePolicy() {
        final Policy policy = ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null,
                Collections.singleton(createPolicyEntry1()));

        final PolicyEntry policyEntry = createPolicyEntry2();
        final Set<PolicyEntry> entriesSet = policy.getEntriesSet();
        entriesSet.add(policyEntry);

        assertThat(entriesSet).contains(policyEntry);
        assertThat(policy.getEntriesSet()).doesNotContain(policyEntry);
    }

}
