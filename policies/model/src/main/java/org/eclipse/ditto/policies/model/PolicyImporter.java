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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Policy model functionality used in order to perform the importing/merging of imported {@link PolicyEntry}s into the
 * importing Policy.
 *
 * @since 3.x.0 TODO ditto#298
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
    public static Set<PolicyEntry> mergeImportedPolicyEntries(final Policy policy,
            final Function<PolicyId, Optional<Policy>> policyLoader) {
        final Set<PolicyEntry> policyEntriesSet = new HashSet<>(policy.getEntriesSet());

        policy.getImports()
                .map(PolicyImports::stream)
                .map(stream -> stream.map(policyImport -> {
                    final PolicyId importedPolicyId = policyImport.getImportedPolicyId();
                    final Optional<Policy> loadedPolicyOpt = policyLoader.apply(importedPolicyId);

                    return loadedPolicyOpt.map(loadedPolicy -> {
                        final ImportedLabels included = policyImport.getEffectedImports()
                                .map(EffectedImports::getIncludedImportedLabels)
                                .orElse(ImportedLabels.none());
                        final ImportedLabels excluded = policyImport.getEffectedImports()
                                .map(EffectedImports::getExcludedImportedLabels)
                                .orElse(ImportedLabels.none());

                        if (included.isEmpty() && excluded.isEmpty()) {
                            // import them all:
                            return rewriteImportedLabels(importedPolicyId, loadedPolicy.getEntriesSet());
                        } else if (included.isEmpty()) {
                            // only apply excludes:
                            final Set<CharSequence> excludedEntryLabels = new HashSet<>(excluded);
                            return rewriteImportedLabels(importedPolicyId,
                                    loadedPolicy.removeEntries(excludedEntryLabels));
                        } else {
                            // calculate those we want to import:
                            final Set<Label> allExistingLabels = new LinkedHashSet<>(loadedPolicy.getLabels());

                            allExistingLabels.retainAll(included);
                            allExistingLabels.removeAll(excluded);

                            return rewriteImportedLabels(importedPolicyId,
                                    loadedPolicy.stream()
                                            .filter(policyEntry -> allExistingLabels.contains(policyEntry.getLabel())))
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
                        }
                    }).orElse(Collections.emptySet());
                })).ifPresent(stream -> stream.forEach(policyEntriesSet::addAll));
        return policyEntriesSet;
    }

    private static Set<PolicyEntry> rewriteImportedLabels(final PolicyId importedPolicyId,
            final Iterable<PolicyEntry> importedEntries) {

        return rewriteImportedLabels(importedPolicyId, StreamSupport.stream(importedEntries.spliterator(), false))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Stream<PolicyEntry> rewriteImportedLabels(final PolicyId importedPolicyId,
            final Stream<PolicyEntry> importedEntriesStream) {

        return importedEntriesStream
            .filter(PolicyEntry::isImportable)
            .map(entry ->
                PoliciesModelFactory.newPolicyEntry(
                        PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel()),
                        entry.getSubjects(),
                        entry.getResources(),
                        entry.isImportable()
                )
        );
    }
}
