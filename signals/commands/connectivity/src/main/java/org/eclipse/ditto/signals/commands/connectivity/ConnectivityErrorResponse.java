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
package org.eclipse.ditto.signals.commands.connectivity;

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
 * Response to a {@link ConnectivityCommand} which wraps the exception thrown when processing the command.
 */
@Immutable
public final class ConnectivityErrorResponse extends AbstractCommandResponse<ConnectivityErrorResponse> implements
        ConnectivityCommandResponse<ConnectivityErrorResponse>, ErrorResponse<ConnectivityErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final ConnectivityErrorRegistry ERROR_REGISTRY = ConnectivityErrorRegistry.newInstance();

    private final DittoRuntimeException dittoRuntimeException;

    private ConnectivityErrorResponse(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoRuntimeException.getStatusCode(), dittoHeaders);
        this.dittoRuntimeException = requireNonNull(dittoRuntimeException, "The CR Runtime Exception must not be null");
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ConnectivityErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        return new ConnectivityErrorResponse(dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ConnectivityErrorResponse of(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        return new ConnectivityErrorResponse(dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ConnectivityErrorResponse.
     */
    public static ConnectivityErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(ERROR_REGISTRY, jsonString, dittoHeaders);
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString} using a special {@code ConnectivityErrorRegistry}.
     *
     * @param connectivityErrorRegistry the special {@code ConnectivityErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ConnectivityErrorResponse.
     */
    public static ConnectivityErrorResponse fromJson(final ConnectivityErrorRegistry connectivityErrorRegistry,
            final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(connectivityErrorRegistry, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject}.
     *
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ConnectivityErrorResponse.
     */
    public static ConnectivityErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromJson(ERROR_REGISTRY, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code ConnectivityErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject} using a special {@code ConnectivityErrorRegistry}.
     *
     * @param connectivityErrorRegistry the special {@code ConnectivityErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ConnectivityErrorResponse.
     */
    public static ConnectivityErrorResponse fromJson(final ConnectivityErrorRegistry connectivityErrorRegistry,
            final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final JsonObject payload = jsonObject.getValue(ConnectivityCommandResponse.JsonFields.PAYLOAD)
                .map(JsonValue::asObject)
                .orElseThrow(
                        () -> new JsonMissingFieldException(ConnectivityCommandResponse.JsonFields.PAYLOAD.getPointer()));
        final DittoRuntimeException exception = connectivityErrorRegistry.parse(payload, dittoHeaders);
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
                ConnectivityCommandResponse.JsonFields.PAYLOAD, dittoRuntimeException.toJson(schemaVersion, thePredicate),
                predicate);
    }

    @Override
    public ConnectivityErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoRuntimeException, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ConnectivityErrorResponse);
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
        final ConnectivityErrorResponse
                that = (ConnectivityErrorResponse) o;
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
