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
package org.eclipse.ditto.internal.utils.akka;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Internal simple command which is Jsonifiable, has a command name, an optional correlationId and optionally payload.
 */
@Immutable
public final class SimpleCommand implements Jsonifiable<JsonObject> {

    private final String commandName;
    @Nullable private final String correlationId;
    @Nullable private final JsonValue payload;

    private SimpleCommand(final String commandName, @Nullable final String correlationId, @Nullable final JsonValue payload) {
        this.commandName = ConditionChecker.checkNotNull(commandName, "command name");
        this.correlationId = correlationId;
        this.payload = payload;
    }

    /**
     * Returns a new {@code SimpleCommand} instance.
     *
     * @param commandName the name of the command.
     * @param correlationId an optional identifier correlating a SimpleCommand to a SimpleCommandResponse.
     * @param payload optional payload to transmit with the SimpleCommand.
     * @return the new SimpleCommand instance.
     */
    public static SimpleCommand of(final String commandName, @Nullable final String correlationId, @Nullable final JsonValue payload) {
        return new SimpleCommand(commandName, correlationId, payload);
    }

    /**
     * Creates a new {@code SimpleCommand} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SimpleCommand is to be created.
     * @return the SimpleCommand which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonString} was not in the expected 'SimpleCommand'
     * format.
     */
    public static SimpleCommand fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code SimpleCommand} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SimpleCommand is to be created.
     * @return the SimpleCommand which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected 'SimpleCommand'
     * format.
     */
    public static SimpleCommand fromJson(final JsonObject jsonObject) {
        final String extractedCommandName = jsonObject.getValueOrThrow(JsonFields.NAME);
        final String extractedCorrelationId = jsonObject.getValue(JsonFields.CORRELATION_ID).orElse(null);
        final JsonValue extractedPayload = jsonObject.getValue(JsonFields.PAYLOAD).orElse(null);

        return of(extractedCommandName, extractedCorrelationId, extractedPayload);
    }

    /**
     * Returns the name of the SimpleCommand.
     *
     * @return the name of the SimpleCommand.
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * Returns the optional correlationId of the SimpleCommand.
     *
     * @return the optional correlationId of the SimpleCommand.
     */
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Returns the optional JSON payload of the SimpleCommand.
     *
     * @return the optional JSON payload of the SimpleCommand.
     */
    public Optional<JsonValue> getPayload() {
        return Optional.ofNullable(payload);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.NAME, commandName);
        if (null != correlationId) {
            jsonObjectBuilder.set(JsonFields.CORRELATION_ID, correlationId);
        }
        if (null != payload) {
            jsonObjectBuilder.set(JsonFields.PAYLOAD, payload);
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SimpleCommand that = (SimpleCommand) o;
        return Objects.equals(commandName, that.commandName) &&
                Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandName, correlationId, payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "commandName=" + commandName +
                ", correlationId=" + correlationId +
                ", payload=" + payload +
                "]";
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a SimpleCommand.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the command name.
         */
        static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition("command");

        /**
         * JSON field containing the correlationId.
         */
        static final JsonFieldDefinition<String> CORRELATION_ID = JsonFactory.newStringFieldDefinition("correlationId");

        /**
         * JSON field containing optional payload.
         */
        static final JsonFieldDefinition<JsonValue> PAYLOAD = JsonFactory.newJsonValueFieldDefinition("payload");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
