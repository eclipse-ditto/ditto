/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportDeleted;

/**
 * This strategy handles {@link PolicyImportDeleted} events.
 */
final class PolicyImportDeletedStrategy extends AbstractPolicyEventStrategy<PolicyImportDeleted> {

    @Override
    protected PolicyBuilder applyEvent(final PolicyImportDeleted pid, final Policy policy,
            final PolicyBuilder policyBuilder) {
        return policyBuilder.setImports(policy.getImports()
                .map(policyImports -> policyImports.removePolicyImport(pid.getImportedPolicyId()))
                .orElse(null));
    }
}
