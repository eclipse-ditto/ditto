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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * TODO TJ doc
 */
@Immutable
final class ImmutableConnectionMetrics implements ConnectionMetrics {

    private final ConnectionStatus connectionStatus;
    @Nullable private final String connectionStatusDetails;

    private ImmutableConnectionMetrics(final ConnectionStatus connectionStatus,
            @Nullable final String connectionStatusDetails) {

        this.connectionStatus = connectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
    }

    /**
     * Creates a new {@code ImmutableConnectionMetrics} instance.
     *
     * @param connectionStatus the ConnectionStatus of the metrics to create
     * @param connectionStatusDetails the optional details about the connection status
     * @return a new instance of ConnectionMetrics.
     */
    public static ImmutableConnectionMetrics of(final ConnectionStatus connectionStatus,
            @Nullable final String connectionStatusDetails) {
        checkNotNull(connectionStatus, "connectionStatus");
        checkNotNull(connectionStatusDetails, "connectionStatusDetails");

        return new ImmutableConnectionMetrics(connectionStatus, connectionStatusDetails);
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
        final String readConnectionStatusDetails = jsonObject.getValueOrThrow(JsonFields.CONNECTION_STATUS_DETAILS);

        return of(readConnectionStatus, readConnectionStatusDetails);
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS, connectionStatus.getName(), predicate);
        if (connectionStatusDetails != null) {
            jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS_DETAILS, connectionStatusDetails, predicate);
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableConnectionMetrics)) {return false;}
        final ImmutableConnectionMetrics that = (ImmutableConnectionMetrics) o;
        return connectionStatus == that.connectionStatus &&
                Objects.equals(connectionStatusDetails, that.connectionStatusDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionStatus, connectionStatusDetails);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionStatus=" + connectionStatus +
                ", connectionStatusDetails=" + connectionStatusDetails +
                "]";
    }

}
