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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link RetrieveConnection} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveHonoConnectionResponse.TYPE)
public final class RetrieveHonoConnectionResponse extends AbstractCommandResponse<RetrieveHonoConnectionResponse>
        implements ConnectivityQueryCommandResponse<RetrieveHonoConnectionResponse>, WithConnectionId,
        SignalWithEntityId<RetrieveHonoConnectionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveHonoConnection.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFieldDefinition.ofJsonObject("connection", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveHonoConnectionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveHonoConnectionResponse(jsonObject.getValueOrThrow(JSON_CONNECTION),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final JsonObject connection;

    private RetrieveHonoConnectionResponse(final JsonObject connection,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveHonoConnectionResponse.class),
                dittoHeaders);
        this.connection = checkNotNull(connection, "connection");
        checkArgument(connection.getValueOrThrow(Connection.JsonFields.CONNECTION_TYPE),
                ConnectionType.HONO.getName()::equals,
                () -> "The connection must be of type 'Hono'!");
    }

    /**
     * Returns a new instance of {@code RetrieveHonoConnectionResponse}.
     *
     * @param connection the retrieved jsonObject.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveHonoConnectionResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveHonoConnectionResponse of(final JsonObject connection, final DittoHeaders dittoHeaders) {
        return new RetrieveHonoConnectionResponse(connection, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveHonoConnectionResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if connectionId is missing in the passed in {@code jsonString}
     */
    public static RetrieveHonoConnectionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveHonoConnectionResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if connectionId is missing in the passed in {@code jsonString}
     */
    public static RetrieveHonoConnectionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, connection, predicate);
    }

    /**
     * @return the {@code JsonObject} of the connection.
     */
    public JsonObject getJsonObject() {
        return connection;
    }

    @Override
    public ConnectionId getEntityId() {
        return ConnectionId.of(connection.getValueOrThrow(Connection.JsonFields.ID));
    }

    @Override
    public RetrieveHonoConnectionResponse setEntity(final JsonValue entity) {
        return of(entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return connection;
    }

    @Override
    public RetrieveHonoConnectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connection, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveHonoConnectionResponse;
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
        final RetrieveHonoConnectionResponse that = (RetrieveHonoConnectionResponse) o;
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
                ", jsonObject=" + connection +
                "]";
    }

}
