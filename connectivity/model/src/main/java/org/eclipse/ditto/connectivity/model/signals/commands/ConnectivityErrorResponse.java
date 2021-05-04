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
package org.eclipse.ditto.connectivity.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.commands.AbstractErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Response to a {@link ConnectivityCommand} which wraps the exception thrown when processing the command.
 */
@Immutable
@JsonParsableCommandResponse(type = ConnectivityErrorResponse.TYPE)
public final class ConnectivityErrorResponse extends AbstractErrorResponse<ConnectivityErrorResponse>
        implements ConnectivityCommandResponse<ConnectivityErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final GlobalErrorRegistry ERROR_REGISTRY = GlobalErrorRegistry.getInstance();

    private final DittoRuntimeException dittoRuntimeException;

    private ConnectivityErrorResponse(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoRuntimeException.getHttpStatus(), dittoHeaders);
        this.dittoRuntimeException = checkNotNull(dittoRuntimeException, "CR Runtime Exception");
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
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
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
        final JsonObject payload = jsonObject.getValue(CommandResponse.JsonFields.PAYLOAD)
                .map(JsonValue::asObject)
                .orElseThrow(() -> new JsonMissingFieldException(CommandResponse.JsonFields.PAYLOAD.getPointer()));
        final DittoRuntimeException exception = ERROR_REGISTRY.parse(payload, dittoHeaders);
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

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(CommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException.toJson(schemaVersion, thePredicate),
                predicate);
    }

    @Override
    public ConnectivityErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoRuntimeException, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ConnectivityErrorResponse;
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
        final ConnectivityErrorResponse that = (ConnectivityErrorResponse) o;
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
