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
package org.eclipse.ditto.signals.commands.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectReader;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonReader;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link Command}. Hereby this class extracts the values
 * which are common for all commands. All remaining required values have to be extracted in {@link
 * FactoryMethodFunction#create(JsonObjectReader)}. There the actual command object is created,
 * too.
 */
@Immutable
public final class CommandJsonDeserializer<T extends Command> {

    private final JsonObject jsonObject;
    private final JsonObjectReader jsonReader;
    private final String expectedCommandType;
    private final String commandTypePrefix;

    /**
     * Constructs a new {@code CommandJsonDeserializer} object.
     *
     * @param type the type of the command.
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or does not contain a type prefix.
     */
    public CommandJsonDeserializer(final String type, final JsonObject jsonObject) {
        validateType(argumentNotEmpty(type, "command type"));
        checkNotNull(jsonObject, "JSON object to be deserialized");

        this.jsonObject = jsonObject;
        jsonReader = JsonReader.from(jsonObject);
        expectedCommandType = type;
        commandTypePrefix = type.split(":")[0];
    }

    /**
     * Constructs a new {@code CommandJsonDeserializer} object.
     *
     * @param type the type of the target command of deserialization.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public CommandJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    private void validateType(final String type) {
        // for backward compatibility, extract the prefix for later use:
        if (!type.contains(":")) {
            throw new IllegalArgumentException(
                    MessageFormat.format("The type ''{0}'' does not contain a prefix separated by a colon (':').",
                            type));
        }
    }

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code CommandType}. This method receives the
     * partly
     * deserialized values as well as the {@link JsonReader} for the JSON to obtain further values if required.
     *
     * @param factoryMethodFunction creates the actual {@code CommandType} object.
     * @return the command.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or if the command type differs from the expected one.
     */
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        checkNotNull(factoryMethodFunction, "method for creating a command object");
        validateCommandType();

        return factoryMethodFunction.create(jsonReader);
    }

    private void validateCommandType() {
        final Optional<String> typeOpt = jsonObject.getValue(Command.JsonFields.TYPE).map(JsonValue::asString);
        final String type = typeOpt.orElseGet(() ->
                // if type was not present (was introduced in V2) take "command" instead
                jsonObject.getValue(Command.JsonFields.ID).map(JsonValue::asString)
                        .map(command -> commandTypePrefix + ':' + command) // and transform to V2 format
                        .orElseThrow(
                                () -> JsonMissingFieldException.newBuilder() // fail if "command" also is not present
                                        .fieldName(Command.JsonFields.TYPE.getPointer().toString()).build()));

        if (!expectedCommandType.equals(type)) {
            final String msg =
                    MessageFormat.format("Command JSON was not a ''{0}'' command but a ''{1}''!", expectedCommandType,
                            type);
            final JsonParseException jsonParseException = new JsonParseException(msg);
            throw new DittoJsonException(jsonParseException);
        }
    }

    /**
     * Represents a function that accepts three arguments to produce a {@code Command}. The arguments were
     * extracted from a given JSON beforehand.
     *
     * @param <T> the type of the result of the function.
     */
    @FunctionalInterface
    public interface FactoryMethodFunction<T extends Command> {

        /**
         * Creates a {@code Command} with the help of the given arguments.
         *
         * @param jsonObjectReader the reader which was initialized with the JSON to be deserialized. It can be used to
         * obtain further values from JSON.
         * @return the command.
         */
        T create(JsonObjectReader jsonObjectReader);
    }

}
