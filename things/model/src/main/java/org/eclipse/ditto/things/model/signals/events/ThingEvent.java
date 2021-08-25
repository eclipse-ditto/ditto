/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.WithThingId;

/**
 * Interface for all Thing-related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingEvent<T extends ThingEvent<T>> extends EventsourcedEvent<T>, WithThingId, SignalWithEntityId<T> {

    /**
     * Type Prefix of Thing events.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "thing";

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Get the category of the command that caused this event.
     *
     * @return the command category.
     * @since 2.1.0
     */
    Command.Category getCommandCategory();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    @Immutable
    final class JsonFields {

        /**
         * Payload JSON field containing the Thing ID.
         */
        public static final JsonFieldDefinition<String> THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);


        /**
         * Payload JSON field containing the Feature ID.
         */
        public static final JsonFieldDefinition<String> FEATURE_ID =
                JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Payload JSON field containing the Thing.
         */
        public static final JsonFieldDefinition<JsonObject> THING =
                JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
