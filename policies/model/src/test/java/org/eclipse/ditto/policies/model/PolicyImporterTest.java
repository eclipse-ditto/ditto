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
import java.util.HashSet;
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

    private static final Set<AllowedImportAddition> ALLOWED_SUBJECTS =
            Collections.singleton(AllowedImportAddition.SUBJECTS);
    private static final Set<AllowedImportAddition> ALLOWED_RESOURCES =
            Collections.singleton(AllowedImportAddition.RESOURCES);
    private static final Set<AllowedImportAddition> ALLOWED_BOTH;
    static {
        final Set<AllowedImportAddition> both = new HashSet<>();
        both.add(AllowedImportAddition.SUBJECTS);
        both.add(AllowedImportAddition.RESOURCES);
        ALLOWED_BOTH = Collections.unmodifiableSet(both);
    }

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
        return policyEntry(importableType, Collections.emptySet());
    }

    private static PolicyEntry policyEntry(final ImportableType importableType,
            final Set<AllowedImportAddition> allowedImportAdditions) {
        return ImmutablePolicyEntry.of(Label.of(importableType.getName() + SUPPORT_LABEL),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, importableType.getName() + "Group")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))),
                importableType,
                allowedImportAdditions);
    }

    private static PolicyEntry importedPolicyEntry(final PolicyId importedPolicyId, final PolicyEntry entry) {
        return ImmutablePolicyEntry.of(PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel()),
                entry.getSubjects(),
                entry.getResources(),
                entry.getImportableType(),
                entry.getAllowedImportAdditions());
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

    @Test
    public void entriesAdditionsMergesSubjectsAdditively() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Subject additionalSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "additionalUser");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                Subjects.newInstance(additionalSubject), null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy importedPolicyAllowingSubjects =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_SUBJECTS);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingSubjects))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        // Find the imported implicit entry
        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Should contain both original and additional subjects
        final PolicyEntry originalEntry = policyEntry(ImportableType.IMPLICIT, ALLOWED_SUBJECTS);
        assertThat(mergedEntry.getSubjects().getSize())
                .isEqualTo(originalEntry.getSubjects().getSize() + 1);
        assertThat(mergedEntry.getSubjects().getSubject(additionalSubject.getId())).isPresent();
        // Original subjects preserved
        originalEntry.getSubjects().forEach(s ->
                assertThat(mergedEntry.getSubjects().getSubject(s.getId())).isPresent());
    }

    @Test
    public void entriesAdditionsMergesNewResourcePath() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final ResourceKey newResourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("features"));
        final Resource newResource = Resource.newInstance(newResourceKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                        Permissions.none()));
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                null, Resources.newInstance(newResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy importedPolicyAllowingResources =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_RESOURCES);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingResources))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Should contain original resource + new resource
        assertThat(mergedEntry.getResources().getResource(newResourceKey)).isPresent();
        // Original resource preserved
        final PolicyEntry originalEntry = policyEntry(ImportableType.IMPLICIT, ALLOWED_RESOURCES);
        originalEntry.getResources().forEach(r ->
                assertThat(mergedEntry.getResources().getResource(r.getResourceKey())).isPresent());
    }

    @Test
    public void entriesAdditionsMergesOverlappingResourcePermissions() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        // The original entry has: grant=[READ, WRITE], revoke=[]
        // Add an overlapping resource with different permissions
        final ResourceKey overlappingKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("attributes"));
        final Resource overlappingResource = Resource.newInstance(overlappingKey,
                EffectedPermissions.newInstance(
                        Permissions.none(), // no additional grants
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))); // add revoke

        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                null, Resources.newInstance(overlappingResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy importedPolicyAllowingResources =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_RESOURCES);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingResources))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        final Resource mergedResource = mergedEntry.getResources().getResource(overlappingKey)
                .orElseThrow(() -> new AssertionError("Expected merged resource not found"));

        // Grants should be union: READ + WRITE (from template)
        assertThat(mergedResource.getEffectedPermissions().getGrantedPermissions())
                .contains(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);
        // Revokes should include the additional WRITE revoke
        assertThat(mergedResource.getEffectedPermissions().getRevokedPermissions())
                .contains(TestConstants.Policy.PERMISSION_WRITE);
    }

    @Test
    public void templateRevokesArePreserved() {
        // Create a special imported policy with revokes on the template
        final Label entryLabel = Label.of("revokeTestEntry");
        final ResourceKey resourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("attributes"));
        final PolicyEntry templateEntry = ImmutablePolicyEntry.of(entryLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateUser")),
                Resources.newInstance(Resource.newInstance(resourceKey,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                ImportableType.IMPLICIT,
                ALLOWED_RESOURCES);

        final PolicyId specialImportedId = PolicyId.of("com.example", "specialImported");
        final Policy specialImportedPolicy = ImmutablePolicy.of(specialImportedId, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(templateEntry));

        // Addition tries to add a grant but should not remove the template revoke
        final Resource additionalResource = Resource.newInstance(resourceKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                        Permissions.none())); // empty revokes - should NOT remove template's WRITE revoke

        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(entryLabel,
                                null, Resources.newInstance(additionalResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                specialImportedId.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(specialImportedPolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(KNOWN_POLICY_ENTRY_OWN)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(specialImportedId, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(specialImportedId, entryLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        final Resource mergedResource = mergedEntry.getResources().getResource(resourceKey)
                .orElseThrow(() -> new AssertionError("Expected merged resource not found"));

        // Template revoke WRITE must be preserved
        assertThat(mergedResource.getEffectedPermissions().getRevokedPermissions())
                .contains(TestConstants.Policy.PERMISSION_WRITE);
        // Grants should be union: READ (from template) + WRITE (from addition)
        assertThat(mergedResource.getEffectedPermissions().getGrantedPermissions())
                .contains(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);
    }

    @Test
    public void entriesAdditionsForNonExistentLabelIsSilentlyIgnored() {
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(Label.of("nonExistentLabel"),
                                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "extraUser")),
                                null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        // Only IMPLICIT entry + own entry, no crash from nonexistent label
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN,
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.IMPLICIT)));
    }

    @Test
    public void entriesAdditionsForNeverImportableEntryIsSilentlyIgnored() {
        final Label neverLabel = Label.of(ImportableType.NEVER.getName() + "SupportGroup");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(neverLabel,
                                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "extraUser")),
                                null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        // NEVER entry is not imported regardless of additions
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN,
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.IMPLICIT)));
    }

    @Test
    public void entriesAdditionsForExplicitEntryNotInEntriesIsSilentlyIgnored() {
        final Label explicitLabel = Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(explicitLabel,
                                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "extraUser")),
                                null)));

        // Don't include the explicit label in the entries list
        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        // EXPLICIT entry is not imported since it's not in the entries list
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN,
                importedPolicyEntry(IMPORTED_POLICY_ID, policyEntry(ImportableType.IMPLICIT)));
    }

    @Test
    public void emptyEntriesAdditionHasNoEffect() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel, null, null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Should be identical to original entry
        final PolicyEntry originalEntry = policyEntry(ImportableType.IMPLICIT);
        assertThat(mergedEntry.getSubjects()).isEqualTo(originalEntry.getSubjects());
        assertThat(mergedEntry.getResources()).isEqualTo(originalEntry.getResources());
    }

    @Test
    public void partialAdditionSubjectsOnlyWorks() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Subject additionalSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "subjectOnlyUser");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                Subjects.newInstance(additionalSubject), null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy importedPolicyAllowingSubjects =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_SUBJECTS);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingSubjects))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Subjects merged, resources unchanged
        assertThat(mergedEntry.getSubjects().getSubject(additionalSubject.getId())).isPresent();
        assertThat(mergedEntry.getResources())
                .isEqualTo(policyEntry(ImportableType.IMPLICIT, ALLOWED_SUBJECTS).getResources());
    }

    @Test
    public void partialAdditionResourcesOnlyWorks() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final ResourceKey newKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("features"));
        final Resource newResource = Resource.newInstance(newKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                        Permissions.none()));

        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                null, Resources.newInstance(newResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Policy importedPolicyAllowingResources =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_RESOURCES);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingResources))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Resources merged, subjects unchanged
        assertThat(mergedEntry.getResources().getResource(newKey)).isPresent();
        assertThat(mergedEntry.getSubjects())
                .isEqualTo(policyEntry(ImportableType.IMPLICIT, ALLOWED_RESOURCES).getSubjects());
    }

    @Test
    public void subjectAdditionsBlockedWhenNotAllowed() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Subject additionalSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "blockedUser");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                Subjects.newInstance(additionalSubject), null)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        // Use default imported policy (no allowedImportAdditions)
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Subject additions should be silently ignored since not allowed
        assertThat(mergedEntry.getSubjects().getSubject(additionalSubject.getId())).isNotPresent();
        assertThat(mergedEntry.getSubjects().getSize())
                .isEqualTo(policyEntry(ImportableType.IMPLICIT).getSubjects().getSize());
    }

    @Test
    public void resourceAdditionsBlockedWhenNotAllowed() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final ResourceKey newKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("features"));
        final Resource newResource = Resource.newInstance(newKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                        Permissions.none()));

        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                null, Resources.newInstance(newResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        // Use default imported policy (no allowedImportAdditions)
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Resource additions should be silently ignored since not allowed
        assertThat(mergedEntry.getResources().getResource(newKey)).isNotPresent();
        assertThat(mergedEntry.getResources())
                .isEqualTo(policyEntry(ImportableType.IMPLICIT).getResources());
    }

    @Test
    public void subjectsAllowedButResourcesBlockedWhenOnlySubjectsAllowed() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Subject additionalSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "partialUser");
        final ResourceKey newKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("features"));
        final Resource newResource = Resource.newInstance(newKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                        Permissions.none()));

        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                Subjects.newInstance(additionalSubject),
                                Resources.newInstance(newResource))));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        // Template only allows subjects, not resources
        final Policy importedPolicyAllowingSubjects =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_SUBJECTS);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicyAllowingSubjects))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, effectedImports))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));

        // Subject additions should succeed
        assertThat(mergedEntry.getSubjects().getSubject(additionalSubject.getId())).isPresent();
        // Resource additions should be silently ignored
        assertThat(mergedEntry.getResources().getResource(newKey)).isNotPresent();
    }

    @Test
    public void importedEntryPreservesAllowedImportAdditions() {
        final EffectedImports importedLabels = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup")));

        final Policy importedPolicy =
                createImportedPolicyWithAdditions(IMPORTED_POLICY_ID, ALLOWED_BOTH);
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, importedLabels))
                .build();

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        // Verify both implicit and explicit imported entries preserve allowedImportAdditions
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Label importedImplicitLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry implicitEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedImplicitLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected implicit imported entry not found"));
        assertThat(implicitEntry.getAllowedImportAdditions()).isEqualTo(ALLOWED_BOTH);

        final Label explicitLabel = Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup");
        final Label importedExplicitLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, explicitLabel);
        final PolicyEntry explicitEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedExplicitLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected explicit imported entry not found"));
        assertThat(explicitEntry.getAllowedImportAdditions()).isEqualTo(ALLOWED_BOTH);
    }

    private static Policy createImportedPolicy(final PolicyId importedPolicyId) {
        final List<PolicyEntry> policyEntries =
                Arrays.asList(policyEntry(ImportableType.IMPLICIT), policyEntry(ImportableType.EXPLICIT),
                        policyEntry(ImportableType.NEVER));
        return ImmutablePolicy.of(
                importedPolicyId, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, emptyPolicyImports(), policyEntries);
    }

    private static Policy createImportedPolicyWithAdditions(final PolicyId importedPolicyId,
            final Set<AllowedImportAddition> allowedAdditions) {
        final List<PolicyEntry> policyEntries =
                Arrays.asList(policyEntry(ImportableType.IMPLICIT, allowedAdditions),
                        policyEntry(ImportableType.EXPLICIT, allowedAdditions),
                        policyEntry(ImportableType.NEVER, allowedAdditions));
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
