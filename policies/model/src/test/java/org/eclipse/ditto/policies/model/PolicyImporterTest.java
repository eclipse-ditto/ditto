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
import java.util.stream.StreamSupport;

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

    private static final Set<AllowedAddition> ALLOWED_BOTH;
    static {
        final Set<AllowedAddition> both = new HashSet<>();
        both.add(AllowedAddition.SUBJECTS);
        both.add(AllowedAddition.RESOURCES);
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
        return policyEntry(importableType, ALLOWED_BOTH);
    }

    private static PolicyEntry policyEntry(final ImportableType importableType,
            final Set<AllowedAddition> allowedAdditions) {
        return ImmutablePolicyEntry.of(Label.of(importableType.getName() + SUPPORT_LABEL),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, importableType.getName() + "Group")),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ,
                                                TestConstants.Policy.PERMISSION_WRITE), Permissions.none()))),
                importableType,
                allowedAdditions);
    }

    private static PolicyEntry importedPolicyEntry(final PolicyId importedPolicyId, final PolicyEntry entry) {
        return ImmutablePolicyEntry.of(PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel()),
                entry.getSubjects(),
                entry.getResources(),
                entry.getNamespaces().orElse(null),
                entry.getImportableType(),
                entry.getAllowedAdditions().orElse(null));
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
    public void importedEntryPreservesAllowedAdditions() {
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

        // Verify both implicit and explicit imported entries preserve allowedAdditions
        final Label implicitLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final Label importedImplicitLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, implicitLabel);
        final PolicyEntry implicitEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedImplicitLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected implicit imported entry not found"));
        assertThat(implicitEntry.getAllowedAdditions()).contains(ALLOWED_BOTH);

        final Label explicitLabel = Label.of(ImportableType.EXPLICIT.getName() + "SupportGroup");
        final Label importedExplicitLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, explicitLabel);
        final PolicyEntry explicitEntry = entries.stream()
                .filter(e -> e.getLabel().equals(importedExplicitLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected explicit imported entry not found"));
        assertThat(explicitEntry.getAllowedAdditions()).contains(ALLOWED_BOTH);
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

        // B's transitive entries from C get double-prefixed: C-prefix inside B-prefix
        final Label cPrefixedRole = PoliciesModelFactory.newImportedLabel(policyIdC, roleLabel);
        final Label bPrefixedCRole = PoliciesModelFactory.newImportedLabel(policyIdB, cPrefixedRole);
        assertThat(entries.stream().anyMatch(e -> e.getLabel().equals(bPrefixedCRole))).isTrue();

        // A's own entry should be preserved
        assertThat(entries.stream().anyMatch(e -> e.getLabel().equals(END_USER_LABEL))).isTrue();
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
                Collections.emptyList(), Collections.singletonList(policyIdB));
        final Policy policyC = ImmutablePolicy.of(policyIdC, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdA, cEffected))),
                Collections.singletonList(roleEntry));

        // B imports C with transitiveImports=["A"]
        final EffectedImports bEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(policyIdA));
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdC, bEffected))),
                Collections.emptyList());

        // A imports B with transitiveImports=["C"]
        final EffectedImports aEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(policyIdC));
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

        // B imports C (no transitiveImports — C has inline entries)
        final EffectedImports bEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(roleLabel));
        final Policy policyB = ImmutablePolicy.of(policyIdB, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null,
                PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(policyIdC, bEffected))),
                Collections.emptyList());

        // A imports B with transitiveImports=["C"]. The entries filter includes the
        // prefixed label since transitive entries carry import prefixes.
        final Label cPrefixedRole = PoliciesModelFactory.newImportedLabel(policyIdC, roleLabel);
        final EffectedImports aEffected = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.singletonList(cPrefixedRole), Collections.singletonList(policyIdC));
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

        // The EXPLICIT "ROLE" entry from C gets double-prefixed: C-prefix inside B-prefix
        final Label expectedLabel = PoliciesModelFactory.newImportedLabel(policyIdB,
                PoliciesModelFactory.newImportedLabel(policyIdC, roleLabel));
        final PolicyEntry mergedEntry = entries.stream()
                .filter(e -> e.getLabel().equals(expectedLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected EXPLICIT ROLE entry not found. Available labels: " +
                                entries.stream().map(e -> e.getLabel().toString()).collect(
                                        java.util.stream.Collectors.joining(", "))));

        assertThat(mergedEntry.getResources().getResource(roleResourceKey)).isPresent();
    }

    @Test
    public void testResolveLocalReferenceInheritsSubjects() {
        final Label sharedLabel = Label.of("shared");
        final Label consumerLabel = Label.of("consumer");

        final SubjectId alice = SubjectId.newInstance(SubjectIssuer.GOOGLE, "alice");
        final SubjectId bob = SubjectId.newInstance(SubjectIssuer.GOOGLE, "bob");
        final SubjectId charlie = SubjectId.newInstance(SubjectIssuer.GOOGLE, "charlie");

        final PolicyEntry sharedEntry = ImmutablePolicyEntry.of(sharedLabel,
                Subjects.newInstance(Subject.newInstance(alice), Subject.newInstance(bob)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(sharedLabel);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(charlie)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(localRef));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(sharedEntry)
                .set(consumerEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Verify alice and bob from shared entry are merged into consumer's subjects
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains(alice.toString(), bob.toString(), charlie.toString());
    }

    @Test
    public void testResolveLocalReferenceInheritsResources() {
        final Label templateLabel = Label.of("template");
        final Label consumerLabel = Label.of("consumer");

        final ResourceKey templateResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));
        final ResourceKey consumerResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("features"));

        final PolicyEntry templateEntry = ImmutablePolicyEntry.of(templateLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(templateLabel);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "consumerUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(localRef));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(templateEntry)
                .set(consumerEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Verify template's resources are merged into consumer's resources
        assertThat(resolvedConsumer.getResources().getResource(templateResourceKey)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(consumerResourceKey)).isPresent();
    }

    @Test
    public void testResolveMixedImportAndLocalReferences() {
        final Label localSharedLabel = Label.of("shared");
        final Label consumerLabel = Label.of("consumer");

        // The imported policy entry that will be resolved via import reference
        final Label importedEntryLabel = Label.of(ImportableType.IMPLICIT.getName() + "SupportGroup");
        final ResourceKey importedResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));

        // Local shared entry with a unique resource
        final ResourceKey localResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("local/stuff"));
        final PolicyEntry localSharedEntry = ImmutablePolicyEntry.of(localSharedLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "localUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("local/stuff"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        // Consumer entry references both an import and a local entry
        final EntryReference importRef = PoliciesModelFactory.newEntryReference(
                IMPORTED_POLICY_ID, importedEntryLabel);
        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(localSharedLabel);
        final ResourceKey consumerResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("features"));

        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "consumerUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Arrays.asList(importRef, localRef));

        // Build policy with an import of IMPORTED_POLICY_ID
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(localSharedEntry)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        // First resolve imports to get the imported entries with prefixed labels
        final Set<PolicyEntry> mergedEntries =
                PolicyImporter.mergeImportedPolicyEntries(policy, POLICY_LOADER).toCompletableFuture().join();

        // Then resolve references
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, mergedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Verify import reference contributed imported entry's resources
        assertThat(resolvedConsumer.getResources().getResource(importedResourceKey)).isPresent();
        // Verify local reference contributed local entry's resources
        assertThat(resolvedConsumer.getResources().getResource(localResourceKey)).isPresent();
        // Verify consumer's own resources are still there
        assertThat(resolvedConsumer.getResources().getResource(consumerResourceKey)).isPresent();

        // Verify local reference also merged subjects (localUser)
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:localUser", "google:consumerUser");
    }

    @Test
    public void testAllowedAdditionsRestrictsOwnResources() {
        // Template: allows only subject additions (NOT resources)
        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), // subjects only, NOT resources
                null);

        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry)
                .build();

        // Consumer: has own WRITE resource + reference to template
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("user-access"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "subject2")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));

        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (IMPORTED_POLICY_ID.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templatePolicy));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();

        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("user-access"))
                .orElseThrow(() -> new AssertionError("user-access entry not found"));

        // Template allows subjects → subject2 should be present
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:subject2");

        // Template allows subjects only, NOT resources → consumer's own WRITE should be stripped
        // Only the template's READ resource should remain
        final ResourceKey rootResourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/"));
        assertThat(resolvedConsumer.getResources().getResource(rootResourceKey)).isPresent();
        final EffectedPermissions effectivePerms =
                resolvedConsumer.getResources().getResource(rootResourceKey).get().getEffectedPermissions();
        assertThat(effectivePerms.getGrantedPermissions())
                .contains(TestConstants.Policy.PERMISSION_READ);
        assertThat(effectivePerms.getGrantedPermissions())
                .doesNotContain(TestConstants.Policy.PERMISSION_WRITE);
    }

    @Test
    public void testAllowedAdditionsRestrictsOwnSubjects() {
        // Template: allows only resource additions (NOT subjects)
        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.RESOURCES), // resources only, NOT subjects
                null);

        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry)
                .build();

        // Consumer: has own subject + reference to template
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "ownUser")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));

        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (IMPORTED_POLICY_ID.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templatePolicy));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();

        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Template allows resources only, NOT subjects → consumer's own subject should be stripped
        // Only template's templateUser should remain
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:templateUser");
        assertThat(subjectIds).doesNotContain("google:ownUser");
    }

    @Test
    public void testMultipleImportReferencesFromDifferentPolicies() {
        final PolicyId templateAId = PolicyId.of("com.example", "templateA");
        final PolicyId templateBId = PolicyId.of("com.example", "templateB");
        final PolicyId importingId = PolicyId.of("com.example", "importer");

        final ResourceKey attrResource = ResourceKey.newInstance("thing", JsonPointer.of("attributes"));
        final ResourceKey featResource = ResourceKey.newInstance("thing", JsonPointer.of("features"));

        // Template A: ATTR_ACCESS entry with thing:/attributes READ
        final PolicyEntry attrEntry = PoliciesModelFactory.newPolicyEntry(Label.of("ATTR_ACCESS"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance("thing", JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance("READ"), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final Policy templateA = PoliciesModelFactory.newPolicyBuilder(templateAId).set(attrEntry).build();

        // Template B: FEAT_ACCESS entry with thing:/features READ
        final PolicyEntry featEntry = PoliciesModelFactory.newPolicyEntry(Label.of("FEAT_ACCESS"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance("thing", JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance("READ"), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final Policy templateB = PoliciesModelFactory.newPolicyBuilder(templateBId).set(featEntry).build();

        // Importing policy: combined-access references both templates
        final PolicyEntry combinedEntry = PoliciesModelFactory.newPolicyEntry(Label.of("combined-access"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "subject2")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Arrays.asList(
                        PoliciesModelFactory.newEntryReference(templateAId, Label.of("ATTR_ACCESS")),
                        PoliciesModelFactory.newEntryReference(templateBId, Label.of("FEAT_ACCESS"))));

        final Policy importingPolicy = PoliciesModelFactory.newPolicyBuilder(importingId)
                .set(combinedEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateAId, (EffectedImports) null))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateBId, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (templateAId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templateA));
            } else if (templateBId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templateB));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy resolved = importingPolicy.withResolvedImports(loader).toCompletableFuture().join();

        final PolicyEntry resolvedCombined = resolved.getEntryFor(Label.of("combined-access"))
                .orElseThrow(() -> new AssertionError("combined-access entry not found"));

        // Both templates' resources should be present
        assertThat(resolvedCombined.getResources().getResource(attrResource)).isPresent();
        assertThat(resolvedCombined.getResources().getResource(featResource)).isPresent();

        // subject2 should be present (allowed by both templates)
        final Set<String> subjectIds = StreamSupport.stream(resolvedCombined.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:subject2");
    }

    @Test
    public void testResolveImportReferenceToImportableNeverIsSkipped() {
        final Label consumerLabel = Label.of("consumer");
        final Label neverLabel = Label.of("neverEntry");

        // Create an imported entry with importableType=NEVER
        final PolicyEntry neverEntry = ImmutablePolicyEntry.of(neverLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "neverUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("restricted"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.NEVER);
        final Policy importedPolicy = ImmutablePolicy.of(IMPORTED_POLICY_ID, PolicyLifecycle.ACTIVE,
                PolicyRevision.newInstance(1), null, null, null, emptyPolicyImports(),
                Collections.singletonList(neverEntry));

        // Consumer references the imported NEVER entry
        final EntryReference importRef = PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, neverLabel);
        final ResourceKey consumerResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("features"));
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "consumerUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(importRef));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        // Resolve imports first to get the imported entry with prefixed label
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = (id) ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(importedPolicy))
                        : CompletableFuture.completedFuture(Optional.empty());
        final Set<PolicyEntry> mergedEntries =
                PolicyImporter.mergeImportedPolicyEntries(policy, loader).toCompletableFuture().join();

        // Resolve references
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, mergedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // The consumer's resources should remain unchanged — NEVER entry was not merged
        assertThat(resolvedConsumer.getResources().getResource(consumerResourceKey)).isPresent();
        final ResourceKey restrictedKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("restricted"));
        assertThat(resolvedConsumer.getResources().getResource(restrictedKey)).isNotPresent();

        // Subjects should only contain the consumer's own subject
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).containsExactly("google:consumerUser");
    }

    @Test
    public void testResolveSelfReferenceIsSkipped() {
        final Label selfLabel = Label.of("A");

        // Entry "A" references itself
        final EntryReference selfRef = PoliciesModelFactory.newLocalEntryReference(selfLabel);
        final ResourceKey resourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));
        final SubjectId alice = SubjectId.newInstance(SubjectIssuer.GOOGLE, "alice");

        final PolicyEntry selfEntry = PoliciesModelFactory.newPolicyEntry(selfLabel,
                Subjects.newInstance(Subject.newInstance(alice)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(selfRef));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(selfEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();

        // Should complete without infinite loop
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        final PolicyEntry resolvedA = result.stream()
                .filter(e -> e.getLabel().equals(selfLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entry A not found"));

        // Entry should still have its own resource and subject (self-reference merges the same data)
        assertThat(resolvedA.getResources().getResource(resourceKey)).isPresent();
        final Set<String> subjectIds = StreamSupport.stream(resolvedA.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains(alice.toString());
    }

    @Test
    public void testResolveMultipleLocalReferences() {
        final Label templateALabel = Label.of("template-a");
        final Label templateBLabel = Label.of("template-b");
        final Label consumerLabel = Label.of("consumer");

        final SubjectId alice = SubjectId.newInstance(SubjectIssuer.GOOGLE, "alice");
        final SubjectId bob = SubjectId.newInstance(SubjectIssuer.GOOGLE, "bob");
        final SubjectId charlie = SubjectId.newInstance(SubjectIssuer.GOOGLE, "charlie");

        final ResourceKey attrResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"));
        final ResourceKey featResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("features"));
        final ResourceKey consumerResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("messages"));

        final PolicyEntry templateA = ImmutablePolicyEntry.of(templateALabel,
                Subjects.newInstance(Subject.newInstance(alice)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        final PolicyEntry templateB = ImmutablePolicyEntry.of(templateBLabel,
                Subjects.newInstance(Subject.newInstance(bob)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        final EntryReference refA = PoliciesModelFactory.newLocalEntryReference(templateALabel);
        final EntryReference refB = PoliciesModelFactory.newLocalEntryReference(templateBLabel);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(charlie)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("messages"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Arrays.asList(refA, refB));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(templateA)
                .set(templateB)
                .set(consumerEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Verify resources from both templates AND the consumer's own are merged
        assertThat(resolvedConsumer.getResources().getResource(attrResource)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(featResource)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(consumerResource)).isPresent();

        // Verify subjects from both templates AND the consumer's own are merged
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains(alice.toString(), bob.toString(), charlie.toString());
    }

    @Test
    public void testResolveLocalReferenceDoesNotInheritReferences() {
        final Label otherLabel = Label.of("other");
        final Label sharedLabel = Label.of("shared");
        final Label consumerLabel = Label.of("consumer");

        final SubjectId otherSubject = SubjectId.newInstance(SubjectIssuer.GOOGLE, "otherUser");
        final SubjectId sharedSubject = SubjectId.newInstance(SubjectIssuer.GOOGLE, "sharedUser");
        final SubjectId consumerSubject = SubjectId.newInstance(SubjectIssuer.GOOGLE, "consumerUser");

        final ResourceKey otherResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("other-stuff"));
        final ResourceKey sharedResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("shared-stuff"));
        final ResourceKey consumerResource = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("consumer-stuff"));

        // "other" entry — will be referenced by "shared" but NOT transitively by "consumer"
        final PolicyEntry otherEntry = ImmutablePolicyEntry.of(otherLabel,
                Subjects.newInstance(Subject.newInstance(otherSubject)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("other-stuff"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        // "shared" entry references "other"
        final EntryReference refOther = PoliciesModelFactory.newLocalEntryReference(otherLabel);
        final PolicyEntry sharedEntry = PoliciesModelFactory.newPolicyEntry(sharedLabel,
                Subjects.newInstance(Subject.newInstance(sharedSubject)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("shared-stuff"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(refOther));

        // "consumer" references "shared" — should get shared's own content but NOT other's
        final EntryReference refShared = PoliciesModelFactory.newLocalEntryReference(sharedLabel);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(consumerSubject)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("consumer-stuff"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(refShared));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(otherEntry)
                .set(sharedEntry)
                .set(consumerEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Consumer should have shared's own resource (shared-stuff) and its own (consumer-stuff)
        assertThat(resolvedConsumer.getResources().getResource(sharedResource)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(consumerResource)).isPresent();

        // Consumer should NOT have other's resource — references are not resolved transitively
        assertThat(resolvedConsumer.getResources().getResource(otherResource)).isNotPresent();

        // Consumer should have shared's own subject and its own, but NOT other's
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains(sharedSubject.toString(), consumerSubject.toString());
        assertThat(subjectIds).doesNotContain(otherSubject.toString());
    }

    @Test
    public void testResolveLocalReferenceMissingEntryIsSkipped() {
        final Label consumerLabel = Label.of("consumer");
        final Label nonExistentLabel = Label.of("doesNotExist");

        final ResourceKey consumerResourceKey = ResourceKey.newInstance(
                TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("features"));

        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(nonExistentLabel);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "consumerUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("features"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null,
                ImportableType.IMPLICIT,
                null,
                Collections.singletonList(localRef));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .build();

        final Set<PolicyEntry> resolvedEntries = policy.getEntriesSet();

        // Should not throw — missing reference is silently skipped
        final Set<PolicyEntry> result = PolicyImporter.resolveReferences(policy, resolvedEntries);

        // Consumer entry should still be present and unchanged
        final PolicyEntry resolvedConsumer = result.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        assertThat(resolvedConsumer.getResources().getResource(consumerResourceKey)).isPresent();
        // Only the consumer's own subject should be present
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).containsExactly("google:consumerUser");
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
            final Set<AllowedAddition> allowedAdditions) {
        final List<PolicyEntry> policyEntries =
                Arrays.asList(policyEntry(ImportableType.IMPLICIT, allowedAdditions),
                        policyEntry(ImportableType.EXPLICIT, allowedAdditions),
                        policyEntry(ImportableType.NEVER, allowedAdditions));
        return ImmutablePolicy.of(
                importedPolicyId, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null,
                null, emptyPolicyImports(), policyEntries);
    }

    /**
     * 3-level hierarchy: template → intermediate → leaf.
     * Template defines resources on "driver" entry.
     * Intermediate imports template and has a "driver" entry with references to template's driver + own subjects.
     * Leaf imports intermediate with transitiveImports to template and references intermediate's driver.
     *
     * Verifies that the leaf's resolved driver entry has:
     * - resources from the template (inherited via intermediate's reference, materialized during import)
     * - subjects from the intermediate (alice, bob)
     * - subjects from the leaf (charlie)
     */
    @Test
    public void testThreeLevelReferenceResolution() {
        final PolicyId templateId = PolicyId.of("com.example", "template");
        final PolicyId intermediateId = PolicyId.of("com.example", "intermediate");
        final PolicyId leafId = PolicyId.of("com.example", "leaf");

        final ResourceKey locationResource = ResourceKey.newInstance("thing", JsonPointer.of("features/location"));
        final ResourceKey fuelResource = ResourceKey.newInstance("thing", JsonPointer.of("features/fuel"));

        // Template: driver entry with resources, no subjects
        final PolicyEntry templateDriver = PoliciesModelFactory.newPolicyEntry(Label.of("driver"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(
                        Resource.newInstance("thing", JsonPointer.of("features/location"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance("READ"), Permissions.none())),
                        Resource.newInstance("thing", JsonPointer.of("features/fuel"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance("READ"), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);

        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(templateId)
                .set(templateDriver)
                .build();

        // Intermediate: imports template, driver entry with subjects + reference to template driver
        final PolicyEntry intermediateDriver = PoliciesModelFactory.newPolicyEntry(Label.of("driver"),
                Subjects.newInstance(
                        Subject.newInstance(SubjectIssuer.GOOGLE, "alice"),
                        Subject.newInstance(SubjectIssuer.GOOGLE, "bob")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS),
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(templateId, Label.of("driver"))));

        final Policy intermediatePolicy = PoliciesModelFactory.newPolicyBuilder(intermediateId)
                .set(intermediateDriver)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateId, (EffectedImports) null))
                .build();

        // Leaf: imports intermediate with transitiveImports to template
        final PolicyEntry leafDriver = PoliciesModelFactory.newPolicyEntry(Label.of("driver"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "charlie")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(intermediateId, Label.of("driver"))));

        final EffectedImports intermediateImport = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(templateId));

        final Policy leafPolicy = PoliciesModelFactory.newPolicyBuilder(leafId)
                .set(leafDriver)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(intermediateId, intermediateImport))
                .build();

        // Policy loader knows all three
        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (templateId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templatePolicy));
            } else if (intermediateId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(intermediatePolicy));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        // Resolve via withResolvedImports (the full pipeline)
        final Policy resolved = leafPolicy.withResolvedImports(loader).toCompletableFuture().join();

        // Find the leaf's resolved driver entry
        final PolicyEntry resolvedDriver = resolved.getEntryFor(Label.of("driver"))
                .orElseThrow(() -> new AssertionError("driver entry not found in resolved policy"));

        // Verify resources from template are present (inherited via intermediate's reference)
        assertThat(resolvedDriver.getResources().getResource(locationResource)).isPresent();
        assertThat(resolvedDriver.getResources().getResource(fuelResource)).isPresent();

        // Import references merge subjects additively — leaf's driver inherits alice+bob
        // from the intermediate's resolved driver, plus its own charlie.
        final Set<String> subjectIds = StreamSupport.stream(resolvedDriver.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:alice", "google:bob", "google:charlie");
    }

    /**
     * Verifies that a policy using only local references (no imports) correctly resolves
     * references via withResolvedImports. This was a bug where the else-branch skipped
     * resolveReferences entirely when imports were empty.
     */
    @Test
    public void testLocalRefOnlyPolicyResolvesViaWithResolvedImports() {
        final Label sharedLabel = Label.of("operators");
        final Label consumerLabel = Label.of("reactor-op");

        final SubjectId alice = SubjectId.newInstance(SubjectIssuer.GOOGLE, "alice");
        final ResourceKey reactorResource = ResourceKey.newInstance("thing", JsonPointer.of("features/reactor"));

        final PolicyEntry sharedEntry = ImmutablePolicyEntry.of(sharedLabel,
                Subjects.newInstance(Subject.newInstance(alice)),
                PoliciesModelFactory.emptyResources(),
                ImportableType.IMPLICIT);

        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance("thing", JsonPointer.of("features/reactor"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance("READ", "WRITE"), Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(sharedLabel)));

        // No imports — only local references
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(sharedEntry)
                .set(consumerEntry)
                .build();

        // Use withResolvedImports (with a no-op loader since there are no imports)
        final Function<PolicyId, CompletionStage<Optional<Policy>>> noOpLoader =
                id -> CompletableFuture.completedFuture(Optional.empty());

        final Policy resolved = policy.withResolvedImports(noOpLoader).toCompletableFuture().join();

        final PolicyEntry resolvedConsumer = resolved.getEntryFor(consumerLabel)
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Verify alice from shared entry is merged into consumer's subjects
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains(alice.toString());

        // Verify consumer's own resources are still there
        assertThat(resolvedConsumer.getResources().getResource(reactorResource)).isPresent();
    }

    /**
     * Verifies that intermediate policy's references are resolved before its entries are
     * imported. Without this, resources inherited via references at the intermediate level
     * would be lost during import (since rewriteLabel strips references).
     */
    @Test
    public void testImportedPolicyReferencesAreResolvedBeforeImport() {
        final PolicyId templateId = PolicyId.of("com.example", "tmpl");
        final PolicyId importingId = PolicyId.of("com.example", "importer");

        final ResourceKey attrResource = ResourceKey.newInstance("thing", JsonPointer.of("attributes"));

        // Template: entry with resources
        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("role"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance("thing", JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance("READ"), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);

        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(templateId)
                .set(templateEntry)
                .build();

        // Importing policy: imports template, has entry referencing template's role
        final PolicyEntry importingEntry = PoliciesModelFactory.newPolicyEntry(Label.of("role"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(templateId, Label.of("role"))));

        final Policy importingPolicy = PoliciesModelFactory.newPolicyBuilder(importingId)
                .set(importingEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateId, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (templateId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templatePolicy));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy resolved = importingPolicy.withResolvedImports(loader).toCompletableFuture().join();

        final PolicyEntry resolvedRole = resolved.getEntryFor(Label.of("role"))
                .orElseThrow(() -> new AssertionError("role entry not found"));

        // Resources from template must be inherited via resolved reference
        assertThat(resolvedRole.getResources().getResource(attrResource)).isPresent();

        // Subject from importing policy's own entry
        final Set<String> subjectIds = StreamSupport.stream(resolvedRole.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:user");
    }

    @Test
    public void testConflictingAllowedAdditionsAcrossMultipleImports() {
        // T1 allows only SUBJECTS, T2 allows only RESOURCES → effective intersection is empty.
        // Consumer's own subjects AND own resources should both be stripped.
        final PolicyId templateAId = PolicyId.of("com.example", "templateConflictA");
        final PolicyId templateBId = PolicyId.of("com.example", "templateConflictB");

        final ResourceKey resA = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/from-A"));
        final ResourceKey resB = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/from-B"));
        final ResourceKey resOwn = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/own"));

        final PolicyEntry entryA = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "fromA")),
                Resources.newInstance(Resource.newInstance(resA,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS),
                null);
        final PolicyEntry entryB = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "fromB")),
                Resources.newInstance(Resource.newInstance(resB,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.RESOURCES),
                null);

        final Policy templateA = PoliciesModelFactory.newPolicyBuilder(templateAId).set(entryA).build();
        final Policy templateB = PoliciesModelFactory.newPolicyBuilder(templateBId).set(entryB).build();

        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "ownSubject")),
                Resources.newInstance(Resource.newInstance(resOwn,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Arrays.asList(
                        PoliciesModelFactory.newEntryReference(templateAId, Label.of("DEFAULT")),
                        PoliciesModelFactory.newEntryReference(templateBId, Label.of("DEFAULT"))));

        final Policy importingPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateAId, (EffectedImports) null))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateBId, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id -> {
            if (templateAId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templateA));
            } else if (templateBId.equals(id)) {
                return CompletableFuture.completedFuture(Optional.of(templateB));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy resolved = importingPolicy.withResolvedImports(loader).toCompletableFuture().join();
        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Both templates' subjects are inherited (templates' content is the base, not an "addition")
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:fromA", "google:fromB");
        // Consumer's own subject is stripped (intersection is empty: SUBJECTS not in B's allowed)
        assertThat(subjectIds).doesNotContain("google:ownSubject");

        // Both templates' resources are inherited
        assertThat(resolvedConsumer.getResources().getResource(resA)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(resB)).isPresent();
        // Consumer's own resource is stripped (intersection is empty: RESOURCES not in A's allowed)
        assertThat(resolvedConsumer.getResources().getResource(resOwn)).isNotPresent();
    }

    @Test
    public void testEmptyAllowedAdditionsStripsAllOwnAdditions() {
        // Template explicitly declares allowedAdditions=[] (empty set).
        // This must behave the same as omitting the field — consumer's own additions are stripped.
        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateSubject")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/template"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.<AllowedAddition>emptySet(),
                null);

        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry)
                .build();

        final ResourceKey ownKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/own"));
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "ownSubject")),
                Resources.newInstance(Resource.newInstance(ownKey,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));

        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(templatePolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();
        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Consumer's own additions stripped; only template content remains.
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).containsExactly("google:templateSubject");
        assertThat(resolvedConsumer.getResources().getResource(ownKey)).isNotPresent();
    }

    @Test
    public void testSubjectOverlapBetweenConsumerAndTemplateTemplateWins() {
        // When the template and the consumer both declare a subject with the same ID but different
        // expiry/announcement, mergeSubjects de-duplicates by ID with the template's instance winning
        // (template subjects are added first, so the consumer's variant is dropped).
        final SubjectId sharedId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "shared");
        final SubjectExpiry templateExpiry = SubjectExpiry.newInstance("2099-01-01T00:00:00Z");

        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(sharedId,
                        SubjectType.GENERATED, templateExpiry)),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS),
                null);
        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry).build();

        // Consumer declares the SAME subject ID but no expiry.
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(sharedId, SubjectType.GENERATED)),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));

        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(templatePolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();
        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Exactly one subject with the shared ID, and it carries the template's expiry.
        final List<Subject> subjectsWithSharedId = StreamSupport
                .stream(resolvedConsumer.getSubjects().spliterator(), false)
                .filter(s -> s.getId().equals(sharedId))
                .collect(java.util.stream.Collectors.toList());
        assertThat(subjectsWithSharedId).hasSize(1);
        assertThat(subjectsWithSharedId.get(0).getExpiry()).contains(templateExpiry);
    }

    @Test
    public void testMutualLocalReferenceCycleDoesNotInfiniteLoop() {
        // Two entries reference each other locally. Resolution must terminate, dedup subjects,
        // and produce a stable result on a second invocation.
        final Label labelA = Label.of("A");
        final Label labelB = Label.of("B");

        final PolicyEntry entryA = PoliciesModelFactory.newPolicyEntry(labelA,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "alice")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/a"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(labelB)));

        final PolicyEntry entryB = PoliciesModelFactory.newPolicyEntry(labelB,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "bob")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/b"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(labelA)));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(entryA).set(entryB).build();

        // Must terminate.
        final Set<PolicyEntry> resolved =
                PolicyImporter.resolveReferences(policy, policy.getEntriesSet());

        // Each entry sees the other's subject (one resolution pass; not transitive).
        final PolicyEntry resolvedA = resolved.stream()
                .filter(e -> e.getLabel().equals(labelA))
                .findFirst()
                .orElseThrow(() -> new AssertionError("A not resolved"));
        final PolicyEntry resolvedB = resolved.stream()
                .filter(e -> e.getLabel().equals(labelB))
                .findFirst()
                .orElseThrow(() -> new AssertionError("B not resolved"));

        final Set<String> subjectsOfA = StreamSupport.stream(resolvedA.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        final Set<String> subjectsOfB = StreamSupport.stream(resolvedB.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectsOfA).contains("google:alice", "google:bob");
        assertThat(subjectsOfB).contains("google:alice", "google:bob");
    }

    @Test
    public void testOnMissingReferenceCallbackFires() {
        // Local reference to a non-existent entry — the resolver silently skips it,
        // but the onMissingReference callback should be invoked once.
        final Label consumerLabel = Label.of("consumer");
        final Label phantomLabel = Label.of("phantom");

        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "alice")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(phantomLabel)));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry).build();

        final List<EntryReference> missing = new java.util.ArrayList<>();
        PolicyImporter.resolveReferences(policy, policy.getEntriesSet(),
                (entry, ref) -> missing.add(ref));

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).getEntryLabel()).isEqualTo(phantomLabel);
        assertThat(missing.get(0).isLocalReference()).isTrue();
    }

    @Test
    public void testAbsentAllowedAdditionsImposesNoRestriction() {
        // A template with no allowedAdditions field MUST keep the consumer's own additions.
        // This is the upgrade-friendly default — every pre-existing template lacks this field.
        final PolicyEntry templateEntry = ImmutablePolicyEntry.of(Label.of("DEFAULT"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateUser")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/template"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);
        // Note: ImmutablePolicyEntry.of(label, subjects, resources, importableType) creates an entry
        // with allowedAdditions=null (absent), which is exactly what we want to test.
        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry).build();

        final ResourceKey ownKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/own"));
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "ownUser")),
                Resources.newInstance(Resource.newInstance(ownKey,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));

        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(templatePolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();
        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Both consumer's own and template's content survive — absent allowedAdditions = no restriction.
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:templateUser", "google:ownUser");
        assertThat(resolvedConsumer.getResources().getResource(ownKey)).isPresent();
    }

    @Test
    public void testNamespacesAllowedAdditionGatesNamespaceMerge() {
        // Template allows subjects+resources but NOT namespaces. Consumer's own namespaces should be stripped.
        final PolicyEntry templateEntry = PoliciesModelFactory.newPolicyEntry(Label.of("DEFAULT"),
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                Arrays.asList("com.template"),
                ImportableType.IMPLICIT,
                new java.util.LinkedHashSet<>(Arrays.asList(
                        AllowedAddition.SUBJECTS, AllowedAddition.RESOURCES)),
                null);
        final Policy templatePolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .set(templateEntry).build();

        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                Arrays.asList("com.consumer"),
                ImportableType.IMPLICIT, null,
                Collections.singletonList(
                        PoliciesModelFactory.newEntryReference(IMPORTED_POLICY_ID, Label.of("DEFAULT"))));
        final Policy consumerPolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(consumerEntry)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID, (EffectedImports) null))
                .build();

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id ->
                IMPORTED_POLICY_ID.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(templatePolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        final Policy resolved = consumerPolicy.withResolvedImports(loader).toCompletableFuture().join();
        final PolicyEntry resolvedConsumer = resolved.getEntryFor(Label.of("consumer"))
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Template's namespace survives (it's the base); consumer's own namespace is stripped.
        final List<String> namespaces = resolvedConsumer.getNamespaces().orElse(Collections.emptyList());
        assertThat(namespaces).contains("com.template");
        assertThat(namespaces).doesNotContain("com.consumer");
    }

    @Test
    public void testLocalReferenceToNeverEntryIsSkipped() {
        // A local reference to an entry marked importable=never must be skipped at resolution.
        final Label consumerLabel = Label.of("consumer");
        final Label neverLabel = Label.of("breakGlassAdmin");

        final PolicyEntry neverEntry = ImmutablePolicyEntry.of(neverLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "rootAdmin")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                ImportableType.NEVER);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(consumerLabel,
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "alice")),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(neverLabel)));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(neverEntry).set(consumerEntry).build();

        final Set<PolicyEntry> resolved = PolicyImporter.resolveReferences(policy, policy.getEntriesSet());
        final PolicyEntry resolvedConsumer = resolved.stream()
                .filter(e -> e.getLabel().equals(consumerLabel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // The NEVER entry's content must NOT have been merged.
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).containsExactly("google:alice");
        assertThat(resolvedConsumer.getResources()).isEmpty();
    }

    @Test
    public void testLocalReferenceRespectsAllowedAdditions() {
        // Local references now honor the same allowedAdditions filter as import refs.
        // Target has allowedAdditions=[SUBJECTS] only — consumer's own resource must be stripped.
        final ResourceKey targetKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/target"));
        final ResourceKey ownKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                JsonPointer.of("/own"));
        final PolicyEntry targetEntry = PoliciesModelFactory.newPolicyEntry(Label.of("template"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "templateUser")),
                Resources.newInstance(Resource.newInstance(targetKey,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS),
                null);
        final PolicyEntry consumerEntry = PoliciesModelFactory.newPolicyEntry(Label.of("consumer"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "alice")),
                Resources.newInstance(Resource.newInstance(ownKey,
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE),
                                Permissions.none()))),
                null, ImportableType.IMPLICIT, null,
                Collections.singletonList(PoliciesModelFactory.newLocalEntryReference(Label.of("template"))));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(targetEntry).set(consumerEntry).build();

        final Set<PolicyEntry> resolved = PolicyImporter.resolveReferences(policy, policy.getEntriesSet());
        final PolicyEntry resolvedConsumer = resolved.stream()
                .filter(e -> e.getLabel().equals(Label.of("consumer")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer entry not found"));

        // Subjects merged (SUBJECTS is allowed); consumer's own subject AND template's both present.
        final Set<String> subjectIds = StreamSupport.stream(resolvedConsumer.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(subjectIds).contains("google:templateUser", "google:alice");
        // Resources: template's READ on /target survives, consumer's WRITE on /own is stripped.
        assertThat(resolvedConsumer.getResources().getResource(targetKey)).isPresent();
        assertThat(resolvedConsumer.getResources().getResource(ownKey)).isNotPresent();
    }

    @Test
    public void deepTransitiveChainExceedingMaxDepthIsRejected() {
        // Build a chain of distinct policies P0 → P1 → … where each Pi imports P(i+1) and declares
        // transitiveImports=[P(i+2)]. Every policy ID is unique, so the visited-set short-circuit
        // never fires; resolution recurses one level per link. The chain length is sized so that
        // resolveTransitiveImports is invoked at depths 0..MAX, with the call at depth==MAX raising
        // PolicyImportInvalidException (HTTP 400) instead of silently returning a partially-resolved
        // entry set.
        // Need (MAX+1) transitiveImports-bearing links to drive recursion to depth==MAX, plus one
        // trailing policy that the last link points at — so MAX+3 distinct policies in total.
        final int chainLength = PolicyImporter.MAX_TRANSITIVE_RESOLUTION_DEPTH + 3;
        final PolicyId[] ids = new PolicyId[chainLength];
        for (int i = 0; i < chainLength; i++) {
            ids[i] = PolicyId.of("com.example", "deep" + i);
        }
        final PolicyEntry leafEntry = ImmutablePolicyEntry.of(Label.of("ROLE"),
                Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "user")),
                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                        JsonPointer.of("attributes"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.none()))),
                ImportableType.IMPLICIT);

        final java.util.Map<PolicyId, Policy> policiesById = new java.util.HashMap<>();
        for (int i = 0; i < chainLength; i++) {
            final PolicyImports imports;
            if (i + 1 < chainLength) {
                final EffectedImports effected;
                if (i + 2 < chainLength) {
                    // Each link declares the link-after-next as a transitive import,
                    // forcing resolveTransitiveImports to recurse.
                    effected = PoliciesModelFactory.newEffectedImportedLabels(
                            Collections.emptyList(), Collections.singletonList(ids[i + 2]));
                } else {
                    effected = PoliciesModelFactory.newEffectedImportedLabels(Collections.emptyList());
                }
                imports = PoliciesModelFactory.newPolicyImports(Collections.singletonList(
                        PoliciesModelFactory.newPolicyImport(ids[i + 1], effected)));
            } else {
                imports = emptyPolicyImports();
            }
            final List<PolicyEntry> entries = (i == chainLength - 1)
                    ? Collections.singletonList(leafEntry)
                    : Collections.emptyList();
            policiesById.put(ids[i], ImmutablePolicy.of(ids[i], PolicyLifecycle.ACTIVE,
                    PolicyRevision.newInstance(1), null, null, null, imports, entries));
        }

        final Function<PolicyId, CompletionStage<Optional<Policy>>> loader = id ->
                CompletableFuture.completedFuture(Optional.ofNullable(policiesById.get(id)));

        final java.util.concurrent.ExecutionException thrown =
                org.junit.Assert.assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> PolicyImporter.mergeImportedPolicyEntries(policiesById.get(ids[0]), loader)
                                .toCompletableFuture()
                                .get());
        assertThat(thrown.getCause()).isInstanceOf(PolicyImportInvalidException.class);
        assertThat(thrown.getCause().getMessage())
                .contains(String.valueOf(PolicyImporter.MAX_TRANSITIVE_RESOLUTION_DEPTH));
    }

    private static Policy createPolicy() {
        final List<PolicyEntry> policyEntries = Collections.singletonList(KNOWN_POLICY_ENTRY_OWN);
        return ImmutablePolicy.of(POLICY_ID, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1), null, null, null,
                emptyPolicyImports(), policyEntries);
    }
}
