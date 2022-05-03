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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.things.model.WithThingId;

/**
 * Aggregates all possible responses relating to a given {@link ThingCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingCommandResponse<T extends ThingCommandResponse<T>> extends CommandResponse<T>, WithThingId,
        SignalWithEntityId<T> {

    /**
     * Type Prefix of Thing command responses.
     */
    String TYPE_PREFIX = "things." + TYPE_QUALIFIER + ":";

    @Override
    default String getResourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Indicates whether the specified signal argument is a {@link ThingCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommandResponse}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isThingCommandResponse(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, ThingCommandResponse.TYPE_PREFIX);
    }

    /**
     * This class contains definitions for all specific fields of a {@code ThingCommandResponse}'s JSON representation.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the ThingCommandResponse's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
