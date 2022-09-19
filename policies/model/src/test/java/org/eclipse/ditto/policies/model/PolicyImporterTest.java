/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link PolicyImporter}.
 */
public final class PolicyImporterTest {

    private static final Label END_USER_LABEL = Label.of("EndUser");
    private static final JsonPointer END_USER_RESOURCE_1 = JsonPointer.of("foo/bar");
    private static final SubjectId END_USER_SUBJECT_ID_1 = SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself");
    private static final SubjectType END_USER_SUBJECT_TYPE_1 = SubjectType.newInstance("endUserSubjectType1");
    private static final EffectedPermissions END_USER_EFFECTED_PERMISSIONS_1 = EffectedPermissions.newInstance(
            Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
            Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE));

    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("com.example", "myImportedPolicy");
    private static final PolicyId POLICY_ID = PolicyId.of("com.example", "myPolicy");

    private static final PolicyEntry KNOWN_POLICY_ENTRY_OWN = ImmutablePolicyEntry.of(END_USER_LABEL,
            Subjects.newInstance(Subject.newInstance(END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1)),
            Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1,
                    END_USER_EFFECTED_PERMISSIONS_1)),
            ImportableType.NEVER);

    private static final Label SUPPORT_LABEL = Label.of("SupportGroup");
    private static final PolicyEntry KNOWN_POLICY_ENTRY_EXPLICIT = newPolicyEntry(ImportableType.EXPLICIT);
    private static final PolicyEntry KNOWN_POLICY_ENTRY_IMPLICIT = newPolicyEntry(ImportableType.IMPLICIT);
    private static final PolicyEntry KNOWN_POLICY_ENTRY_NEVER = newPolicyEntry(ImportableType.NEVER);

    private static final PolicyEntry KNOWN_IMPORTED_POLICY_ENTRY_EXPLICIT = toImportedPolicyEntry(KNOWN_POLICY_ENTRY_EXPLICIT);
    private static final PolicyEntry KNOWN_IMPORTED_POLICY_ENTRY_IMPLICIT = toImportedPolicyEntry(KNOWN_POLICY_ENTRY_IMPLICIT);

    private static PolicyEntry newPolicyEntry(final ImportableType importableType) {
        return ImmutablePolicyEntry.of(Label.of(importableType.getName() + SUPPORT_LABEL),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, importableType.getName() + "Group")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"), EffectedPermissions.newInstance(Permissions.newInstance(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))), importableType);
    }
    private static PolicyEntry toImportedPolicyEntry(final PolicyEntry entry) {
        return ImmutablePolicyEntry.of(PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, entry.getLabel()),
                entry.getSubjects(),
                entry.getResources(),
                entry.getImportableType());
    }

    @Test
    public void importAllPolicyEntries() {

        final Policy importedPolicy = createImportedPolicy();
        final Function<PolicyId, Optional<Policy>> policyLoader = (policyId) -> {
            if (IMPORTED_POLICY_ID.equals(policyId)) {
                return Optional.of(importedPolicy);
            }
            return Optional.empty();
        };

        final Policy policy = createTargetPolicy(Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup"));
        final Set<PolicyEntry> entries = PolicyImporter.mergeImportedPolicyEntries(policy, policyLoader);

        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN, KNOWN_IMPORTED_POLICY_ENTRY_EXPLICIT, KNOWN_IMPORTED_POLICY_ENTRY_IMPLICIT);
    }

    private static Policy createImportedPolicy() {
        final List<PolicyEntry> policyEntries = Arrays.asList(KNOWN_POLICY_ENTRY_EXPLICIT, KNOWN_POLICY_ENTRY_IMPLICIT, KNOWN_POLICY_ENTRY_NEVER);
        return ImmutablePolicy.of(
                IMPORTED_POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, null, policyEntries);
    }

    private static Policy createTargetPolicy(final Label... importedLabels) {
        final List<Label> labels = Arrays.asList(importedLabels);
        final List<PolicyEntry> policyEntries = Collections.singletonList(KNOWN_POLICY_ENTRY_OWN);
        return ImmutablePolicy.of(
                POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null, null,
                PolicyImports.newInstance(
                        PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID,
                                EffectedImports.newInstance(labels))), policyEntries);
    }
}
