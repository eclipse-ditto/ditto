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


import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Aggregates all {@link Command}s which are related to a {@link org.eclipse.ditto.model.things.Thing}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingCommand<T extends ThingCommand> extends Command<T>, WithThingId {

    /**
     * Type Prefix of Thing commands.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "thing";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    default String getId() {
        return getThingId();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Returns the maximal allowed Thing size in this environment. This is extracted from a system property
     * {@code "ditto.limits.things.max-size.bytes"} and cached upon first retrieval.
     *
     * @return the maximal allowed Thing size.
     */
    static Optional<Long> getMaxThingSize() {
        // lazily initialize static variable upon first access with the system properties value:
        if (ThingCommandRegistry.maxThingSize == null) {
            ThingCommandRegistry.maxThingSize = Long.parseLong(
                    System.getProperty("ditto.limits.things.max-size.bytes", "-1"));
        }

        if (ThingCommandRegistry.maxThingSize > 0) {
            return Optional.of(ThingCommandRegistry.maxThingSize);
        } else {
            return Optional.empty();
        }
    }

    /**
     * This class contains definitions for all specific fields of a {@code ThingCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the ThingCommand's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
