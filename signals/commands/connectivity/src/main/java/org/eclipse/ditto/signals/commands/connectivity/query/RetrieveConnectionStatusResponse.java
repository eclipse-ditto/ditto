/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
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
import org.eclipse.ditto.model.connectivity.ImmutableResourceStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
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

    private final String connectionId;
    private final ConnectionStatus connectionStatus;
    private final List<ResourceStatus> clientStatus;
    private final List<ResourceStatus> sourceStatus;
    private final List<ResourceStatus> targetStatus;

    private RetrieveConnectionStatusResponse(final String connectionId, final ConnectionStatus connectionStatus,
            final List<ResourceStatus> clientStatus,
            final List<ResourceStatus> sourceStatus,
            final List<ResourceStatus> targetStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.connectionId = connectionId;
        this.connectionStatus = connectionStatus;
        this.clientStatus = Collections.unmodifiableList(new ArrayList<>(clientStatus));
        this.sourceStatus = Collections.unmodifiableList(new ArrayList<>(sourceStatus));
        this.targetStatus = Collections.unmodifiableList(new ArrayList<>(targetStatus));
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param connectionStatus the retrieved connection status.
     * @param sourceStatus the retrieved source status.
     * @param targetStatus the retrieved target status.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusResponse of(final String connectionId,
            final ConnectionStatus connectionStatus,
            final List<ResourceStatus> clientStatus,
            final List<ResourceStatus> sourceStatus,
            final List<ResourceStatus> targetStatus,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionStatus, "Connection Status");
        return new RetrieveConnectionStatusResponse(connectionId, connectionStatus, clientStatus, sourceStatus,
                targetStatus, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusResponse closedResponse(final String connectionId,
            final Instant connectionClosedAt,
            final String clientStatus,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionClosedAt, "connectionClosedAt");
        final ImmutableResourceStatus resourceStatus =
                ImmutableResourceStatus.of(ResourceStatus.ResourceType.CLIENT, clientStatus,
                        "connection is closed", connectionClosedAt);
        return new RetrieveConnectionStatusResponse(connectionId, ConnectionStatus.CLOSED,
                Collections.singletonList(resourceStatus),
                Collections.emptyList(),
                Collections.emptyList(), dittoHeaders);
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
                    final String readConnectionId = jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);
                    final ConnectionStatus readConnectionStatus =
                            ConnectionStatus.forName(jsonObject.getValueOrThrow(JsonFields.JSON_CONNECTION_STATUS))
                                    .orElse(ConnectionStatus.UNKNOWN);

                    final List<ResourceStatus> readClientStatus =
                            readAddressStatus(ResourceStatus.ResourceType.CLIENT,
                                    jsonObject.getValueOrThrow(JsonFields.JSON_CLIENT_STATUS));

                    final List<ResourceStatus> readSourceStatus =
                            readAddressStatus(ResourceStatus.ResourceType.SOURCE,
                                    jsonObject.getValueOrThrow(JsonFields.JSON_SOURCE_STATUS));

                    final List<ResourceStatus> readTargetStatus =
                            readAddressStatus(ResourceStatus.ResourceType.TARGET,
                                    jsonObject.getValueOrThrow(JsonFields.JSON_TARGET_STATUS));

                    return of(readConnectionId, readConnectionStatus,readClientStatus, readSourceStatus,
                            readTargetStatus, dittoHeaders);
                });
    }

    private static List<ResourceStatus> readAddressStatus(
            final ImmutableResourceStatus.ResourceType type,
            final JsonArray jsonArray) {
        return jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(status -> ImmutableResourceStatus.fromJson(status, type))
                .collect(Collectors.toList());
    }

    /**
     * @return the current ConnectionStatus of the related {@link Connection}.
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * @return in which state the client handling the {@link Connection} currently is.
     */
    public List<ResourceStatus> getClientStatus() {
        return clientStatus;
    }

    /**
     * @return the source {@link ResourceStatus}.
     */
    public List<ResourceStatus> getSourceStatus() {
        return sourceStatus;
    }

    /**
     * @return the target {@link ResourceStatus}.
     */
    public List<ResourceStatus> getTargetStatus() {
        return targetStatus;
    }

    public RetrieveConnectionStatusResponse withAddressStatus(final ResourceStatus resourceStatus) {
        final List<ResourceStatus> newClientStatus;
        final List<ResourceStatus> newSourceStatus;
        final List<ResourceStatus> newTargetStatus;
        switch (resourceStatus.getResourceType()) {
            case SOURCE:
                newClientStatus = this.getClientStatus();
                newSourceStatus = addToList(this.getSourceStatus(), resourceStatus);
                newTargetStatus = this.getTargetStatus();
                break;
            case TARGET:
                newClientStatus = this.getClientStatus();
                newSourceStatus = this.getSourceStatus();
                newTargetStatus = addToList(this.getTargetStatus(), resourceStatus);
                break;
            case CLIENT:
                newClientStatus = addToList(this.getClientStatus(), resourceStatus);
                newSourceStatus = this.getSourceStatus();
                newTargetStatus = this.getSourceStatus();
                break;
            default:
                newClientStatus = this.getClientStatus();
                newSourceStatus = this.getSourceStatus();
                newTargetStatus = this.getTargetStatus();
                break;
        }
        return of(connectionId, connectionStatus, newClientStatus, newSourceStatus, newTargetStatus, getDittoHeaders());
    }

    private List<ResourceStatus> addToList(List<ResourceStatus> existing,
            final ResourceStatus resourceStatus) {
        final List<ResourceStatus> List = new ArrayList<>(existing);
        List.add(resourceStatus);
        return List;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_CONNECTION_STATUS, connectionStatus.getName(), predicate);

        jsonObjectBuilder.set(JsonFields.JSON_CLIENT_STATUS, clientStatus.stream()
                .map(source -> source.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.JSON_SOURCE_STATUS, sourceStatus.stream()
                        .map(source -> source.toJson(schemaVersion, thePredicate))
                        .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.JSON_TARGET_STATUS, targetStatus.stream()
                        .map(source -> source.toJson(schemaVersion, thePredicate))
                        .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public WithEntity setEntity(final JsonValue entity) {
        final ConnectionStatus connectionStatusToSet = ConnectionStatus.forName(entity.asString()).orElse(ConnectionStatus.UNKNOWN);
        return of(connectionId, connectionStatusToSet, clientStatus, sourceStatus, targetStatus, getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(connectionId);
    }

    @Override
    public RetrieveConnectionStatusResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, connectionStatus, clientStatus, sourceStatus, targetStatus, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionStatusResponse);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final RetrieveConnectionStatusResponse that = (RetrieveConnectionStatusResponse) o;
        return connectionId.equals(that.connectionId) &&
                connectionStatus == that.connectionStatus &&
                Objects.equals(clientStatus, that.clientStatus) &&
                Objects.equals(sourceStatus, that.sourceStatus) &&
                Objects.equals(targetStatus, that.targetStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionStatus, clientStatus, sourceStatus, targetStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", connectionStatus=" + connectionStatus +
                ", clientStatus=" + clientStatus +
                ", sourceStatus=" + sourceStatus +
                ", targetStatus=" + targetStatus +
                "]";
    }

    /**
     * This class contains definitions for all specific fields of a {@code ConnectivityCommandResponse}'s JSON
     * representation.
     */
    static final class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the ConnectivityCommandResponse's connectionId.
         */
        public static final JsonFieldDefinition<String> JSON_CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> JSON_CONNECTION_STATUS =
                JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> JSON_CLIENT_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("clientStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonArray> JSON_SOURCE_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("sourceStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonArray> JSON_TARGET_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("targetStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

}
