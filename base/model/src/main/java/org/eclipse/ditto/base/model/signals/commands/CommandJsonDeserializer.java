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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link Command}. Hereby this class extracts the values
 * which are common for all commands. All remaining required values have to be extracted in a user provided Supplier.
 * There the actual command object is created, too.
 *
 * This is not replaced by a simple type check in order to be equal to {@code CommandResponseJsonDeserializer} and
 * {@code EventJsonDeserializer}.
 */
@Immutable
public final class CommandJsonDeserializer<T extends Command> {

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
    public CommandJsonDeserializer(final String type, final JsonObject jsonObject) {
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
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public CommandJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    /**
     * Validates the command type and invokes the specified Supplier which provides the actual {@link Command}.
     *
     * @param commandSupplier creates the actual {@code Command} object.
     * @return the command.
     * @throws NullPointerException if {@code commandSupplier} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object did not contain a field for
     * {@link org.eclipse.ditto.base.model.signals.commands.Command.JsonFields#TYPE}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if the JSON object did not contain
     * the expected value for {@link org.eclipse.ditto.base.model.signals.commands.Command.JsonFields#TYPE}.
     */
    public T deserialize(final Supplier<T> commandSupplier) {
        checkNotNull(commandSupplier, "supplier for a command object");
        validateCommandType();

        return commandSupplier.get();
    }

    private void validateCommandType() {
        final String actualCommandType = jsonObject.getValueOrThrow(Command.JsonFields.TYPE);

        if (!expectedCommandType.equals(actualCommandType)) {
            final String msgPattern = "Command JSON was not a <{0}> command but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedCommandType, actualCommandType);

            throw new DittoJsonException(new JsonParseException(msg));
        }
    }

}
