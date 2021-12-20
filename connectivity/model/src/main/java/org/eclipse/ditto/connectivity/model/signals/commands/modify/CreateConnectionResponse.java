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
package org.eclipse.ditto.connectivity.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link CreateConnection} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CreateConnectionResponse.TYPE)
public final class CreateConnectionResponse extends AbstractCommandResponse<CreateConnectionResponse>
        implements ConnectivityModifyCommandResponse<CreateConnectionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ConnectivityCommandResponse.TYPE_PREFIX + CreateConnection.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFieldDefinition.ofJsonObject("connection", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.CREATED;

    private static final CommandResponseJsonDeserializer<CreateConnectionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new CreateConnectionResponse(
                                ConnectivityModelFactory.connectionFromJson(jsonObject.getValueOrThrow(JSON_CONNECTION)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final Connection connection;

    private CreateConnectionResponse(final Connection connection,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        CreateConnectionResponse.class),
                dittoHeaders);
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
        return new CreateConnectionResponse(checkNotNull(connection, "connection"), HTTP_STATUS, dittoHeaders);
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
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
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
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ConnectionId getEntityId() {
        return connection.getId();
    }

    @Override
    public CreateConnectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connection, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreateConnectionResponse;
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
