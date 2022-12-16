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

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Policy model functionality used in order to perform the importing/merging of imported {@link PolicyEntry}s into the
 * importing Policy.
 *
 * @since 3.1.0
 */
public final class PolicyImporter {

    private PolicyImporter() {
        throw new AssertionError();
    }

    /**
     * Merges potentially {@code imported} {@link PolicyEntry}s from the passed {@code policy} into the policy entries
     * of this passed policy returning a new Set with the combined/merged policy entries.
     *
     * @param policy the Policy to use all contained {@link PolicyEntry}s from, importing configured
     * {@link PolicyImports} by using the provided {@code policyLoader} used to resolve/load the imported policies.
     * @param policyLoader a function to load imported policies, e.g. provided by a cache.
     * @return a combined set of existing {@link PolicyEntry}s from the passed {@code policy} merged with policy entries
     * from imported policies.
     */
    public static CompletionStage<Set<PolicyEntry>> mergeImportedPolicyEntries(final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyLoader) {
        return policy.getPolicyImports().stream()
                .map(policyImport -> {
                    final PolicyId importedPolicyId = policyImport.getImportedPolicyId();
                    final CompletionStage<Optional<Policy>> loadedPolicyOptCs = policyLoader.apply(importedPolicyId);
                    return loadedPolicyOptCs.thenApply(loadedPolicyOpt -> loadedPolicyOpt.map(loadedPolicy -> {
                        final ImportedLabels importedLabels = policyImport.getEffectedImports()
                                .map(EffectedImports::getImportedLabels)
                                .orElse(ImportedLabels.none());
                        return rewriteImportedLabels(importedPolicyId, loadedPolicy, importedLabels);
                    }).orElse(Collections.emptySet()));
                })
                .reduce(CompletableFuture.completedFuture(policy.getEntriesSet()), PolicyImporter::combineSets,
                        PolicyImporter::combineSets);
    }

    private static Set<PolicyEntry> rewriteImportedLabels(final PolicyId importedPolicyId, final Policy importedPolicy,
            final Collection<Label> importedLabels) {

        return importedPolicy.getEntriesSet().stream()
                .flatMap(importedEntry -> importEntry(importedLabels, importedEntry))
                .map(entry -> PoliciesModelFactory.newPolicyEntry(
                        PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel()),
                        entry.getSubjects(),
                        entry.getResources(),
                        entry.getImportableType()
                ))
                .collect(Collectors.toSet());
    }

    private static Stream<PolicyEntry> importEntry(final Collection<Label> importedLabels,
            final PolicyEntry importedEntry) {
        switch (importedEntry.getImportableType()) {
            case IMPLICIT:
                return Stream.of(importedEntry);
            case EXPLICIT:
                return importedLabels.contains(importedEntry.getLabel()) ? Stream.of(importedEntry) : Stream.empty();
            case NEVER:
            default:
                return Stream.empty();
        }
    }

    private static CompletionStage<Set<PolicyEntry>> combineSets(final CompletionStage<Set<PolicyEntry>> set1Cs,
            final CompletionStage<Set<PolicyEntry>> set2Cs) {
        return set1Cs.thenCombine(set2Cs,
                (set1, set2) -> Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet())
        );
    }
}
