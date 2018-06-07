/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.cache;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;

/**
 * Creates commands to access the Things service.
 */
final class ThingCommandFactory {

    private ThingCommandFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a sudo command for retrieving a thing.
     * @param thingId the thingId.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        final JsonFieldSelector jsonFieldSelector = JsonFieldSelector.newInstance(
                Thing.JsonFields.ID.getPointer(),
                Thing.JsonFields.REVISION.getPointer(),
                Thing.JsonFields.ACL.getPointer(),
                Thing.JsonFields.POLICY_ID.getPointer());
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, jsonFieldSelector, DittoHeaders.empty());
    }
}
