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
import java.util.Optional;
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
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
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
    private final ConnectivityStatus connectionStatus;
    private final ConnectivityStatus liveStatus;
    @Nullable private final Instant connectedSince;
    private final List<ResourceStatus> clientStatus;
    private final List<ResourceStatus> sourceStatus;
    private final List<ResourceStatus> targetStatus;

    private RetrieveConnectionStatusResponse(final String connectionId,
            final ConnectivityStatus connectionStatus,
            final ConnectivityStatus liveStatus,
            @Nullable final Instant connectedSince,
            final List<ResourceStatus> clientStatus,
            final List<ResourceStatus> sourceStatus,
            final List<ResourceStatus> targetStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.connectionId = connectionId;
        this.connectionStatus = connectionStatus;
        this.liveStatus = liveStatus;
        this.connectedSince = connectedSince;
        this.clientStatus = Collections.unmodifiableList(new ArrayList<>(clientStatus));
        this.sourceStatus = Collections.unmodifiableList(new ArrayList<>(sourceStatus));
        this.targetStatus = Collections.unmodifiableList(new ArrayList<>(targetStatus));
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param connectionStatus the retrieved connection status.
     * @param liveStatus the live connection status.
     * @param connectedSince the Instant since when the earliest client of the connection was connected.
     * @param sourceStatus the retrieved source status.
     * @param targetStatus the retrieved target status.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusResponse of(final String connectionId,
            final ConnectivityStatus connectionStatus,
            final ConnectivityStatus liveStatus,
            @Nullable final Instant connectedSince,
            final List<ResourceStatus> clientStatus,
            final List<ResourceStatus> sourceStatus,
            final List<ResourceStatus> targetStatus,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionStatus, "Connection Status");
        checkNotNull(liveStatus, "Live Connection Status");
        return new RetrieveConnectionStatusResponse(connectionId, connectionStatus, liveStatus,
                connectedSince, clientStatus, sourceStatus, targetStatus, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionStatusResponse}.
     *
     * @param connectionId the identifier of the connection.
     * @param connectionClosedAt the instant when the connection was closed
     * @param clientStatus the {@link ConnectivityStatus} of the client
     * @param statusDetails the details string for the {@code clientStatus}
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionStatusResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionStatusResponse closedResponse(final String connectionId,
            final String address,
            final Instant connectionClosedAt,
            final ConnectivityStatus clientStatus,
            final String statusDetails,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(connectionClosedAt, "connectionClosedAt");
        final ResourceStatus resourceStatus =
                ConnectivityModelFactory.newClientStatus(address, clientStatus, statusDetails, connectionClosedAt);
        return new RetrieveConnectionStatusResponse(connectionId, clientStatus, clientStatus, null,
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
                    final String readConnectionId =
                            jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);
                    final ConnectivityStatus readConnectionStatus =
                            ConnectivityStatus.forName(jsonObject.getValueOrThrow(JsonFields.CONNECTION_STATUS))
                                    .orElse(ConnectivityStatus.UNKNOWN);
                    final ConnectivityStatus readLiveStatus =
                            ConnectivityStatus.forName(jsonObject.getValueOrThrow(JsonFields.LIVE_STATUS))
                                    .orElse(ConnectivityStatus.UNKNOWN);
                    final String connSinceStr = jsonObject.getValue(JsonFields.CONNECTED_SINCE).orElse(null);
                    final Instant readConnectedSince = connSinceStr != null ? Instant.parse(connSinceStr) : null;

                    final List<ResourceStatus> readClientStatus =
                            readAddressStatus(jsonObject.getValueOrThrow(JsonFields.CLIENT_STATUS));

                    final List<ResourceStatus> readSourceStatus =
                            readAddressStatus(jsonObject.getValueOrThrow(JsonFields.SOURCE_STATUS));

                    final List<ResourceStatus> readTargetStatus =
                            readAddressStatus(jsonObject.getValueOrThrow(JsonFields.TARGET_STATUS));

                    return of(readConnectionId, readConnectionStatus, readLiveStatus, readConnectedSince,
                            readClientStatus,
                            readSourceStatus,
                            readTargetStatus, dittoHeaders);
                });
    }

    private static List<ResourceStatus> readAddressStatus(
            final JsonArray jsonArray) {
        return jsonArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ConnectivityModelFactory::resourceStatusFromJson)
                .collect(Collectors.toList());
    }

    /**
     * @return the current ConnectionStatus of the related {@link Connection}.
     */
    public ConnectivityStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * @return the current live ConnectionStatus of the related {@link Connection}.
     */
    public ConnectivityStatus getLiveStatus() {
        return liveStatus;
    }

    /**
     * @return the Instant since when the earliest client of the connection was connected.
     */
    public Optional<Instant> getConnectedSince() {
        return Optional.ofNullable(connectedSince);
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

    /**
     * Builds a new instance of RetrieveConnectionStatusResponse based on {@code this} instance with the passed {@code
     * liveStatus} as live ConnectionStatus.
     *
     * @param liveStatus the live ConnectionStatus.
     * @return the new RetrieveConnectionStatusResponse.
     */
    public RetrieveConnectionStatusResponse withLiveStatus(final ConnectivityStatus liveStatus) {
        return of(connectionId, connectionStatus, liveStatus, connectedSince,
                clientStatus,
                sourceStatus, targetStatus, getDittoHeaders());
    }
    /**
     * Builds a new instance of RetrieveConnectionStatusResponse based on {@code this} instance with the passed {@code
     * connectedSince}.
     *
     * @param connectedSince the "connected since" value.
     * @return the new RetrieveConnectionStatusResponse.
     */
    public RetrieveConnectionStatusResponse withConnectedSince(@Nullable final Instant connectedSince) {
        return of(connectionId, connectionStatus, liveStatus, connectedSince,
                clientStatus, sourceStatus, targetStatus, getDittoHeaders());
    }

    /**
     * Builds a new instance of RetrieveConnectionStatusResponse based on {@code this} instance with the passed {@code
     * resourceStatus} as address status.
     *
     * @param resourceStatus the ResourceStatus.
     * @return the new RetrieveConnectionStatusResponse.
     */
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
                newTargetStatus = this.getTargetStatus();
                break;
            default:
                newClientStatus = this.getClientStatus();
                newSourceStatus = this.getSourceStatus();
                newTargetStatus = this.getTargetStatus();
                break;
        }
        return of(connectionId, connectionStatus, liveStatus, connectedSince,
                newClientStatus, newSourceStatus, newTargetStatus, getDittoHeaders());
    }

    private List<ResourceStatus> addToList(List<ResourceStatus> existing,
            final ResourceStatus resourceStatus) {
        final List<ResourceStatus> list = new ArrayList<>(existing);
        list.add(resourceStatus);
        return list;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS, connectionStatus.getName(), predicate);
        jsonObjectBuilder.set(JsonFields.LIVE_STATUS, liveStatus.getName(), predicate);
        jsonObjectBuilder.set(JsonFields.CONNECTED_SINCE, connectedSince != null ? connectedSince.toString() : null,
                predicate);

        jsonObjectBuilder.set(JsonFields.CLIENT_STATUS, clientStatus.stream()
                .map(source -> source.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.SOURCE_STATUS, sourceStatus.stream()
                .map(source -> source.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.TARGET_STATUS, targetStatus.stream()
                .map(source -> source.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public RetrieveConnectionStatusResponse setEntity(final JsonValue entity) {
        return fromJson(entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        appendPayload(jsonObjectBuilder, schemaVersion, field -> true);
        return jsonObjectBuilder.build();
    }

    @Override
    public RetrieveConnectionStatusResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, connectionStatus, liveStatus, connectedSince,
                clientStatus, sourceStatus, targetStatus, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionStatusResponse);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveConnectionStatusResponse that = (RetrieveConnectionStatusResponse) o;
        return connectionId.equals(that.connectionId) &&
                connectionStatus == that.connectionStatus &&
                liveStatus == that.liveStatus &&
                Objects.equals(connectedSince, that.connectedSince) &&
                Objects.equals(clientStatus, that.clientStatus) &&
                Objects.equals(sourceStatus, that.sourceStatus) &&
                Objects.equals(targetStatus, that.targetStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, connectionStatus, liveStatus, connectedSince,
                clientStatus, sourceStatus, targetStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", connectionStatus=" + connectionStatus +
                ", liveStatus=" + liveStatus +
                ", connectedSince=" + connectedSince +
                ", clientStatus=" + clientStatus +
                ", sourceStatus=" + sourceStatus +
                ", targetStatus=" + targetStatus +
                "]";
    }

    /**
     * This class contains definitions for all specific fields of a {@code ConnectivityCommandResponse}'s JSON
     * representation.
     */
    public static final class JsonFields extends CommandResponse.JsonFields {

        public static final JsonFieldDefinition<String> CONNECTION_STATUS =
                JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<String> LIVE_STATUS =
                JsonFactory.newStringFieldDefinition("liveStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<String> CONNECTED_SINCE =
                JsonFactory.newStringFieldDefinition("connectedSince", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonArray> CLIENT_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("clientStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        public static final JsonFieldDefinition<JsonArray> SOURCE_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("sourceStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        public static final JsonFieldDefinition<JsonArray> TARGET_STATUS =
                JsonFactory.newJsonArrayFieldDefinition("targetStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

}
