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
package org.eclipse.ditto.signals.commands.things;


import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.entity.type.WithEntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingConstants;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Aggregates all {@link Command}s which are related to a {@link org.eclipse.ditto.model.things.Thing}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingCommand<T extends ThingCommand<T>> extends Command<T>, WithThingId, WithEntityType {

    /**
     * Type Prefix of Thing commands.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = ThingConstants.ENTITY_TYPE.toString();

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    default ThingId getEntityId() {
        return getThingEntityId();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * Returns the entity type {@link ThingConstants#ENTITY_TYPE}.
     *
     * @return the Thing entity type.
     * @since 1.1.0
     */
    @Override
    default EntityType getEntityType() {
        return ThingConstants.ENTITY_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);


    /**
     * This class contains definitions for all specific fields of a {@code ThingCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the ThingCommand's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }

}
