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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link DevOpsCommand}. Hereby this class extracts the values
 * which are common for all commands. All remaining required values have to be extracted in a user provided
 * Supplier.  There the actual command object is created, too.
 */
// TODO Replace with simple type check.
@Immutable
public final class DevOpsCommandJsonDeserializer<T extends DevOpsCommand> {

    private final JsonObject jsonObject;
    private final String expectedCommandType;

    /**
     * Constructs a new {@code CommandJsonDeserializer} object.
     *
     * @param type the type of the command.
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or does not contain a type prefix.
     */
    public DevOpsCommandJsonDeserializer(final String type, final JsonObject jsonObject) {
        validateType(argumentNotEmpty(type, "command type"));
        checkNotNull(jsonObject, "JSON object to be deserialized");

        this.jsonObject = jsonObject;
        expectedCommandType = type;
    }

    private static void validateType(final String type) {
        // for backward compatibility, extract the prefix for later use:
        if (!type.contains(":")) {
            final String msgPattern = "The type <{0}> does not contain a prefix separated by a colon (':')!";
            throw new IllegalArgumentException(MessageFormat.format(msgPattern, type));
        }
    }

    /**
     * Constructs a new {@code CommandJsonDeserializer} object.
     *
     * @param type the type of the target command of deserialization.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public DevOpsCommandJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    /**
     * Validates the command type and invokes the specified Supplier which provides the actual {@link DevOpsCommand}.
     *
     * @param commandSupplier creates the actual {@code DevOpsCommand} object.
     * @return the command.
     * @throws NullPointerException if {@code commandSupplier} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object did not contain a field for
     * {@link DevOpsCommand.JsonFields#TYPE}.
     * @throws DittoJsonException if the JSON object did not contain the expected value for
     * {@link DevOpsCommand.JsonFields#TYPE}.
     */
    public T deserialize(final Supplier<T> commandSupplier) {
        checkNotNull(commandSupplier, "Supplier for a command object");
        validateCommandType();

        return commandSupplier.get();
    }

    private void validateCommandType() {
        final String actualCommandType = jsonObject.getValueOrThrow(DevOpsCommand.JsonFields.TYPE);

        if (!expectedCommandType.equals(actualCommandType)) {
            final String msgPattern = "DevOpsCommand JSON was not a <{0}> command but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedCommandType, actualCommandType);

            throw new DittoJsonException(new JsonParseException(msg));
        }
    }

}
