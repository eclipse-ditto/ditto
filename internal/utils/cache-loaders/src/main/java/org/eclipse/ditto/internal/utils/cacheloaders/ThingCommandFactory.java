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
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.ThingId;
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
     */
    static SudoRetrieveThing sudoRetrieveThing(final EntityId thingId) {
        return sudoRetrieveThing(ThingId.of(thingId));
    }

    /**
     * Creates a sudo command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final ThingId thingId) {
        LOGGER.debug("Sending SudoRetrieveThing for Thing with ID <{}>.", thingId);
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, DittoHeaders.newBuilder()
                .correlationId("sudoRetrieveThing-" + UUID.randomUUID())
                .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), Boolean.TRUE.toString())
                .build());
    }

}
