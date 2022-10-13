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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response for a {@link RetrieveConnections} request.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveConnectionsResponse.TYPE)
public final class RetrieveConnectionsResponse extends AbstractCommandResponse<RetrieveConnectionsResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ConnectivityCommandResponse.TYPE_PREFIX + RetrieveConnections.NAME;

    static final JsonFieldDefinition<JsonArray> JSON_CONNECTIONS =
            JsonFieldDefinition.ofJsonArray("connections", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveConnectionsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveConnectionsResponse(jsonObject.getValueOrThrow(JSON_CONNECTIONS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final JsonArray connections;

    private RetrieveConnectionsResponse(final JsonArray connections,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveConnectionsResponse.class),
                dittoHeaders);
        this.connections = connections;
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command.
     *
     * @param connections the retrieved Connections.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionsResponse of(final JsonArray connections, final DittoHeaders dittoHeaders) {
        return new RetrieveConnectionsResponse(checkConnections(connections), HTTP_STATUS, dittoHeaders);
    }

    private static <T> T checkConnections(final T connections) {
        return checkNotNull(connections, "Connections");
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command.
     *
     * @param connections the retrieved Connections.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionsResponse of(final List<Connection> connections, final DittoHeaders dittoHeaders) {
        return of(connections, FieldType.notHidden(), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command.
     *
     * @param connections the retrieved Connections.
     * @param predicate the predicate to apply to the connections when transforming to JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionsResponse of(final List<Connection> connections,
            final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders) {

        final JsonSchemaVersion jsonSchemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        final JsonArray connectionsJsonArray = checkConnections(connections).stream()
                .map(connection -> connection.toJson(jsonSchemaVersion, predicate))
                .collect(JsonCollectors.valuesToArray());

        return of(connectionsJsonArray, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command.
     *
     * @param connections the retrieved Connections.
     * @param fieldSelector the JsonFieldSelector to apply to the passed connections when transforming to JSON.
     * @param predicate the predicate to apply to the connections when transforming to JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionsResponse of(final List<Connection> connections,
            final JsonFieldSelector fieldSelector,
            final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders) {

        final JsonSchemaVersion jsonSchemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        final JsonArray connectionsJsonArray = checkConnections(connections).stream()
                .map(connection -> connection.toJson(jsonSchemaVersion, fieldSelector, predicate))
                .collect(JsonCollectors.valuesToArray());

        return of(connectionsJsonArray, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = DittoJsonException.wrapJsonRuntimeException(() -> JsonObject.of(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveConnections} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the retrieved Connections.
     *
     * @return the retrieved Connections.
     */
    public List<Connection> getConnections() {
        return connections.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ConnectivityModelFactory::connectionFromJson)
                .collect(Collectors.toList());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/connections");
    }

    @Override
    public RetrieveConnectionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connections, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTIONS, connections, predicate);
    }

    @Override
    public RetrieveConnectionsResponse setEntity(final JsonValue jsonValue) {
        return of(jsonValue.asArray(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion jsonSchemaVersion) {
        return connections;
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
        final RetrieveConnectionsResponse that = (RetrieveConnectionsResponse) o;
        return Objects.equals(connections, that.connections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connections);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connections=" + connections +
                "]";
    }

}
