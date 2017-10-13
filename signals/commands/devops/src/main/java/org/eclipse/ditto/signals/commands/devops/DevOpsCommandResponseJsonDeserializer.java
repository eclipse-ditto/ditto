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
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link DevOpsCommandResponse}. Hereby this class extracts the
 * values which are common for all command responses. All remaining required values have to be extracted by the user
 * provided Supplier.  There the actual command response object is created, too.
 */
// TODO Replace with simple type check.
@Immutable
public final class DevOpsCommandResponseJsonDeserializer<T extends DevOpsCommandResponse> {

    private final JsonObject jsonObject;
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
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public DevOpsCommandResponseJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    /**
     * Validates the command response type and invokes the specified Supplier which provides the actual
     * {@link DevOpsCommandResponse}.
     *
     * @param commandResponseSupplier creates the actual {@code DevOpsCommandResponse} object.
     * @return the command response.
     * @throws NullPointerException if {@code commandResponseSupplier} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object did not contain a field for
     * {@link DevOpsCommandResponse.JsonFields#TYPE}.
     * @throws DittoJsonException if the JSON object did not contain the expected value for
     * {@link DevOpsCommandResponse.JsonFields#TYPE}.
     */
    public T deserialize(final Supplier<T> commandResponseSupplier) {
        checkNotNull(commandResponseSupplier, "Supplier for a command response object");
        validateCommandResponseType();

        return commandResponseSupplier.get();
    }

    private void validateCommandResponseType() {
        final String actualCommandResponseType = jsonObject.getValueOrThrow(DevOpsCommandResponse.JsonFields.TYPE);

        if (!expectedCommandResponseType.equals(actualCommandResponseType)) {
            final String msgPattern = "Command Response JSON was not a <{0}> command response but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedCommandResponseType, actualCommandResponseType);

            throw new DittoJsonException(new JsonParseException(msg));
        }
    }

}
