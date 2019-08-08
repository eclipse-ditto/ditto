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

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @deprecated thing ID is now typed. Use {@link #sudoRetrieveThing(org.eclipse.ditto.model.things.id.ThingId)}
     * instead.
     */
    @Deprecated
    static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        return sudoRetrieveThing(ThingId.of(thingId));
    }


    static SudoRetrieveThing sudoRetrieveThing(final EntityId thingId) {
        return sudoRetrieveThing(ThingId.asThingId(thingId));
    }

    /**
     * Creates a sudo command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final ThingId thingId) {
        LOGGER.debug("Sending SudoRetrieveThing for Thing with ID <{}>", thingId);
        final JsonFieldSelector jsonFieldSelector = JsonFieldSelector.newInstance(
                Thing.JsonFields.ID.getPointer(),
                Thing.JsonFields.REVISION.getPointer(),
                Thing.JsonFields.ACL.getPointer(),
                Thing.JsonFields.POLICY_ID.getPointer());
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, jsonFieldSelector,
                DittoHeaders.newBuilder().correlationId(getCorrelationId(thingId)).build());
    }

    private static String getCorrelationId(final ThingId thingId) {
        return LogUtil.getCorrelationId(() -> {
            final String correlationId = UUID.randomUUID().toString();
            LOGGER.debug("Found no correlation-id for SudoRetrieveThing on Thing <{}>. " +
                    "Using new correlation-id: {}", thingId, correlationId);
            return correlationId;
        });
    }

}
