/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import static org.eclipse.ditto.connectivity.model.Connection.JsonFields.CLIENT_COUNT;
import static org.eclipse.ditto.connectivity.model.Connection.JsonFields.CONNECTION_STATUS;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link SudoRetrieveConnectionStatus} command.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = SudoRetrieveConnectionStatusResponse.TYPE)
public final class SudoRetrieveConnectionStatusResponse
        extends AbstractCommandResponse<SudoRetrieveConnectionStatusResponse>
        implements ConnectivitySudoQueryCommandResponse<SudoRetrieveConnectionStatusResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + SudoRetrieveConnectionStatus.NAME;

    private static final CommandResponseJsonDeserializer<SudoRetrieveConnectionStatusResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new SudoRetrieveConnectionStatusResponse(
                                ConnectivityStatus.valueOf(jsonObject.getValueOrThrow(CONNECTION_STATUS).toUpperCase()),
                                jsonObject.getValueOrThrow(CLIENT_COUNT),
                                context.getDittoHeaders());
                    });

    private final ConnectivityStatus status;
    private final int clientCount;

    private SudoRetrieveConnectionStatusResponse(final ConnectivityStatus status,
            final int clientCount, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.status = status;
        this.clientCount = clientCount;
    }

    /**
     * Returns a new instance of {@code SudoRetrieveConnectionStatusResponse}.
     *
     * @param status the connection status.
     * @param dittoHeaders the headers of the request.
     * @return a new SudoRetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveConnectionStatusResponse of(final ConnectivityStatus status, final int clientCount,
            final DittoHeaders dittoHeaders) {

        return new SudoRetrieveConnectionStatusResponse(status, clientCount, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveConnectionStatusResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveConnectionStatusResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(CONNECTION_STATUS, status.getName(), thePredicate);
        jsonObjectBuilder.set(CLIENT_COUNT, clientCount, predicate);
    }

    /**
     * Return the connection status.
     *
     * @return the status.
     */
    public ConnectivityStatus getStatus() {
        return status;
    }

    /**
     * Return the client count.
     *
     * @return the client count.
     */
    public int getClientCount() {
        return clientCount;
    }

    @Override
    public SudoRetrieveConnectionStatusResponse setEntity(final JsonValue entity) {
        return of(ConnectivityStatus.valueOf(entity.asString()), clientCount, getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(status.getName());
    }

    @Override
    public SudoRetrieveConnectionStatusResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(status, clientCount, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveConnectionStatusResponse;
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
        final SudoRetrieveConnectionStatusResponse that = (SudoRetrieveConnectionStatusResponse) o;
        return status == that.status && clientCount == that.clientCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), status, clientCount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", status=" + status +
                ", clientCount=" + clientCount +
                "]";
    }

}
