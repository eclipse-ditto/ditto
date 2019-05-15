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
package org.eclipse.ditto.services.utils.cacheloaders;

import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates commands to access the Policies service.
 */
final class PolicyCommandFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyCommandFactory.class);

    private PolicyCommandFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a sudo command for retrieving a policy.
     *
     * @param policyId the policyId.
     * @return the created command.
     */
    static SudoRetrievePolicy sudoRetrievePolicy(final String policyId) {
        return SudoRetrievePolicy.of(policyId,
                DittoHeaders.newBuilder().correlationId(getCorrelationId(policyId)).build());
    }

    private static String getCorrelationId(final String policyId) {
        return LogUtil.getCorrelationId(() -> {
            final String correlationId = UUID.randomUUID().toString();
            LOGGER.debug("Found no correlation-id for SudoRetrievePolicy on Policy <{}>. " +
                    "Using new correlation-id: {}", policyId, correlationId);
            return correlationId;
        });
    }

}
