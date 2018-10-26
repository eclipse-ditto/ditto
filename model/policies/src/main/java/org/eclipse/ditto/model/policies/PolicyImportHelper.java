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

/**
 * TODO TJ doc
 */
public final class PolicyImportHelper {

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

                            final Optional<Policy> loadedPolicyOpt = policyLoader.apply(policyImport.getImportedPolicyId());
                            return loadedPolicyOpt.map(loadedPolicy -> {
                                final ImportedEntries included =
                                        policyImport.getEffectedImportedEntries().getIncludedImportedEntries();
                                final ImportedEntries excluded =
                                        policyImport.getEffectedImportedEntries().getExcludedImportedEntries();

                                if (included.isEmpty() && excluded.isEmpty()) {
                                    // import them all:
                                    return loadedPolicy.getEntriesSet();
                                } else if (included.isEmpty()) {
                                    // only apply excludes:
                                    final Set<CharSequence> excludedEntryLabels = new HashSet<>(excluded);
                                    return loadedPolicy.removeEntries(excludedEntryLabels);
                                } else {
                                    // calculate those we want to import:
                                    final Set<CharSequence> allExistingLabels = loadedPolicy.getLabels().stream()
                                            .map(Label::toString)
                                            .collect(Collectors.toSet());

                                    allExistingLabels.retainAll(included);
                                    allExistingLabels.removeAll(excluded);

                                    return loadedPolicy.stream()
                                            .filter(policyEntry -> allExistingLabels.contains(policyEntry.getLabel()))
                                            .collect(Collectors.toSet());
                                }
                            }).orElse(Collections.emptySet());
                        })
                )
                .ifPresent(stream -> stream.forEach(importedEntries ->
                        importedEntries.forEach(policyEntriesSet::add)
                ));
        return policyEntriesSet;
    }
}
