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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
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
    private static final PolicyId POLICY_ID = PolicyId.of("com.example", "myPolicy");

    private static final PolicyImports POLICY_IMPORTS = PoliciesModelFactory.newPolicyImports(
            PoliciesModelFactory.newPolicyImport(PolicyId.of("com.example:importedPolicy")));

    private static Policy createPolicy() {
        final List<PolicyEntry> policyEntries = Arrays.asList(createPolicyEntry1(), createPolicyEntry2());
        return ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, POLICY_IMPORTS, policyEntries);
    }

    private static PolicyEntry createPolicyEntry2() {
        return ImmutablePolicyEntry.of(SUPPORT_LABEL,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "someGroup")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.empty(),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE),
                                        Permissions.none()))),
                true);
    }

    private static PolicyEntry createPolicyEntry1() {
        return ImmutablePolicyEntry.of(END_USER_LABEL,
                Subjects.newInstance(Subject.newInstance(END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1,
                        END_USER_EFFECTED_PERMISSIONS_1)),
                false);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicy.class,
                areImmutable(),
                provided(PolicyId.class, Label.class, PolicyRevision.class, PolicyImports.class, Metadata.class,
                        PolicyEntry.class)
                        .areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicy.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final PolicyEntry policyEntry1 = createPolicyEntry1();
        final PolicyEntry policyEntry2 = createPolicyEntry2();

        final Policy policy = ImmutablePolicy.of(POLICY_ID, null, null, null,
                null, null, null, Arrays.asList(policyEntry1, policyEntry2));

        final JsonObject policyJson = policy.toJson();
        final Policy policy1 = ImmutablePolicy.fromJson(policyJson);

        DittoPolicyAssertions.assertThat(policy1).isEqualTo(policy);
    }

    @Test
    public void testToAndFromJsonWithSpecialFields() {
        final PolicyEntry policyEntry1 = createPolicyEntry1();
        final PolicyEntry policyEntry2 = createPolicyEntry2();

        final Policy policy = ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1),
                null, null, null, null, Arrays.asList(policyEntry1, policyEntry2));

        final JsonObject policyJson = policy.toJson(FieldType.regularOrSpecial());
        final Policy policy1 = ImmutablePolicy.fromJson(policyJson);

        DittoPolicyAssertions.assertThat(policy1).isEqualTo(policy);
    }

    @Test
    public void removeEntryWorks() {
        final Policy policy = createPolicy();
        final Policy policyModified = policy.removeEntry(SUPPORT_LABEL);

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasLabel(SUPPORT_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).doesNotHaveLabel(SUPPORT_LABEL);
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

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, NEW_SUBJECT_1.getId());
        DittoPolicyAssertions.assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, NEW_SUBJECT_2.getId());
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, NEW_SUBJECT_1.getId());
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, NEW_SUBJECT_2.getId());
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void setSubjectForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.setSubjectFor(END_USER_LABEL,
                        Subject.newInstance(END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2));

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_2);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_2);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2);
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void setSubjectForShouldAddNewEntry() {
        final Label label = Label.of("setSubjectForShouldAddNewEntry");
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.setSubjectFor(label, Subject.newInstance(END_USER_SUBJECT_ID_2, END_USER_SUBJECT_TYPE_2));

        DittoPolicyAssertions.assertThat(policy).doesNotHaveLabel(label);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(label);
    }

    @Test
    public void removeSubjectForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified = policy.removeSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).doesNotHaveSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
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

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_1.getPath());
        DittoPolicyAssertions.assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_2.getPath());
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policyModified).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                NEW_RESOURCE_1.getPath());
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
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

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policy).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_2);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_2);
    }

    @Test
    public void removeResourceForShouldCopyExisting() {
        final Policy policy = createPolicy();
        final Policy policyModified =
                policy.removeResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);

        DittoPolicyAssertions.assertThat(policy).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policy).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policy).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policy).hasResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1);
        DittoPolicyAssertions.assertThat(policyModified).hasLabel(END_USER_LABEL);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectFor(END_USER_LABEL, END_USER_SUBJECT_ID_1);
        DittoPolicyAssertions.assertThat(policyModified).hasSubjectTypeFor(END_USER_LABEL, END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1);
        DittoPolicyAssertions.assertThat(policyModified).doesNotHaveResourceFor(END_USER_LABEL, TestConstants.Policy.RESOURCE_TYPE,
                END_USER_RESOURCE_1);
    }

    @Test
    public void getEffectedPermissionsForWorks() {
        final Policy policy = createPolicy();
        final Optional<EffectedPermissions> actualPermissions =
                policy.getEffectedPermissionsFor(END_USER_LABEL, END_USER_SUBJECT_ID_1,
                        TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1.toString());
        DittoPolicyAssertions.assertThat(actualPermissions).contains(END_USER_EFFECTED_PERMISSIONS_1);
    }

    @Test
    public void getEffectedPermissionsForNonExistingShouldBeEmpty() {
        final Policy policy = createPolicy();
        final Optional<EffectedPermissions> actualPermissions =
                policy.getEffectedPermissionsFor(Label.of("foo"),
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, "bar"),
                        ResourceKey.newInstance("thing:/foo/bar"));
        DittoPolicyAssertions.assertThat(actualPermissions).isEmpty();
    }

    @Test
    public void newPolicyIsEmpty() {
        final Policy policy = Policy.newBuilder(POLICY_ID).build();
        DittoPolicyAssertions.assertThat(policy.getEntityId()).contains(POLICY_ID);
        DittoPolicyAssertions.assertThat(policy.isEmpty()).isTrue();
        DittoPolicyAssertions.assertThat(policy.getSize()).isEqualTo(0);
    }

    @Test
    public void newPolicyWithoutID() {
        final Policy policy = Policy.newBuilder().build();
        DittoPolicyAssertions.assertThat(policy.getEntityId()).isEmpty();
        DittoPolicyAssertions.assertThat(policy.isEmpty()).isTrue();
        DittoPolicyAssertions.assertThat(policy.getSize()).isEqualTo(0);
    }

    @Test
    public void removeAllEntriesPolicyIsEmpty() {
        final Policy policy = createPolicy().removeEntry(END_USER_LABEL).removeEntry(SUPPORT_LABEL);
        DittoPolicyAssertions.assertThat(policy.isEmpty()).isTrue();
        DittoPolicyAssertions.assertThat(policy.getSize()).isEqualTo(0);
    }

    @Test
    public void modifyingTheEntrySetDoesNotModifyThePolicy() {
        final Policy policy = ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1),
                null, null, null, null, Collections.singleton(createPolicyEntry1()));

        final PolicyEntry policyEntry = createPolicyEntry2();
        final Set<PolicyEntry> entriesSet = policy.getEntriesSet();
        entriesSet.add(policyEntry);

        DittoPolicyAssertions.assertThat(entriesSet).contains(policyEntry);
        DittoPolicyAssertions.assertThat(policy.getEntriesSet()).doesNotContain(policyEntry);
    }

}
