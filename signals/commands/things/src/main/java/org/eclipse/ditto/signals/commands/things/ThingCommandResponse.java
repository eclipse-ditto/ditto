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
package org.eclipse.ditto.signals.commands.things;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Aggregates all possible responses relating to a given {@link ThingCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingCommandResponse<T extends ThingCommandResponse> extends CommandResponse<T>, WithThingId {

    /**
     * Type Prefix of Thing command responses.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    @Override
    default String getId() {
        return getThingId();
    }

    @Override
    default String getResourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * This class contains definitions for all specific fields of a {@code ThingCommandResponse}'s JSON representation.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the ThingCommandResponse's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
