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
import org.eclipse.ditto.policies.model.signals.events.PolicyImportEntriesModified;

/**
 * This strategy handles {@link PolicyImportEntriesModified} events.
 */
final class PolicyImportEntriesModifiedStrategy
        extends AbstractPolicyEventStrategy<PolicyImportEntriesModified> {

    @Override
    protected PolicyBuilder applyEvent(final PolicyImportEntriesModified event, final Policy policy,
            final PolicyBuilder policyBuilder) {
        final PolicyId importedPolicyId = event.getImportedPolicyId();
        return policy.getPolicyImports().getPolicyImport(importedPolicyId)
                .map(existingImport -> {
                    final PolicyImport newImport = reconstructImportWithEntries(
                            existingImport, event.getImportedLabels());
                    return policyBuilder.setPolicyImports(
                            policy.getPolicyImports().setPolicyImport(newImport));
                })
                .orElse(policyBuilder);
    }

    private static PolicyImport reconstructImportWithEntries(final PolicyImport existingImport,
            final ImportedLabels newLabels) {
        final EntriesAdditions entriesAdditions = existingImport.getEffectedImports()
                .flatMap(EffectedImports::getEntriesAdditions)
                .orElse(PoliciesModelFactory.emptyEntriesAdditions());
        final EffectedImports newEffectedImports =
                PoliciesModelFactory.newEffectedImportedLabels(newLabels, entriesAdditions);
        return PoliciesModelFactory.newPolicyImport(existingImport.getImportedPolicyId(), newEffectedImports);
    }

}
