/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * TODO TJ doc
 */
public final class PolicyImportHelper {

    /**
     * Prefix for Policy labels which were imported. As a consequence Policy entries may not start with the prefix.
     */
    private static final String IMPORTED_PREFIX = "imported-";

    private PolicyImportHelper() {
        throw new AssertionError();
    }

    /**
     * TODO TJ doc
     */
    public static Set<PolicyEntry> mergeImportedPolicyEntries(final Policy policy,
            final Function<String, Optional<Policy>> policyLoader) {
        final Set<PolicyEntry> policyEntriesSet = new HashSet<>(policy.getEntriesSet());

        policy.getImports()
                .map(PolicyImports::stream)
                .map(stream -> stream
                        .map(policyImport -> {

                            final String importedPolicyId = policyImport.getImportedPolicyId();
                            final Optional<Policy> loadedPolicyOpt = policyLoader.apply(
                                    importedPolicyId);
                            return loadedPolicyOpt.map(loadedPolicy -> {
                                final ImportedLabels included =
                                        policyImport.getEffectedImports().getIncludedImportedEntries();
                                final ImportedLabels excluded =
                                        policyImport.getEffectedImports().getExcludedImportedEntries();

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
                                    final Set<CharSequence> allExistingLabels = loadedPolicy.getLabels().stream()
                                            .map(Label::toString)
                                            .collect(Collectors.toSet());

                                    allExistingLabels.retainAll(included);
                                    allExistingLabels.removeAll(excluded);

                                    return rewriteImportedLabels(importedPolicyId, loadedPolicy.stream()
                                            .filter(policyEntry -> allExistingLabels.contains(policyEntry.getLabel())))
                                            .collect(Collectors.toSet());
                                }
                            }).orElse(Collections.emptySet());
                        })
                )
                .ifPresent(stream -> stream.forEach(policyEntriesSet::addAll));
        return policyEntriesSet;
    }

    private static Set<PolicyEntry> rewriteImportedLabels(final String importedPolicyId,
            final Iterable<PolicyEntry> importedEntries) {

        return rewriteImportedLabels(importedPolicyId,
                StreamSupport.stream(importedEntries.spliterator(), false)).collect(Collectors.toSet());
    }

    private static Stream<PolicyEntry> rewriteImportedLabels(final String importedPolicyId,
            final Stream<PolicyEntry> importedEntriesStream) {

        return importedEntriesStream
                .map(entry -> PoliciesModelFactory.newPolicyEntry(
                        IMPORTED_PREFIX + importedPolicyId + "-" + entry.getLabel(),
                        entry.getSubjects(), entry.getResources()
                ));
    }
}
