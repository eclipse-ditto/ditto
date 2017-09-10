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
package org.eclipse.ditto.signals.commands.devops;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectReader;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonReader;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link DevOpsCommandResponse}. Hereby this class extracts the
 * values which are common for all command responses. All remaining required values have to be extracted in {@link
 * FactoryMethodFunction#create(HttpStatusCode, JsonObjectReader)}. There the actual command response object is created,
 * too.
 */
@Immutable
public final class DevOpsCommandResponseJsonDeserializer<T extends DevOpsCommandResponse> {

    private final JsonObjectReader jsonReader;
    private final String expectedCommandResponseType;

    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the command response.
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public DevOpsCommandResponseJsonDeserializer(final String type, final JsonObject jsonObject) {
        checkNotNull(type, "command response type");
        checkNotNull(jsonObject, "JSON object to be deserialized");

        jsonReader = JsonReader.from(jsonObject);
        expectedCommandResponseType = type;
    }

    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the target command response of deserialization.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public DevOpsCommandResponseJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code CommandResponseType}. This method receives the
     * partly deserialized values as well as the {@link JsonReader} for the JSON to obtain further values if required.
     *
     * @param factoryMethodFunction creates the actual {@code CommandResponseType} object.
     * @return the command response.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws JsonParseException if the JSON is invalid or if the command response type differs from the expected one.
     */
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        checkNotNull(factoryMethodFunction, "method for creating a command response object");
        validateCommandResponseType();

        final HttpStatusCode statusCode = HttpStatusCode.forInt(jsonReader.get(DevOpsCommandResponse.JsonFields.STATUS))
                .orElseThrow(() -> new JsonParseException("The given HTTP status code is not supported."));

        return factoryMethodFunction.create(statusCode, jsonReader);
    }

    private void validateCommandResponseType() {
        final String commandResponseType = jsonReader.get(DevOpsCommandResponse.JsonFields.TYPE);
        if (!expectedCommandResponseType.equals(commandResponseType)) {
            final String msg = MessageFormat
                    .format("Command Response JSON was not a ''{0}'' command response but a ''{1}''!",
                            expectedCommandResponseType, commandResponseType);
            final JsonParseException jsonParseException = new JsonParseException(msg);
            throw new DittoJsonException(jsonParseException);
        }
    }

    /**
     * Represents a function that accepts three arguments to produce a {@code DevOpsCommandResponse}. The arguments were
     * extracted from a given JSON beforehand.
     *
     * @param <T> the type of the result of the function.
     */
    @FunctionalInterface
    public interface FactoryMethodFunction<T extends DevOpsCommandResponse> {

        /**
         * Creates a {@code DevOpsCommandResponse} with the help of the given arguments.
         *
         * @param statusCode the status of the response.
         * @param jsonObjectReader the reader which was initialized with the JSON to be deserialized. It can be used to
         * obtain further values from JSON.
         * @return the command response.
         */
        T create(HttpStatusCode statusCode, JsonObjectReader jsonObjectReader);
    }

}
