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
package org.eclipse.ditto.signals.commands.batch;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.CommandRegistry;

/**
 * This command initiates a batch execution of a list of {@link Command}.
 */
@Immutable
public final class ExecuteBatch extends AbstractCommand<ExecuteBatch> implements BatchCommand<ExecuteBatch> {

    /**
     * Name of this command.
     */
    public static final String NAME = "executeBatch";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * JSON field containing the commands.
     */
    public static final JsonFieldDefinition JSON_COMMANDS =
            JsonFactory.newFieldDefinition("commands", JsonArray.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    /**
     * JSON field containing a single command.
     */
    public static final JsonFieldDefinition JSON_COMMAND =
            JsonFactory.newFieldDefinition("command", JsonObject.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    /**
     * JSON field containing a single command's headers.
     */
    public static final JsonFieldDefinition JSON_DITTO_HEADERS =
            JsonFactory.newFieldDefinition("dittoHeaders", JsonObject.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_2);

    private final String batchId;
    private final List<Command> commands;

    private ExecuteBatch(final String batchId, final List<Command> commands,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.batchId = batchId;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
    }

    /**
     * Returns a new {@code ExecuteBatch} command for the given {@code commands} and {@code dittoHeaders}.
     *
     * @param batchId the identifier of the batch.
     * @param modifyCommands the commands to execute in a batch.
     * @param dittoHeaders the command headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ExecuteBatch of(final String batchId, final List<Command> modifyCommands,
            final DittoHeaders dittoHeaders) {
        requireNonNull(batchId);
        requireNonNull(modifyCommands);
        return new ExecuteBatch(batchId, modifyCommands, dittoHeaders);
    }

    /**
     * Creates a new {@code ExecuteBatch} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @param commandRegistry the {@link CommandRegistry} to use in order to deserialize the commands in the JSON.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ExecuteBatch fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final CommandRegistry<? extends Command> commandRegistry) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, commandRegistry);
    }

    /**
     * Creates a new {@code ExecuteBatch} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @param commandRegistry the {@link CommandRegistry} to use in order to deserialize the commands in the JSON.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ExecuteBatch fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final CommandRegistry<? extends Command> commandRegistry) {
        return new CommandJsonDeserializer<ExecuteBatch>(TYPE, jsonObject).deserialize(jsonObjectReader -> {
            final String batchId = jsonObjectReader.get(BatchCommand.JsonFields.BATCH_ID);
            final List<Command> commands = jsonObjectReader.<JsonArray>get(JSON_COMMANDS).stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(json -> {
                        final DittoHeaders cmdHeaders = json.getValue(JSON_DITTO_HEADERS)
                                .map(JsonValue::asObject)
                                .map(DittoHeaders::newBuilder)
                                .map(DittoHeadersBuilder::build)
                                .orElseThrow(() -> new JsonMissingFieldException(JSON_DITTO_HEADERS.getPointer()));
                        return json.getValue(JSON_COMMAND)
                                .map(JsonValue::asObject)
                                .map(commandJson -> commandRegistry.parse(commandJson, cmdHeaders))
                                .orElseThrow(() -> new JsonMissingFieldException(JSON_COMMAND.getPointer()));
                    })
                    .collect(Collectors.toList());

            return of(batchId, commands, dittoHeaders);
        });
    }

    @Override
    public String getBatchId() {
        return batchId;
    }

    /**
     * Returns the commands to execute in a batch.
     *
     * @return the commands.
     */
    public List<Command> getCommands() {
        return commands;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ExecuteBatch setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(batchId, commands, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(BatchCommand.JsonFields.BATCH_ID, batchId, predicate);
        final JsonArray commandsJson = commands.stream()
                .map(cmd -> JsonFactory.newObjectBuilder()
                        .set(JSON_COMMAND, cmd.toJson(cmd.getImplementedSchemaVersion(), FieldType.regularOrSpecial()))
                        .set(JSON_DITTO_HEADERS, cmd.getDittoHeaders().toJson())
                        .build())
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_COMMANDS, commandsJson, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ExecuteBatch that = (ExecuteBatch) o;
        return Objects.equals(batchId, that.batchId) &&
                Objects.equals(commands, that.commands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), batchId, commands);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "batchId=" + batchId +
                ", commands=" + commands +
                "]";
    }
}
