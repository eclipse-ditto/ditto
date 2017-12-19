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
package org.eclipse.ditto.signals.commands.amqpbridge;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;

/**
 * Response to a {@link AmqpBridgeCommand} which wraps the exception thrown when processing the command.
 */
@Immutable
public final class AmqpBridgeErrorResponse extends AbstractCommandResponse<AmqpBridgeErrorResponse> implements
        AmqpBridgeCommandResponse<AmqpBridgeErrorResponse>, ErrorResponse<AmqpBridgeErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final AmqpBridgeErrorRegistry ERROR_REGISTRY = AmqpBridgeErrorRegistry.newInstance();

    private final DittoRuntimeException dittoRuntimeException;

    private AmqpBridgeErrorResponse(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoRuntimeException.getStatusCode(), dittoHeaders);
        this.dittoRuntimeException = requireNonNull(dittoRuntimeException, "The CR Runtime Exception must not be null");
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static AmqpBridgeErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        return new AmqpBridgeErrorResponse(dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static AmqpBridgeErrorResponse of(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        return new AmqpBridgeErrorResponse(dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the AmqpBridgeErrorResponse.
     */
    public static AmqpBridgeErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(ERROR_REGISTRY, jsonString, dittoHeaders);
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString} using a special {@code AmqpBridgeErrorRegistry}.
     *
     * @param amqpBridgeErrorRegistry the special {@code AmqpBridgeErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the AmqpBridgeErrorResponse.
     */
    public static AmqpBridgeErrorResponse fromJson(final AmqpBridgeErrorRegistry amqpBridgeErrorRegistry,
            final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(amqpBridgeErrorRegistry, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject}.
     *
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the AmqpBridgeErrorResponse.
     */
    public static AmqpBridgeErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromJson(ERROR_REGISTRY, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code AmqpBridgeErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject} using a special {@code AmqpBridgeErrorRegistry}.
     *
     * @param amqpBridgeErrorRegistry the special {@code AmqpBridgeErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the AmqpBridgeErrorResponse.
     */
    public static AmqpBridgeErrorResponse fromJson(final AmqpBridgeErrorRegistry amqpBridgeErrorRegistry,
            final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final JsonObject payload = jsonObject.getValue(AmqpBridgeCommandResponse.JsonFields.PAYLOAD)
                .map(JsonValue::asObject)
                .orElseThrow(
                        () -> new JsonMissingFieldException(AmqpBridgeCommandResponse.JsonFields.PAYLOAD.getPointer()));
        final DittoRuntimeException exception = amqpBridgeErrorRegistry.parse(payload, dittoHeaders);
        return of(exception, dittoHeaders);
    }

    /**
     * Returns the wrapped {@code DittoRuntimeException}.
     *
     * @return the wrapped exception.
     */
    public DittoRuntimeException getDittoRuntimeException() {
        return dittoRuntimeException;
    }

    /**
     * This command does not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    public String getConnectionId() {
        return "";
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(
                AmqpBridgeCommandResponse.JsonFields.PAYLOAD, dittoRuntimeException.toJson(schemaVersion, thePredicate),
                predicate);
    }

    @Override
    public AmqpBridgeErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoRuntimeException, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AmqpBridgeErrorResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        final AmqpBridgeErrorResponse
                that = (AmqpBridgeErrorResponse) o;
        return Objects.equals(dittoRuntimeException, that.dittoRuntimeException);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dittoRuntimeException);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", dittoRuntimeException=" + dittoRuntimeException +
                "]";
    }

}
