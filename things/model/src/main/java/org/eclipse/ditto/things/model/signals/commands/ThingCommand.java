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
package org.eclipse.ditto.things.model.signals.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.WithThingId;

/**
 * Aggregates all {@link org.eclipse.ditto.base.model.signals.commands.Command}s which are related to a {@link org.eclipse.ditto.things.model.Thing}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingCommand<T extends ThingCommand<T>> extends Command<T>, WithThingId, WithEntityType,
        SignalWithEntityId<T> {

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
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * Returns the entity type {@link org.eclipse.ditto.things.model.ThingConstants#ENTITY_TYPE}.
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
     * Indicates whether the specified signal argument is a {@link ThingCommand}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommand}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isThingCommand(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, ThingCommand.TYPE_PREFIX);
    }

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
