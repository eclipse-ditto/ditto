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
package org.eclipse.ditto.model.connectivity;

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

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link ConnectionMetrics}.
 */
@Immutable
final class ImmutableConnectionMetrics implements ConnectionMetrics {

    private final ConnectionStatus connectionStatus;
    @Nullable private final String connectionStatusDetails;
    private final String clientState;
    private final Instant inConnectionStatusSince;
    private final List<SourceMetrics> sourcesMetrics;
    private final List<TargetMetrics> targetsMetrics;

    private ImmutableConnectionMetrics(final ConnectionStatus connectionStatus,
            @Nullable final String connectionStatusDetails, final String clientState,
            final Instant inConnectionStatusSince, final List<SourceMetrics> sourcesMetrics,
            final List<TargetMetrics> targetsMetrics) {

        this.connectionStatus = connectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
        this.clientState = clientState;
        this.inConnectionStatusSince = inConnectionStatusSince;
        this.sourcesMetrics = Collections.unmodifiableList(new ArrayList<>(sourcesMetrics));
        this.targetsMetrics = Collections.unmodifiableList(new ArrayList<>(targetsMetrics));
    }

    /**
     * Creates a new {@code ImmutableConnectionMetrics} instance.
     *
     * @param connectionStatus the ConnectionStatus of the metrics to create
     * @param connectionStatusDetails the optional details about the connection status
     * @param clientState the current state of the Client performing the connection
     * @param inConnectionStatusSince the instant since when the Client is in its current ConnectionStatus
     * @param sourcesMetrics the metrics of all sources of the Connection
     * @param targetsMetrics the metrics of all targets of the Connection
     * @return a new instance of ConnectionMetrics.
     */
    public static ImmutableConnectionMetrics of(final ConnectionStatus connectionStatus,
            @Nullable final String connectionStatusDetails, final Instant inConnectionStatusSince,
            final String clientState, final List<SourceMetrics> sourcesMetrics,
            final List<TargetMetrics> targetsMetrics) {
        checkNotNull(connectionStatus, "connectionStatus");
        checkNotNull(clientState, "clientState");
        checkNotNull(inConnectionStatusSince, "inConnectionStatusSince");
        checkNotNull(sourcesMetrics, "sourcesMetrics");
        checkNotNull(targetsMetrics, "targetsMetrics");

        return new ImmutableConnectionMetrics(connectionStatus, connectionStatusDetails, clientState, inConnectionStatusSince,
                sourcesMetrics, targetsMetrics);
    }

    /**
     * Creates a new {@code ConnectionMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ConnectionMetrics to be created.
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ConnectionMetrics fromJson(final JsonObject jsonObject) {
        final ConnectionStatus readConnectionStatus = ConnectionStatus.forName(
                jsonObject.getValueOrThrow(JsonFields.CONNECTION_STATUS)).orElse(ConnectionStatus.UNKNOWN);
        final String readConnectionStatusDetails = jsonObject.getValue(JsonFields.CONNECTION_STATUS_DETAILS)
                .orElse(null);
        final Instant readInConnectionStatusSince = Instant.parse(jsonObject.getValueOrThrow(
                JsonFields.IN_CONNECTION_STATUS_SINCE));
        final String readClientState = jsonObject.getValueOrThrow(JsonFields.CLIENT_STATE);
        final List<SourceMetrics> readSourceMetrics = jsonObject.getValueOrThrow(JsonFields.SOURCES_METRICS)
                .stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableSourceMetrics::fromJson)
                .collect(Collectors.toList());
        final List<TargetMetrics> readTargetMetrics = jsonObject.getValueOrThrow(JsonFields.TARGETS_METRICS)
                .stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableTargetMetrics::fromJson)
                .collect(Collectors.toList());

        return of(readConnectionStatus, readConnectionStatusDetails, readInConnectionStatusSince, readClientState,
                readSourceMetrics, readTargetMetrics);
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public Optional<String> getConnectionStatusDetails() {
        return Optional.ofNullable(connectionStatusDetails);
    }

    @Override
    public Instant getInConnectionStatusSince() {
        return inConnectionStatusSince;
    }

    @Override
    public String getClientState() {
        return clientState;
    }

    @Override
    public List<SourceMetrics> getSourcesMetrics() {
        return sourcesMetrics;
    }

    @Override
    public List<TargetMetrics> getTargetsMetrics() {
        return targetsMetrics;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS, connectionStatus.getName(), predicate);
        if (connectionStatusDetails != null) {
            jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS_DETAILS, connectionStatusDetails, predicate);
        }
        jsonObjectBuilder.set(JsonFields.IN_CONNECTION_STATUS_SINCE, inConnectionStatusSince.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.CLIENT_STATE, clientState, predicate);
        jsonObjectBuilder.set(JsonFields.SOURCES_METRICS, sourcesMetrics.stream()
                .map(SourceMetrics::toJson)
                .collect(JsonCollectors.valuesToArray()), predicate);
        jsonObjectBuilder.set(JsonFields.TARGETS_METRICS, targetsMetrics.stream()
                .map(TargetMetrics::toJson)
                .collect(JsonCollectors.valuesToArray()), predicate);
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableConnectionMetrics)) {return false;}
        final ImmutableConnectionMetrics that = (ImmutableConnectionMetrics) o;
        return connectionStatus == that.connectionStatus &&
                Objects.equals(connectionStatusDetails, that.connectionStatusDetails) &&
                Objects.equals(inConnectionStatusSince, that.inConnectionStatusSince) &&
                Objects.equals(clientState, that.clientState) &&
                Objects.equals(sourcesMetrics, that.sourcesMetrics) &&
                Objects.equals(targetsMetrics, that.targetsMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionStatus, connectionStatusDetails, inConnectionStatusSince, clientState,
                sourcesMetrics, targetsMetrics);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionStatus=" + connectionStatus +
                ", connectionStatusDetails=" + connectionStatusDetails +
                ", inConnectionStatusSince=" + inConnectionStatusSince +
                ", clientState=" + clientState +
                ", sourcesMetrics=" + sourcesMetrics +
                ", targetsMetrics=" + targetsMetrics +
                "]";
    }
}
