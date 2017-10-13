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
package org.eclipse.ditto.services.utils.akka;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Internal simple command which is Jsonifiable, has a command name, an optional correlationId and optionally payload.
 */
@Immutable
public final class SimpleCommand implements Jsonifiable<JsonObject> {

    private final String commandName;
    private final String correlationId;
    private final JsonValue payload;

    private SimpleCommand(final String commandName, final String correlationId, final JsonValue payload) {
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
    public static SimpleCommand of(final String commandName, final String correlationId, final JsonValue payload) {
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
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.NAME, commandName)
                .set(JsonFields.CORRELATION_ID, correlationId)
                .set(JsonFields.PAYLOAD, payload)
                .build();
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
