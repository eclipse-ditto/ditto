/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Address aliases used by connections of type 'Hono'
 *
 * @since 2.5.0
 */
@Immutable
public final class HonoAddressAliasValues {

    private final String telemetry;
    private final String event;
    private final String commandAndControl;
    private final String commandResponse;

    private HonoAddressAliasValues(final String telemetry, final String event, final String commandAndControl,
            final String commandResponse) {
        this.telemetry = telemetry;
        this.event = event;
        this.commandAndControl = commandAndControl;
        this.commandResponse = commandResponse;
    }

    /**
     * Gets the 'telemetry' alias configuration value
     *
     * @return The telemetry address
     */
    public String getTelemetryAddress() {
        return telemetry;
    }

    /**
     * Gets the 'event' alias configuration value
     *
     * @return The event address
     */
    public String getEventAddress() {
        return event;
    }

    /**
     * Gets the 'commandAndControl' alias value
     *
     * @return The command&control address
     */

    public String getCommandAndControlAddress() {
        return commandAndControl;
    }

    /**
     * Gets the 'commandResponse' alias value
     *
     * @return The commandResponse address
     */
    public String getCommandResponseAddress() {
        return commandResponse;
    }

    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.TELEMETRY, telemetry);
        jsonObjectBuilder.set(JsonFields.EVENT, event);
        jsonObjectBuilder.set(JsonFields.COMMAND_AND_CONTROL, commandAndControl);
        jsonObjectBuilder.set(JsonFields.COMMAND_RESPONSE, commandResponse);
        return jsonObjectBuilder.build();
    }

    static HonoAddressAliasValues fromJson(final JsonObject jsonObject) {
        final String telemetry = jsonObject.getValueOrThrow(JsonFields.TELEMETRY);
        final String event = jsonObject.getValueOrThrow(JsonFields.EVENT);
        final String commandAndControl = jsonObject.getValueOrThrow(JsonFields.COMMAND_AND_CONTROL);
        final String commandResponse = jsonObject.getValueOrThrow(JsonFields.COMMAND_RESPONSE);
        return new HonoAddressAliasValues(telemetry, event, commandAndControl, commandResponse);
    }

    /**
     * Create credentials with username and password.
     *
     * @return credentials.
     */
    public static HonoAddressAliasValues newInstance(final String telemetry, final String event,
            final String commandAndControl, final String commandResponse) {
        return new HonoAddressAliasValues(telemetry, event, commandAndControl, commandResponse);
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

        private static final ConcurrentMap<String, Function<JsonObject, Credentials>> DESERIALIZER_MAP =
                new ConcurrentHashMap<>();
        /**
         * JSON field containing the telemetry
         */
        public static final JsonFieldDefinition<String> TELEMETRY = JsonFieldDefinition.ofString("telemetry");

        /**
         * JSON field containing the event
         */
        public static final JsonFieldDefinition<String> EVENT = JsonFieldDefinition.ofString("event");

        /**
         * JSON field containing the event
         */
        public static final JsonFieldDefinition<String> COMMAND_AND_CONTROL = JsonFieldDefinition.ofString("command");

        /**
         * JSON field containing the event
         */
        public static final JsonFieldDefinition<String> COMMAND_RESPONSE = JsonFieldDefinition.ofString("command_response");
    }
}
