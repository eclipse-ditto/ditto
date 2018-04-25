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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;

/**
 * Response to a {@link RetrieveConnection} command.
 */
@Immutable
public final class RetrieveConnectionStatusResponse extends AbstractCommandResponse<RetrieveConnectionStatusResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionStatusResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveConnectionStatus.NAME;

    static final JsonFieldDefinition<String> JSON_CONNECTION_STATUS =
            JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String connectionId;
    private final ConnectionStatus connectionStatus;

    private RetrieveConnectionStatusResponse(final String connectionId, final ConnectionStatus connectionStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.connectionId = connectionId;
        this.connectionStatus = connectionStatus;
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param connectionStatus the retrieved connection status.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusResponse of(final String connectionId,
            final ConnectionStatus connectionStatus, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionStatus, "Connection Status");
        return new RetrieveConnectionStatusResponse(connectionId, connectionStatus, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionStatusResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatusResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionStatusResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionStatusResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveConnectionStatusResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionStatus readConnectionStatus =
                            ConnectionStatus.forName(jsonObject.getValueOrThrow(JSON_CONNECTION_STATUS))
                                    .orElse(ConnectionStatus.UNKNOWN);

                    return of(readConnectionId, readConnectionStatus, dittoHeaders);
                });
    }

    /**
     * Returns the retrieved {@code ConnectionStatus}.
     *
     * @return the ConnectionStatus.
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(JSON_CONNECTION_STATUS, connectionStatus.getName(), predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public WithEntity setEntity(final JsonValue entity) {
        final ConnectionStatus connectionStatusToSet =
                ConnectionStatus.forName(entity.asString()).orElse(ConnectionStatus.UNKNOWN);
        return of(connectionId, connectionStatusToSet, getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(connectionId);
    }

    @Override
    public RetrieveConnectionStatusResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, connectionStatus, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionStatusResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        final RetrieveConnectionStatusResponse that = (RetrieveConnectionStatusResponse) o;
        return Objects.equals(connectionId, that.connectionId) &&
                connectionStatus == that.connectionStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", connectionStatus=" + connectionStatus +
                "]";
    }

}
