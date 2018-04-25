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
package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;

/**
 * Response to a {@link ModifyConnection} command.
 */
@Immutable
public final class ModifyConnectionResponse extends AbstractCommandResponse<ModifyConnectionResponse>
        implements ConnectivityModifyCommandResponse<ModifyConnectionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyConnection.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String connectionId;
    @Nullable private final Connection connectionCreated;

    private ModifyConnectionResponse(final String connectionId,
            final HttpStatusCode statusCode,
            @Nullable final Connection connectionCreated,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.connectionId = checkNotNull(connectionId, "Connection ID");
        this.connectionCreated = connectionCreated;
    }

    /**
     * Returns a new {@code ModifyConnectionResponse} for a created Thing. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param connection the connection which was created.
     * @param dittoHeaders the headers of the request.
     * @return a new ModifyConnectionResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyConnectionResponse created(final Connection connection, final DittoHeaders dittoHeaders) {
        checkNotNull(connection, "Connection");
        return new ModifyConnectionResponse(connection.getId(), HttpStatusCode.CREATED, connection, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyConnectionResponse} for a modified Thing. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param connectionId the ID of the connection which was modified.
     * @param dittoHeaders the headers of the request.
     * @return a new ModifyConnectionResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyConnectionResponse modified(final String connectionId, final DittoHeaders dittoHeaders) {
        return new ModifyConnectionResponse(connectionId, HttpStatusCode.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyConnectionResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyConnectionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyConnectionResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyConnectionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyConnectionResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);
                    final Connection readConnection= jsonObject.getValue(JSON_CONNECTION)
                            .map(ConnectivityModelFactory::connectionFromJson)
                            .orElse(null);

                    return new ModifyConnectionResponse(readConnectionId, statusCode, readConnection, dittoHeaders);
                });
    }

    /**
     * Returns the created {@code Connection}.
     *
     * @return the created Connection.
     */
    public Optional<Connection> getConnectionCreated() {
        return Optional.ofNullable(connectionCreated);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        if (connectionCreated != null) {
            jsonObjectBuilder.set(JSON_CONNECTION, connectionCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(connectionCreated).map(connection ->
                connection.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public ModifyConnectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return connectionCreated != null ? created(connectionCreated, dittoHeaders) :
                modified(connectionId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyConnectionResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModifyConnectionResponse)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ModifyConnectionResponse that = (ModifyConnectionResponse) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(connectionCreated, that.connectionCreated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", connectionCreated=" + connectionCreated +
                "]";
    }
}
