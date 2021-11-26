/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.UUID;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Creates commands to access the Policies service.
 */
final class PolicyCommandFactory {

    private PolicyCommandFactory() {
        throw new AssertionError();
    }


    /**
     * Creates a sudo command for retrieving a policy.
     *
     * @param policyId the policyId.
     * @return the created command.
     */
    static SudoRetrievePolicy sudoRetrievePolicy(final EntityId policyId) {
        return sudoRetrievePolicy(PolicyId.of(policyId));
    }

    /**
     * Creates a sudo command for retrieving a policy.
     *
     * @param policyId the policyId.
     * @return the created command.
     */
    static SudoRetrievePolicy sudoRetrievePolicy(final PolicyId policyId) {
        return SudoRetrievePolicy.of(policyId,
                DittoHeaders.newBuilder().correlationId("sudoRetrievePolicy-" + UUID.randomUUID()).build());
    }

}
