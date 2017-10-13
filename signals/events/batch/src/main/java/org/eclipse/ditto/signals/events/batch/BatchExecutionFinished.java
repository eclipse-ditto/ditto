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
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a batch finished.
 */
@Immutable
public final class BatchExecutionFinished extends AbstractBatchEvent<BatchExecutionFinished>
        implements BatchEvent<BatchExecutionFinished> {

    /**
     * The name of this event.
     */
    public static final String NAME = "batchExecutionFinished";

    /**
     * The type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final List<CommandResponse> commandResponses;

    private BatchExecutionFinished(final String batchId,
            @Nullable final Instant timestamp,
            final List<CommandResponse> commandResponses,
            final DittoHeaders dittoHeaders) {

        super(TYPE, batchId, timestamp, dittoHeaders);
        this.commandResponses = Collections.unmodifiableList(new ArrayList<>(commandResponses));
    }

    /**
     * Returns a new {@code BatchExecutionFinished} event for the given {@code batchId}, {@code commandResponses} and
     * {@code dittoHeaders}.
     *
     * @param batchId the identifier of the batch.
     * @param commandResponses the responses to the commands of the batch.
     * @param dittoHeaders the command headers of the batch.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static BatchExecutionFinished of(final String batchId, final List<CommandResponse> commandResponses,
            final DittoHeaders dittoHeaders) {

        requireNonNull(batchId);
        requireNonNull(commandResponses);
        requireNonNull(dittoHeaders);

        return of(batchId, null, commandResponses, dittoHeaders);
    }

    /**
     * Returns a new {@code BatchExecutionFinished} event for the given {@code batchId}, {@code timestamp}, {@code
     * commandResponses} and {@code dittoHeaders}.
     *
     * @param batchId the identifier of the batch.
     * @param timestamp the timestamp of the event.
     * @param commandResponses the responses to the commands of the batch.
     * @param dittoHeaders the command headers of the batch.
     * @return the event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static BatchExecutionFinished of(final String batchId,
            @Nullable final Instant timestamp,
            final List<CommandResponse> commandResponses,
            final DittoHeaders dittoHeaders) {

        requireNonNull(batchId);
        requireNonNull(commandResponses);
        requireNonNull(dittoHeaders);

        return new BatchExecutionFinished(batchId, timestamp, commandResponses, dittoHeaders);
    }

    /**
     * Creates a new {@code BatchExecutionFinished} from a JSON string.
     *
     * @param jsonString the JSON string from which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param commandResponseRegistry the {@link CommandResponseRegistry} to use in order to deserialize the command
     * responses in the JSON.
     * @return the event.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static BatchExecutionFinished fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final CommandResponseRegistry<? extends CommandResponse> commandResponseRegistry) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, commandResponseRegistry);
    }

    /**
     * Creates a new {@code BatchExecutionFinished} from a JSON object.
     *
     * @param jsonObject the JSON object from which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param commandResponseRegistry the {@link CommandResponseRegistry} to use in order to deserialize the command
     * responses in the JSON.
     * @return the event.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static BatchExecutionFinished fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final CommandResponseRegistry<? extends CommandResponse> commandResponseRegistry) {

        return new EventJsonDeserializer<BatchExecutionFinished>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
            final String id = jsonObject.getValueOrThrow(JsonFields.BATCH_ID);
            final List<CommandResponse> commandResponses =
                    jsonObject.getValueOrThrow(JsonFields.RESPONSES).stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(json -> {
                                final DittoHeaders cmdHeaders = json.getValue(JsonFields.DITTO_HEADERS)
                                        .map(JsonValue::asObject)
                                        .map(DittoHeaders::newBuilder)
                                        .map(DittoHeadersBuilder::build)
                                        .orElseThrow(() -> new
                                                JsonMissingFieldException(JsonFields.DITTO_HEADERS.getPointer()));
                                return json.getValue(JsonFields.RESPONSE)
                                        .map(JsonValue::asObject)
                                        .map(responseJson -> commandResponseRegistry.parse(responseJson, cmdHeaders))
                                        .orElseThrow(
                                                () -> new JsonMissingFieldException(JsonFields.RESPONSE.getPointer()));
                            })
                            .collect(Collectors.toList());

            return of(id, timestamp, commandResponses, dittoHeaders);
        });
    }

    /**
     * Returns the responses to each command from the executed batch.
     *
     * @return the responses.
     */
    public List<CommandResponse> getCommandResponses() {
        return commandResponses;
    }

    @Override
    public BatchExecutionFinished setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getBatchId(), getTimestamp().orElse(null), commandResponses, dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.BATCH_ID, getBatchId(), predicate);
        final JsonArray commandResponsesJson = commandResponses.stream()
                .map(response -> JsonFactory.newObjectBuilder()
                        .set(JsonFields.RESPONSE,
                                response.toJson(response.getImplementedSchemaVersion(), FieldType.regularOrSpecial()))
                        .set(JsonFields.DITTO_HEADERS, response.getDittoHeaders().toJson())
                        .build())
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JsonFields.RESPONSES, commandResponsesJson, predicate);
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
        final BatchExecutionFinished that = (BatchExecutionFinished) o;
        return Objects.equals(commandResponses, that.commandResponses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), commandResponses);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", commandResponses=" + commandResponses +
                "]";
    }

}
