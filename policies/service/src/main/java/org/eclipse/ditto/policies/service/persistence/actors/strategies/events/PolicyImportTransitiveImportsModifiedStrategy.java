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

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportTransitiveImportsModified;

/**
 * This strategy handles {@link PolicyImportTransitiveImportsModified} events.
 *
 * @since 3.9.0
 */
final class PolicyImportTransitiveImportsModifiedStrategy
        extends AbstractPolicyEventStrategy<PolicyImportTransitiveImportsModified> {

    @Override
    protected PolicyBuilder applyEvent(final PolicyImportTransitiveImportsModified event, final Policy policy,
            final PolicyBuilder policyBuilder) {
        final PolicyId importedPolicyId = event.getImportedPolicyId();
        return policy.getPolicyImports().getPolicyImport(importedPolicyId)
                .map(existingImport -> {
                    final PolicyImport newImport = PoliciesModelFactory.policyImportWithTransitiveImports(
                            existingImport, event.getTransitiveImports());
                    return policyBuilder.setPolicyImports(
                            policy.getPolicyImports().setPolicyImport(newImport));
                })
                .orElse(policyBuilder);
    }

}
