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
package org.eclipse.ditto.signals.events.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Interface for all Thing-related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingEvent<T extends ThingEvent> extends Event<T>, WithThingId {

    /**
     * Type Prefix of Thing events.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "thing";

    /**
     * Returns the ID of {@link org.eclipse.ditto.model.things.Thing} which was modified.
     *
     * @return the ID of the modified Thing.
     */
    String getThingId();

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
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    @Immutable
    final class JsonFields {

        /**
         * Payload JSON field containing the Thing ID.
         */
        public static final JsonFieldDefinition<String> THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Payload JSON field containing the Feature ID.
         */
        public static final JsonFieldDefinition<String> FEATURE_ID =
                JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Payload JSON field containing the Thing.
         */
        public static final JsonFieldDefinition<JsonObject> THING =
                JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
