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
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportEntryAdditionCreated;

/**
 * This strategy handles {@link PolicyImportEntryAdditionCreated} events.
 */
final class PolicyImportEntryAdditionCreatedStrategy
        extends AbstractPolicyEventStrategy<PolicyImportEntryAdditionCreated> {

    @Override
    protected PolicyBuilder applyEvent(final PolicyImportEntryAdditionCreated event, final Policy policy,
            final PolicyBuilder policyBuilder) {
        final PolicyId importedPolicyId = event.getImportedPolicyId();
        return policy.getPolicyImports().getPolicyImport(importedPolicyId)
                .map(existingImport -> {
                    final EntriesAdditions existingAdditions = existingImport.getEntriesAdditions()
                            .orElse(PoliciesModelFactory.emptyEntriesAdditions());
                    final EntriesAdditions newAdditions = existingAdditions.setAddition(event.getEntryAddition());
                    final PolicyImport newImport = reconstructImportWithEntriesAdditions(
                            existingImport, newAdditions);
                    return policyBuilder.setPolicyImports(
                            policy.getPolicyImports().setPolicyImport(newImport));
                })
                .orElse(policyBuilder);
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
