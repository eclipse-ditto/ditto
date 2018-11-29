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
package org.eclipse.ditto.services.concierge.cache;

import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
        String correlationId = MDC.get(LogUtil.X_CORRELATION_ID);
        if (null == correlationId) {
            correlationId = UUID.randomUUID().toString();
            LOGGER.debug("Found no correlation-id for SudoRetrievePolicy on Policy <{}>. " +
                    "Using new correlation-id: {}", policyId, correlationId);
            return correlationId;
        } else {
            LOGGER.debug("Found correlation-id [{}] in MDC for SudoRetrievePolicy on Policy <{}>.",
                    correlationId, policyId);
            return correlationId;
        }
    }
}