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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link CommandResponse}. Hereby this class extracts the
 * values which are common for all command responses. All remaining required values have to be extracted in {@link
 * FactoryMethodFunction#create(HttpStatusCode)}. There the actual command response object is
 * created, too.
 */
@Immutable
public final class CommandResponseJsonDeserializer<T extends CommandResponse> {

    private final JsonObject jsonObject;
    private final String expectedCommandResponseType;
    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the command response.
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public CommandResponseJsonDeserializer(final String type, final JsonObject jsonObject) {
        checkNotNull(type, "command response type");
        checkNotNull(jsonObject, "JSON object to be deserialized");

        this.jsonObject = jsonObject;
        expectedCommandResponseType = type;
    }

    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the target command response of deserialization.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public CommandResponseJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code CommandResponseType}. This method receives the
     * partly deserialized values which can be completed by implementors if further values are required.
     *
     * @param factoryMethodFunction creates the actual {@code CommandResponseType} object.
     * @return the command response.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or if the command response type differs from the expected one.
     */
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        checkNotNull(factoryMethodFunction, "method for creating a command response object");
        validateCommandResponseType();

        final int statusCode = jsonObject.getValueOrThrow(CommandResponse.JsonFields.STATUS);
        final HttpStatusCode httpStatusCode = HttpStatusCode.forInt(statusCode).orElseThrow(() -> {
            final String msgPattern = "HTTP status code <{0}> of JSON Object is not supported!";
            return new JsonParseException(MessageFormat.format(msgPattern, statusCode));
        });

        return factoryMethodFunction.create(httpStatusCode);
    }

    private void validateCommandResponseType() {
        final String actualCommandResponseType = jsonObject.getValueOrThrow(CommandResponse.JsonFields.TYPE);

        if (!expectedCommandResponseType.equals(actualCommandResponseType)) {
            final String msgPattern = "Command Response JSON was not a <{0}> command response but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedCommandResponseType, actualCommandResponseType);

            throw new DittoJsonException(new JsonParseException(msg));
        }
    }

    /**
     * Represents a function that accepts three arguments to produce a {@code CommandResponse}. The arguments were
     * extracted from a given JSON beforehand.
     *
     * @param <T> the type of the result of the function.
     */
    @FunctionalInterface
    public interface FactoryMethodFunction<T extends CommandResponse> {

        /**
         * Creates a {@code CommandResponse} with the help of the given arguments.
         *
         * @param statusCode the status of the response.
         * @return the command response.
         */
        T create(HttpStatusCode statusCode);
    }

}
