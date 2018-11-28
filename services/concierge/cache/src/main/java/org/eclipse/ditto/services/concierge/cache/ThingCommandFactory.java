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

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Creates commands to access the Things service.
 */
final class ThingCommandFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingCommandFactory.class);

    private ThingCommandFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a sudo command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        final JsonFieldSelector jsonFieldSelector = JsonFieldSelector.newInstance(
                Thing.JsonFields.ID.getPointer(),
                Thing.JsonFields.REVISION.getPointer(),
                Thing.JsonFields.ACL.getPointer(),
                Thing.JsonFields.POLICY_ID.getPointer());
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, jsonFieldSelector,
                DittoHeaders.newBuilder().correlationId(getCorrelationId(thingId)).build());
    }

    private static String getCorrelationId(final String thingId) {
        String correlationId = MDC.get("x-correlation-id");
        if (null == correlationId) {
            correlationId = UUID.randomUUID().toString();
            LOGGER.debug("Found no correlation-id for SudoRetrieveThing on Thing <{}>. Using new correlation-id: {}",
                    thingId, correlationId);
            return correlationId;
        } else {
            LOGGER.debug("Found correlation-id [{}] in MDC for SudoRetrieveThing on Thing <{}>.", correlationId, thingId);
            return correlationId;
        }
    }
}
