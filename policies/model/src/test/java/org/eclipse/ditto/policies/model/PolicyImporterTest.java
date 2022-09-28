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
import static org.eclipse.ditto.policies.model.PoliciesModelFactory.emptyPolicyImports;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    private static final PolicyId IMPORTED_POLICY_ID2 = PolicyId.of("com.example", "myImportedPolicy2");

    private static final PolicyId POLICY_ID = PolicyId.of("com.example", "myPolicy");

    private static final PolicyEntry KNOWN_POLICY_ENTRY_OWN = ImmutablePolicyEntry.of(END_USER_LABEL,
            Subjects.newInstance(Subject.newInstance(END_USER_SUBJECT_ID_1, END_USER_SUBJECT_TYPE_1)),
            Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, END_USER_RESOURCE_1,
                    END_USER_EFFECTED_PERMISSIONS_1)),
            ImportableType.NEVER);

    private static final Label SUPPORT_LABEL = Label.of("SupportGroup");

    public static final Policy IMPORTED_POLICY = createImportedPolicy(IMPORTED_POLICY_ID);
    public static final Policy IMPORTED_POLICY_2 = createImportedPolicy(IMPORTED_POLICY_ID2);

    private static final Function<PolicyId, CompletionStage<Optional<Policy>>> POLICY_LOADER = (policyId) -> {
        if (IMPORTED_POLICY_ID.equals(policyId)) {
            return CompletableFuture.completedFuture(Optional.of(IMPORTED_POLICY));
        } else if (IMPORTED_POLICY_ID2.equals(policyId)) {
            return CompletableFuture.completedFuture(Optional.of(IMPORTED_POLICY_2));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    };

    private static PolicyEntry policyEntry(final ImportableType importableType) {
        return ImmutablePolicyEntry.of(Label.of(importableType.getName() + SUPPORT_LABEL),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, importableType.getName() + "Group")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))),
                importableType);
    }

    private static PolicyEntry importedPolicyEntry(final PolicyId importedPolicyId, final PolicyEntry entry) {
        return ImmutablePolicyEntry.of(PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel()),
                entry.getSubjects(),
                entry.getResources(),
                entry.getImportableType());
    }

    @Test
    public void importImplicitAndExplicitPolicyEntries() {
        final EffectedImports importedLabels = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup")));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, importedLabels))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN,
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.EXPLICIT)),
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.IMPLICIT))
        );
    }

    @Test
    public void importEntriesFromMultiplePolicies() {
        final EffectedImports importedLabels = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup")));

        final Policy policyWithMultipleImports = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, importedLabels))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID2, importedLabels))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyWithMultipleImports, POLICY_LOADER)
                        .toCompletableFuture()
                        .join();

        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN,
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.EXPLICIT)),
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.IMPLICIT)),
                importedPolicyEntry(IMPORTED_POLICY_ID2, policyEntry(ImportableType.EXPLICIT)),
                importedPolicyEntry(IMPORTED_POLICY_ID2, policyEntry(ImportableType.IMPLICIT))
        );
    }

    @Test
    public void policyEntriesNotModifiedWithEmptyImports() {
        final Policy policy = createPolicy();
        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN);
    }

    @Test
    public void policyEntriesNotModifiedIfImportedPolicyIsNotFound() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(
                        PoliciesModelFactory.newPolicyImport(PolicyId.of("eclipse:notfound"), (EffectedImports) null))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN);
    }

    private static Policy createImportedPolicy(final PolicyId importedPolicyId) {
        final List<PolicyEntry> policyEntries =
                Arrays.asList(policyEntry(ImportableType.IMPLICIT), policyEntry(ImportableType.EXPLICIT),
                        policyEntry(ImportableType.NEVER));
        return ImmutablePolicy.of(
                importedPolicyId, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, emptyPolicyImports(), policyEntries);
    }

    private static Policy createPolicy() {
        final List<PolicyEntry> policyEntries = Collections.singletonList(KNOWN_POLICY_ENTRY_OWN);
        return ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null, null,
                emptyPolicyImports(), policyEntries);
    }
}
