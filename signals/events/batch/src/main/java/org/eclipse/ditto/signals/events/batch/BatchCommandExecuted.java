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
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a command was executed.
 */
@Immutable
public final class BatchCommandExecuted extends AbstractBatchEvent<BatchCommandExecuted>
        implements BatchEvent<BatchCommandExecuted> {

    /**
     * The name of this event.
     */
    public static final String NAME = "batchCommandExecuted";

    /**
     * The type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final CommandResponse response;

    private BatchCommandExecuted(final String batchId, final CommandResponse response,
            @Nullable final Instant timestamp) {

        super(TYPE, batchId, timestamp, response.getDittoHeaders());
        this.response = response;
    }

    /**
     * Returns a new {@code BatchCommandExecuted} event for the given {@code response}.
     *
     * @param batchId the identifier of the batch.
     * @param response the response to the executed command.
     * @return the CommandExecuted.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static BatchCommandExecuted of(final String batchId, final CommandResponse response) {
        return of(batchId, response, null);
    }

    /**
     * Returns a new {@code BatchCommandExecuted} event for the given {@code response} and {@code timestamp}.
     *
     * @param batchId the identifier of the batch.
     * @param response the response to the executed command.
     * @param timestamp the timestamp of the event
     * @return the CommandExecuted.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static BatchCommandExecuted of(final String batchId, final CommandResponse response,
            @Nullable final Instant timestamp) {

        requireNonNull(batchId);
        requireNonNull(response);

        return new BatchCommandExecuted(batchId, response, timestamp);
    }

    /**
     * Creates a new {@code BatchCommandExecuted} from a JSON string.
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
    public static BatchCommandExecuted fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final CommandResponseRegistry<? extends CommandResponse> commandResponseRegistry) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, commandResponseRegistry);
    }

    /**
     * Creates a new {@code BatchCommandExecuted} from a JSON object.
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
    public static BatchCommandExecuted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final CommandResponseRegistry<? extends CommandResponse> commandResponseRegistry) {

        return new EventJsonDeserializer<BatchCommandExecuted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String batchId = jsonObject.getValueOrThrow(JsonFields.BATCH_ID);
            final JsonObject cmdHeadersJson = jsonObject.getValueOrThrow(JsonFields.DITTO_HEADERS);
            final DittoHeaders cmdHeaders = DittoHeaders.newBuilder(cmdHeadersJson).build();
            final JsonObject responseJson = jsonObject.getValueOrThrow(JsonFields.RESPONSE);

            final CommandResponse commandResponse = commandResponseRegistry.parse(responseJson, cmdHeaders);

            return of(batchId, commandResponse, timestamp);
        });
    }

    /**
     * Returns the response to the executed command.
     *
     * @return the response
     */
    public CommandResponse getResponse() {
        return response;
    }

    @Override
    public BatchCommandExecuted setDittoHeaders(final DittoHeaders dittoHeaders) {
        throw new UnsupportedOperationException("This Event does not support setting command headers!");
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        jsonObjectBuilder.set(JsonFields.BATCH_ID, getBatchId());
        jsonObjectBuilder.set(JsonFields.RESPONSE, response.toJson(response.getImplementedSchemaVersion(),
                FieldType.regularOrSpecial()));
        jsonObjectBuilder.set(JsonFields.DITTO_HEADERS, response.getDittoHeaders().toJson());
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
        final BatchCommandExecuted that = (BatchCommandExecuted) o;
        return Objects.equals(response, that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), response);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", response=" + response +
                "]";
    }

}
