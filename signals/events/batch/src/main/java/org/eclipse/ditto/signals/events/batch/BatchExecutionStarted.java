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
package org.eclipse.ditto.signals.events.batch;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
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
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandRegistry;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a batch started.
 */
@Immutable
public final class BatchExecutionStarted extends AbstractBatchEvent<BatchExecutionStarted> implements
        BatchEvent<BatchExecutionStarted> {

    /**
     * The name of this event.
     */
    public static final String NAME = "batchExecutionStarted";

    /**
     * The type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final List<Command> commands;

    private BatchExecutionStarted(final String batchId,
            @Nullable final Instant timestamp,
            final List<Command> commands,
            final DittoHeaders dittoHeaders) {

        super(TYPE, batchId, timestamp, dittoHeaders);
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
    }

    /**
     * Returns a new {@code BatchExecutionStarted} event for the given {@code batchId}, {@code commands} and {@code
     * dittoHeaders}.
     *
     * @param batchId the identifier of the batch.
     * @param commands the commands of the batch.
     * @param dittoHeaders the command headers of the batch.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static BatchExecutionStarted of(final String batchId, final List<Command> commands,
            final DittoHeaders dittoHeaders) {

        return of(batchId, null, commands, dittoHeaders);
    }

    /**
     * Returns a new {@code BatchExecutionStarted} event for the given {@code batchId}, {@code timestamp}, {@code
     * commands} and
     * {@code
     * dittoHeaders}.
     *
     * @param batchId the identifier of the batch.
     * @param timestamp the timestamp of the event.
     * @param commands the commands of the batch.
     * @param dittoHeaders the command headers of the batch.
     * @return the event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static BatchExecutionStarted of(final String batchId,
            @Nullable final Instant timestamp,
            final List<Command> commands,
            final DittoHeaders dittoHeaders) {

        requireNonNull(batchId);
        requireNonNull(commands);
        requireNonNull(dittoHeaders);

        return new BatchExecutionStarted(batchId, timestamp, commands, dittoHeaders);
    }

    /**
     * Creates a new {@code BatchExecutionStarted} from a JSON string.
     *
     * @param jsonString the JSON string from which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param commandRegistry the {@link CommandRegistry} to use in order to deserialize the commands in the JSON.
     * @return the event.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static BatchExecutionStarted fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final CommandRegistry<? extends Command> commandRegistry) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, commandRegistry);
    }

    /**
     * Creates a new {@code BatchExecutionStarted} from a JSON object.
     *
     * @param jsonObject the JSON object from which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param commandRegistry the {@link CommandRegistry} to use in order to deserialize the commands in the JSON.
     * @return the event.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static BatchExecutionStarted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final CommandRegistry<? extends Command> commandRegistry) {

        return new EventJsonDeserializer<BatchExecutionStarted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String id = jsonObject.getValueOrThrow(JsonFields.BATCH_ID);
            final List<Command> commands = jsonObject.getValueOrThrow(JsonFields.COMMANDS)
                    .stream()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(json -> {
                        final DittoHeaders cmdHeaders = json.getValue(JsonFields.DITTO_HEADERS)
                                .map(JsonValue::asObject)
                                .map(DittoHeaders::newBuilder)
                                .map(DittoHeadersBuilder::build)
                                .orElseThrow(() -> new
                                        JsonMissingFieldException(JsonFields.DITTO_HEADERS.getPointer()));
                        return json.getValue(JsonFields.COMMAND)
                                .map(JsonValue::asObject)
                                .map(commandJson -> commandRegistry.parse(commandJson, cmdHeaders))
                                .orElseThrow(() -> new JsonMissingFieldException(JsonFields.COMMAND.getPointer()));
                    })
                    .collect(Collectors.toList());

            return of(id, timestamp, commands, dittoHeaders);
        });
    }

    /**
     * Returns the commands executed within this batch.
     *
     * @return the commands.
     */
    public List<Command> getCommands() {
        return commands;
    }

    @Override
    public BatchExecutionStarted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getBatchId(), getTimestamp().orElse(null), commands, dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.BATCH_ID, getBatchId(), predicate);
        final JsonArray commandsJson = commands.stream()
                .map(cmd -> JsonFactory.newObjectBuilder()
                        .set(JsonFields.COMMAND,
                                cmd.toJson(cmd.getImplementedSchemaVersion(), FieldType.regularOrSpecial()))
                        .set(JsonFields.DITTO_HEADERS, cmd.getDittoHeaders().toJson())
                        .build())
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JsonFields.COMMANDS, commandsJson, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final BatchExecutionStarted that = (BatchExecutionStarted) o;
        return Objects.equals(commands, that.commands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), commands);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", commands=" + commands +
                "]";
    }

}
