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
                entry.getNamespaces().orElse(null),
                entry.getImportableType(),
                entry.getAllowedImportAdditions().orElse(null));
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
        assertThat(implicitEntry.getAllowedImportAdditions()).contains(ALLOWED_BOTH);

        final Label explicitLabel = Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup");
        final Label importedExplicitLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, explicitLabel);
        final PolicyEntry explicitEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedExplicitLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected explicit imported entry not found"));
        assertThat(explicitEntry.getAllowedImportAdditions()).contains(ALLOWED_BOTH);
    }

    @Test
    public void withResolvedImportsPreservesNamespaces() {
        final List<String> namespaces = Arrays.asList("com.acme", "com.acme.*");
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final PolicyEntry scopedImplicitEntry = ImmutablePolicyEntry.of(implicitLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "scopedGroup")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))),
                namespaces,
                ImportableType.IMPLICIT,
                Collections.emptySet());
        final Policy importedPolicy = ImmutablePolicy.of(
                IMPORTED_POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, emptyPolicyImports(), Collections.singletonList(scopedImplicitEntry));
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = policyId ->
                IMPORTED_POLICY_ID.equals(policyId)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(createPolicy())
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Policy resolvedPolicy = policy.withResolvedImports(loader).toCompletableFuture().join();

        final Label importedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry importedEntry = resolvedPolicy.getEntryFor(importedLabel)
                .orElseThrow(() -> new AssertionError("Expected imported entry not found"));
        assertThat(importedEntry.getNamespaces()).contains(namespaces);
    }

    @Test
    public void entriesAdditionsMergesNamespacesAdditively() {
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final List<String> additionalNamespaces = Arrays.asList("org.example", "org.example.*");
        final EntriesAdditions additions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(implicitLabel,
                                null, null, additionalNamespaces)));

        final EffectedImports effectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), additions);

        final Set<AllowedImportAddition> allowedNamespaces =
                Collections.singleton(AllowedImportAddition.NAMESPACES);
        // Create an imported policy where the template entry already has namespaces ["com.acme"]
        final List<String> templateNamespaces = Collections.singletonList("com.acme");
        final PolicyEntry templateEntry = ImmutablePolicyEntry.of(implicitLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "implicitGroup")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))),
                templateNamespaces,
                ImportableType.IMPLICIT,
                allowedNamespaces);
        final Policy importedPolicy = ImmutablePolicy.of(IMPORTED_POLICY_ID, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(templateEntry));

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicy))
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

        // Should contain both template and additional namespaces
        assertThat(mergedEntry.getNamespaces()).isPresent()
                .hasValueSatisfying(ns -> assertThat(ns).contains("com.acme", "org.example", "org.example.*"));
    }

    @Test
    public void transitiveResolutionMergesEntriesFromTransitivePolicy() {
        // Policy C (template) has a "ROLE" entry with implicit importability and allows subject additions
        final PolicyId policyIdC = PolicyId.of("com.example", "templateC");
        final Label roleLabel = Label.of("ROLE");
        final ResourceKey roleResourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("attributes"));
        final Resource roleResource = Resource.newInstance(roleResourceKey,
                EffectedPermissions.newInstance(
                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                TestConstants.Policy.PERMISSION_WRITE),
                        Permissions.none()));
        final Subject templateSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "templateGroup");
        final PolicyEntry roleEntryInC = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(templateSubject),
                Resources.newInstance(roleResource),
                ImportableType.IMPLICIT,
                ALLOWED_SUBJECTS);
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(roleEntryInC));

        // Policy B (intermediate) imports from C and adds a subject to "ROLE" via entriesAdditions
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediateB");
        final Subject additionalSubjectFromB = Subject.newInstance(SubjectIssuer.GOOGLE, "bUser");
        final EntriesAdditions bAdditions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(roleLabel,
                                Subjects.newInstance(additionalSubjectFromB), null)));
        final EffectedImports bEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), bAdditions);
        final PolicyImport bImportOfC = PoliciesModelFactory.newPolicyImport(policyIdC, bEffectedImports);
        // B has no inline entries (empty entries), only an import of C
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(bImportOfC)),
                Collections.emptyList());

        // Policy A imports from B, requesting transitive resolution of C
        final EffectedImports aEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel),
                null,
                Collections.singletonList(policyIdC));
        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(KNOWN_POLICY_ENTRY_OWN)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffectedImports))
                .build();

        // Policy loader returns B and C
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // Find the ROLE entry imported via B (single prefix — transitive resolution does not double-prefix)
        final Label importedRoleLabel = PoliciesModelFactory.newImportedLabel(policyIdB, roleLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedRoleLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected transitively resolved ROLE entry not found"));

        // Should contain BOTH resources from C AND subjects from B's entriesAdditions
        assertThat(mergedEntry.getResources().getResource(roleResourceKey)).isPresent();
        assertThat(mergedEntry.getSubjects().getSubject(templateSubject.getId())).isPresent();
        assertThat(mergedEntry.getSubjects().getSubject(additionalSubjectFromB.getId())).isPresent();
    }

    @Test
    public void transitiveResolutionIgnoredWhenTransitiveImportsIsEmpty() {
        // Same setup as the transitive test, but A's import has NO transitiveImports
        final PolicyId policyIdC = PolicyId.of("com.example", "templateC2");
        final Label roleLabel = Label.of("ROLE");
        final PolicyEntry roleEntryInC = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateGroup")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT,
                ALLOWED_SUBJECTS);
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(roleEntryInC));

        final PolicyId policyIdB = PolicyId.of("com.example", "intermediateB2");
        final EntriesAdditions bAdditions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(roleLabel,
                                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "bUser")),
                                null)));
        final EffectedImports bEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), bAdditions);
        final PolicyImport bImportOfC = PoliciesModelFactory.newPolicyImport(policyIdC, bEffectedImports);
        // B has no inline entries, only an import of C
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(bImportOfC)),
                Collections.emptyList());

        // A imports from B WITHOUT transitiveImports
        final EffectedImports aEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel));
        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(KNOWN_POLICY_ENTRY_OWN)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffectedImports))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // Without transitive resolution, B has no inline entries, so ROLE should NOT appear
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN);
    }

    @Test
    public void transitiveResolutionIgnoresNonexistentTransitiveId() {
        // B has no import of "nonexistent:policy"
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediateB3");
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.emptyList());

        // A imports from B with transitiveImports pointing to a nonexistent policy
        final PolicyId nonexistentId = PolicyId.of("nonexistent", "policy");
        final EffectedImports aEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(),
                null,
                Collections.singletonList(nonexistentId));
        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(KNOWN_POLICY_ENTRY_OWN)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffectedImports))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // No error, result contains only A's own entry
        assertThat(entries).containsExactlyInAnyOrder(KNOWN_POLICY_ENTRY_OWN);
    }

    @Test
    public void transitiveResolutionDoesNotAffectOtherImports() {
        // Policy C (template)
        final PolicyId policyIdC = PolicyId.of("com.example", "templateC4");
        final Label roleLabel = Label.of("ROLE");
        final PolicyEntry roleEntryInC = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateGroup")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT,
                Collections.emptySet());
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(roleEntryInC));

        // Policy B (intermediate) imports from C
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediateB4");
        final PolicyImport bImportOfC = PoliciesModelFactory.newPolicyImport(policyIdC, (EffectedImports) null);
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(bImportOfC)),
                Collections.emptyList());

        // Policy D has inline entries (no transitiveImports needed)
        final PolicyId policyIdD = PolicyId.of("com.example", "directD");
        final Label dLabel = Label.of("DirectEntry");
        final PolicyEntry dEntry = ImmutablePolicyEntry.of(dLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "dUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT,
                Collections.emptySet());
        final Policy policyD = ImmutablePolicy.of(policyIdD, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(dEntry));

        // A imports from B (with transitiveImports for C) AND from D (without transitiveImports)
        final EffectedImports aEffectedImportsForB = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(),
                null,
                Collections.singletonList(policyIdC));
        final EffectedImports aEffectedImportsForD = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList());

        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(KNOWN_POLICY_ENTRY_OWN)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffectedImportsForB))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdD, aEffectedImportsForD))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            } else if (policyIdD.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyD));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // D's entries should be imported normally (implicit entry)
        final Label importedDLabel = PoliciesModelFactory.newImportedLabel(policyIdD, dLabel);
        assertThat(entries.stream().anyMatch(e -> e.getLabel().equals(importedDLabel))).isTrue();

        // B's transitive entries from C should also be resolved (single prefix — no double-prefix)
        final Label importedRoleLabel = PoliciesModelFactory.newImportedLabel(policyIdB, roleLabel);
        assertThat(entries.stream().anyMatch(e -> e.getLabel().equals(importedRoleLabel))).isTrue();

        // A's own entry should be preserved
        assertThat(entries.stream().anyMatch(e -> e.getLabel().equals(END_USER_LABEL))).isTrue();
    }

    @Test
    public void transitiveResolutionWorksAcrossThreeLevels() {
        // 3-level chain: A → B → C → D
        // D has inline entry "ROLE" with resources (the template)
        // C imports D with entriesAdditions adding subject "cUser" (the intermediate — adds subjects)
        // B imports C with transitiveImports: ["D"] (pass-through — enables C's import of D to be resolved)
        // A imports B with transitiveImports: ["C"] (leaf — enables B's import of C to be resolved)
        // Result: A should see ROLE with resources from D + subjects from C's entriesAdditions

        final PolicyId policyIdD = PolicyId.of("com.example", "templateD");
        final PolicyId policyIdC = PolicyId.of("com.example", "intermediateC");
        final PolicyId policyIdB = PolicyId.of("com.example", "intermediateB");
        final Label roleLabel = Label.of("ROLE");

        final Subject templateSubject = Subject.newInstance(SubjectIssuer.GOOGLE, "templateGroup");
        final Subject subjectFromC = Subject.newInstance(SubjectIssuer.GOOGLE, "cUser");
        final ResourceKey roleResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));

        // D: template with inline entry ROLE (resources + allowedImportAdditions)
        final Set<AllowedImportAddition> allowedSubjects =
                Collections.singleton(AllowedImportAddition.SUBJECTS);
        final PolicyEntry roleEntryInD = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(templateSubject),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT,
                allowedSubjects);
        final Policy policyD = ImmutablePolicy.of(policyIdD, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(roleEntryInD));

        // C: imports from D with entriesAdditions adding "cUser" (no transitiveImports — D has inline entries)
        final EntriesAdditions cAdditions = PoliciesModelFactory.newEntriesAdditions(
                Collections.singletonList(
                        PoliciesModelFactory.newEntryAddition(roleLabel,
                                Subjects.newInstance(subjectFromC), null)));
        final EffectedImports cEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), cAdditions);
        final PolicyImport cImportOfD = PoliciesModelFactory.newPolicyImport(policyIdD, cEffectedImports);
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(cImportOfD)),
                Collections.emptyList());

        // B: imports from C with transitiveImports: ["D"] (resolve C's import of D)
        // B is a pass-through — no entriesAdditions of its own
        final EffectedImports bEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), null, Collections.singletonList(policyIdD));
        final PolicyImport bImportOfC = PoliciesModelFactory.newPolicyImport(policyIdC, bEffectedImports);
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(bImportOfC)),
                Collections.emptyList());

        // A: imports from B with transitiveImports: ["C"] (resolve B's import of C)
        final EffectedImports aEffectedImports = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel),
                null,
                Collections.singletonList(policyIdC));
        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffectedImports))
                .build();

        // Policy loader returns all policies
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            } else if (policyIdD.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyD));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // The ROLE entry resolved through 3 levels gets a single prefix from the outer import (B)
        final Label importedRoleLabel = PoliciesModelFactory.newImportedLabel(policyIdB, roleLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedRoleLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected 3-level transitively resolved ROLE entry not found. Available labels: " +
                                entries.stream().map(e -> e.getLabel().toString()).collect(
                                        java.util.stream.Collectors.joining(", "))));

        // Should contain resources from D (template)
        assertThat(mergedEntry.getResources().getResource(roleResourceKey)).isPresent();
        // Should contain the template subject from D
        assertThat(mergedEntry.getSubjects().getSubject(templateSubject.getId())).isPresent();
        // Should contain subject added by C's entriesAdditions
        assertThat(mergedEntry.getSubjects().getSubject(subjectFromC.getId())).isPresent();
    }

    @Test
    public void mutualTransitiveCycleDoesNotCauseInfiniteRecursion() {
        // 3-policy cycle that passes write-time validation (no policy references itself):
        // A imports B with transitiveImports=["C"]  (C != A → valid)
        // B imports C with transitiveImports=["A"]  (A != B → valid)
        // C imports A with transitiveImports=["B"]  (B != C → valid)
        // At resolution time, this creates an infinite cycle that the depth limit must break.
        final PolicyId policyIdA = PolicyId.of("com.example", "cycleA");
        final PolicyId policyIdB = PolicyId.of("com.example", "cycleB");
        final PolicyId policyIdC = PolicyId.of("com.example", "cycleC");
        final Label roleLabel = Label.of("ROLE");

        final PolicyEntry roleEntry = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        // C imports A with transitiveImports=["B"]
        final EffectedImports cEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), null, Collections.singletonList(policyIdB));
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdA, cEffected))),
                Collections.singletonList(roleEntry));

        // B imports C with transitiveImports=["A"]
        final EffectedImports bEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), null, Collections.singletonList(policyIdA));
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdC, bEffected))),
                Collections.emptyList());

        // A imports B with transitiveImports=["C"]
        final EffectedImports aEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), null, Collections.singletonList(policyIdC));
        final Policy policyA = ImmutablePolicy.of(policyIdA, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdB, aEffected))),
                Collections.emptyList());

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdA.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyA));
            } else if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        // Must complete without StackOverflowError — depth limit breaks the cycle
        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // Resolution terminates and produces C's ROLE entry via the chain A → B → (transitive C) → ROLE.
        // The cycle (A→B→C→A) is broken by the visited-set; only the first traversal contributes entries.
        assertThat(entries).isNotEmpty();
        assertThat(entries).hasSize(1);
        assertThat(entries.iterator().next().getLabel().toString()).contains("ROLE");
    }

    @Test
    public void transitiveResolutionWorksWithExplicitImportableType() {
        // C has an EXPLICIT entry "ROLE". B imports C. A imports B with entries=["ROLE"] + transitiveImports=["C"].
        // Without the label prefix fix, the EXPLICIT entry would be silently dropped due to label mismatch.
        final PolicyId policyIdC = PolicyId.of("com.example", "explicitTemplateC");
        final PolicyId policyIdB = PolicyId.of("com.example", "explicitIntermediateB");
        final Label roleLabel = Label.of("ROLE");
        final ResourceKey roleResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));

        // C has EXPLICIT entry "ROLE"
        final PolicyEntry roleEntryInC = ImmutablePolicyEntry.of(roleLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateGroup")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.EXPLICIT);
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(roleEntryInC));

        // B imports C (no entriesAdditions, no transitiveImports — C has inline entries)
        final EffectedImports bEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel));
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdC, bEffected))),
                Collections.emptyList());

        // A imports B with entries=["ROLE"] and transitiveImports=["C"]
        final EffectedImports aEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel), null, Collections.singletonList(policyIdC));
        final Policy policyA = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(policyIdB, aEffected))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) -> {
            if (policyIdB.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyB));
            } else if (policyIdC.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(policyC));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Set<PolicyEntry> entries =
                PolicyImporter.mergeImportedPolicyEntries(policyA, loader).toCompletableFuture().join();

        // The EXPLICIT "ROLE" entry from C should be imported via B with a single prefix
        final Label importedRoleLabel = PoliciesModelFactory.newImportedLabel(policyIdB, roleLabel);
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedRoleLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected EXPLICIT ROLE entry not found. Available labels: " +
                                entries.stream().map(e -> e.getLabel().toString()).collect(
                                        java.util.stream.Collectors.joining(", "))));

        assertThat(mergedEntry.getResources().getResource(roleResourceKey)).isPresent();
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
