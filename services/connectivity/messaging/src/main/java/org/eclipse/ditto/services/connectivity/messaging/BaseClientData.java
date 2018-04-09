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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.MappingContext;

import akka.actor.ActorRef;

/**
 * The data the {@link BaseClientActor} has in its different {@link BaseClientState States}.
 */
@Immutable
public final class BaseClientData {

    private final String connectionId;
    private final Connection connection;
    private final ConnectionStatus connectionStatus;
    private final ConnectionStatus desiredConnectionStatus;
    @Nullable private final String connectionStatusDetails;
    @Nullable private final MappingContext mappingContext;
    @Nullable private final ActorRef origin;

    /**
     * Constructs new instance of BaseClientData, the data of the {@link BaseClientActor}.
     * @param connectionId the ID of the {@link Connection}.
     * @param connection the optional {@link Connection}.
     * @param connectionStatus the current {@link ConnectionStatus} of the Connection.
     * @param desiredConnectionStatus the desired {@link ConnectionStatus} of the Connection.
     * @param connectionStatusDetails the optional details about the ConnectionStatus.
     * @param mappingContext the {@link MappingContext} to apply for the managed Connection.
     * @param origin the ActorRef which caused the latest state data change.
     */
    BaseClientData(final String connectionId, final Connection connection,
            final ConnectionStatus connectionStatus,
            final ConnectionStatus desiredConnectionStatus,
            @Nullable final String connectionStatusDetails,
            @Nullable final MappingContext mappingContext,
            @Nullable final ActorRef origin) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.connectionStatus = connectionStatus;
        this.desiredConnectionStatus = desiredConnectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
        this.mappingContext = mappingContext;
        this.origin = origin;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Connection getConnection() {
        return connection;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public ConnectionStatus getDesiredConnectionStatus() {
        return desiredConnectionStatus;
    }

    public Optional<String> getConnectionStatusDetails() {
        return Optional.ofNullable(connectionStatusDetails);
    }

    public Optional<MappingContext> getMappingContext() {
        return Optional.ofNullable(mappingContext);
    }

    public Optional<ActorRef> getOrigin() {
        return Optional.ofNullable(origin);
    }

    public BaseClientData setConnection(final Connection connection) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    public BaseClientData setConnectionStatus(final ConnectionStatus connectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    public BaseClientData setDesiredConnectionStatus(final ConnectionStatus desiredConnectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    public BaseClientData setConnectionStatusDetails(@Nullable final String connectionStatusDetails) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    public BaseClientData setMappingContext(@Nullable final MappingContext mappingContext) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    public BaseClientData setOrigin(@Nullable final ActorRef origin) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof BaseClientData)) {return false;}
        final BaseClientData that = (BaseClientData) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(connection, that.connection) &&
                connectionStatus == that.connectionStatus &&
                desiredConnectionStatus == that.desiredConnectionStatus &&
                Objects.equals(connectionStatusDetails, that.connectionStatusDetails) &&
                Objects.equals(mappingContext, that.mappingContext) &&
                Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, mappingContext, origin);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", connection=" + connection +
                ", connectionStatus=" + connectionStatus +
                ", desiredConnectionStatus=" + desiredConnectionStatus +
                ", connectionStatusDetails=" + connectionStatusDetails +
                ", mappingContext=" + mappingContext +
                ", origin=" + origin +
                "]";
    }
}

