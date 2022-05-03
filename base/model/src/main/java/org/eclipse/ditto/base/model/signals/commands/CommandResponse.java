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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Aggregates all possible responses relating to a given {@link Command}.
 *
 * @param <T> the type of the implementing class.
 */
@IndexSubclasses
public interface CommandResponse<T extends CommandResponse<T>> extends Signal<T>, WithHttpStatus {

    /**
     * Type qualifier of command responses.
     */
    String TYPE_QUALIFIER = "responses";

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getDittoHeaders().getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Indicates whether this response is of a type contained in
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaderDefinition#EXPECTED_RESPONSE_TYPES} header.
     *
     * @return true if this response is expected, false if not.
     * @since 1.2.0
     */
    default boolean isOfExpectedResponseType() {
        return getDittoHeaders().getExpectedResponseTypes().contains(getResponseType());
    }

    /**
     * @return the type of this response.
     * @since 1.2.0
     */
    default ResponseType getResponseType() {
        return ResponseType.RESPONSE;
    }

    /**
     * Returns all non-hidden marked fields of this command response.
     *
     * @return a JSON object representation of this command response including only regular, non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    JsonObject toJson(JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

    /**
     * Indicates whether the specified signal argument is an instance of {@code CommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link CommandResponse}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isCommandResponse(@Nullable final Signal<?> signal) {
        return signal instanceof CommandResponse;
    }

    /**
     * Indicates whether the specified signal is a live command response.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a live command response, i.e. an instance of {@link CommandResponse}
     * with channel {@value CHANNEL_LIVE} in its headers.
     * {@code false} if {@code signal} is not a live command response.
     * @since 3.0.0
     */
    static boolean isLiveCommandResponse(@Nullable final Signal<?> signal) {
        return CommandResponse.isMessageCommandResponse(signal) ||
                CommandResponse.isCommandResponse(signal) && Signal.isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is a {@code ThingCommandResponse} without requiring a direct
     * dependency to the things-model.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommandResponse}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isThingCommandResponse(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, WithType.THINGS_COMMAND_RESPONSES_PREFIX);
    }

    /**
     * Indicates whether the specified signal argument is a {@code MessageCommandResponse} without requiring a direct
     * dependency to the messages-model.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommandResponse}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isMessageCommandResponse(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, WithType.MESSAGES_COMMAND_RESPONSES_PREFIX);
    }

    /**
     * This class contains common definitions for all fields of a {@code CommandResponse}'s JSON representation.
     * Implementation of {@code CommandResponse} may add additional fields by extending this class.
     */
    @Immutable
    abstract class JsonFields {

        /**
         * JSON field containing the response type as String.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message's status code as int.
         */
        public static final JsonFieldDefinition<Integer> STATUS = JsonFactory.newIntFieldDefinition("status",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message's payload as {@link org.eclipse.ditto.json.JsonValue}.
         */
        public static final JsonFieldDefinition<JsonValue> PAYLOAD = JsonFactory.newJsonValueFieldDefinition("payload",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Constructs a new {@code JsonFields} object.
         */
        protected JsonFields() {
            super();
        }

    }

}
