/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.events;

import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.EntryAddition;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.ImportsAliasTarget;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasSubjectModified;

/**
 * This strategy handles {@link ImportsAliasSubjectModified} events.
 */
final class ImportsAliasSubjectModifiedStrategy
        extends AbstractPolicyEventStrategy<ImportsAliasSubjectModified> {

    @Override
    protected PolicyBuilder applyEvent(final ImportsAliasSubjectModified event, final Policy policy,
            final PolicyBuilder policyBuilder) {

        PolicyImports policyImports = policy.getPolicyImports();
        for (final ImportsAliasTarget target : event.getTargets()) {
            final PolicyImports currentImports = policyImports;
            policyImports = currentImports.getPolicyImport(target.getImportedPolicyId())
                    .map(existingImport -> {
                        final EntriesAdditions existingAdditions = existingImport.getEntriesAdditions()
                                .orElse(PoliciesModelFactory.emptyEntriesAdditions());
                        final EntryAddition oldAddition = existingAdditions.getAddition(target.getEntryLabel())
                                .orElse(null);
                        final Subjects existingSubjects = oldAddition != null
                                ? oldAddition.getSubjects().orElse(PoliciesModelFactory.emptySubjects())
                                : PoliciesModelFactory.emptySubjects();
                        final Subjects newSubjects = existingSubjects.setSubject(event.getSubject());
                        final EntryAddition newAddition = PoliciesModelFactory.newEntryAddition(
                                target.getEntryLabel(),
                                newSubjects,
                                oldAddition != null ? oldAddition.getResources().orElse(null) : null,
                                oldAddition != null ? oldAddition.getNamespaces().orElse(null) : null);
                        final EntriesAdditions newAdditions = existingAdditions.setAddition(newAddition);
                        final PolicyImport newImport = reconstructImportWithEntriesAdditions(
                                existingImport, newAdditions);
                        return currentImports.setPolicyImport(newImport);
                    })
                    .orElse(currentImports);
        }
        return policyBuilder.setPolicyImports(policyImports);
    }

    private static PolicyImport reconstructImportWithEntriesAdditions(final PolicyImport existingImport,
            final EntriesAdditions newEntriesAdditions) {
        final ImportedLabels labels = existingImport.getEffectedImports()
                .map(EffectedImports::getImportedLabels)
                .orElse(PoliciesModelFactory.noImportedEntries());
        final EffectedImports newEffectedImports =
                PoliciesModelFactory.newEffectedImportedLabels(labels, newEntriesAdditions);
        return PoliciesModelFactory.newPolicyImport(existingImport.getImportedPolicyId(), newEffectedImports);
    }

}
