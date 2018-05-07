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

/**
 * Response to a {@link CreateConnection} command.
 */
@Immutable
public final class CreateConnectionResponse extends AbstractCommandResponse<CreateConnectionResponse>
        implements ConnectivityModifyCommandResponse<CreateConnectionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CreateConnection.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Connection connection;

    private CreateConnectionResponse(final Connection connection, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.CREATED, dittoHeaders);
        this.connection = connection;
    }

    /**
     * Returns a new instance of {@code CreateConnectionResponse}.
     *
     * @param connection the connection to be created.
     * @param dittoHeaders the headers of the request.
     * @return a new CreateConnectionResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateConnectionResponse of(final Connection connection, final DittoHeaders dittoHeaders) {
        checkNotNull(connection, "Connection");
        return new CreateConnectionResponse(connection, dittoHeaders);
    }

    /**
     * Creates a new {@code CreateConnectionResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreateConnectionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code CreateConnectionResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreateConnectionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<CreateConnectionResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final JsonObject jsonConnection = jsonObject.getValueOrThrow(JSON_CONNECTION);
                    final Connection readConnection = ConnectivityModelFactory.connectionFromJson(jsonConnection);

                    return of(readConnection, dittoHeaders);
                });
    }

    /**
     * Returns the {@code Connection} to be created.
     *
     * @return the Connection.
     */
    public Connection getConnection() {
        return connection;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(connection.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public String getConnectionId() {
        return connection.getId();
    }

    @Override
    public CreateConnectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connection, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof CreateConnectionResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CreateConnectionResponse)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final CreateConnectionResponse that = (CreateConnectionResponse) o;
        return Objects.equals(connection, that.connection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connection);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connection=" + connection +
                "]";
    }
}
