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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;

import akka.actor.ActorRef;

/**
 * The data the {@link BaseClientActor} has in its different {@link BaseClientState States}.
 */
@Immutable
public final class BaseClientData {

    /**
     * MDC (logging) field name for the connection ID.
     */
    public static final String MDC_CONNECTION_ID = "connection-id";

    private final String connectionId;
    private final Connection connection;
    private final ConnectionStatus connectionStatus;
    private final ConnectionStatus desiredConnectionStatus;
    @Nullable private final String connectionStatusDetails;
    private final Instant inConnectionStatusSince;
    @Nullable private final ActorRef sessionSender;
    @Nullable private final DittoHeaders sessionHeaders;

    /**
     * Constructs new instance of BaseClientData, the data of the {@link BaseClientActor}.
     *
     * @param connectionId the ID of the {@link Connection}.
     * @param connection the optional {@link Connection}.
     * @param connectionStatus the current {@link ConnectionStatus} of the Connection.
     * @param desiredConnectionStatus the desired {@link ConnectionStatus} of the Connection.
     * @param connectionStatusDetails the optional details about the ConnectionStatus.
     * @param inConnectionStatusSince the instant since when the Client is in its current ConnectionStatus.
     * @param sessionSender the ActorRef which caused the latest state data change.
     */
    BaseClientData(final String connectionId, final Connection connection,
            final ConnectionStatus connectionStatus,
            final ConnectionStatus desiredConnectionStatus,
            @Nullable final String connectionStatusDetails,
            final Instant inConnectionStatusSince,
            @Nullable final ActorRef sessionSender,
            @Nullable DittoHeaders sessionHeaders) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.connectionStatus = connectionStatus;
        this.desiredConnectionStatus = desiredConnectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
        this.inConnectionStatusSince = inConnectionStatusSince;
        this.sessionSender = sessionSender;
        this.sessionHeaders = sessionHeaders;
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

    public Instant getInConnectionStatusSince() {
        return inConnectionStatusSince;
    }

    public Optional<ActorRef> getSessionSender() {
        return Optional.ofNullable(sessionSender);
    }

    public DittoHeaders getSessionHeaders() {
        return Optional.ofNullable(sessionHeaders).orElseGet(DittoHeaders::empty);
    }

    public BaseClientData setConnection(final Connection connection) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders);
    }

    public BaseClientData setConnectionStatus(final ConnectionStatus connectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, Instant.now(), sessionSender, sessionHeaders);
    }

    public BaseClientData setDesiredConnectionStatus(final ConnectionStatus desiredConnectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders);
    }

    public BaseClientData setConnectionStatusDetails(@Nullable final String connectionStatusDetails) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders);
    }

    public BaseClientData setSessionSender(@Nullable final ActorRef origin) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, origin, sessionHeaders);
    }

    public BaseClientData setSessionHeaders(@Nullable final DittoHeaders lastCommandHeaders) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, lastCommandHeaders);
    }

    /**
     * Remove info related to the last command. Should be called when entering a stable state (GENERATED, CONNECTED,
     * DISCONNECTED).
     *
     * @return data without info related to the last command.
     */
    public BaseClientData resetSession() {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, null, null);
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
                Objects.equals(inConnectionStatusSince, that.inConnectionStatusSince) &&
                Objects.equals(sessionSender, that.sessionSender) &&
                Objects.equals(sessionHeaders, that.sessionHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", connection=" + connection +
                ", connectionStatus=" + connectionStatus +
                ", desiredConnectionStatus=" + desiredConnectionStatus +
                ", connectionStatusDetails=" + connectionStatusDetails +
                ", inConnectionStatusSince=" + inConnectionStatusSince +
                ", sessionSender=" + sessionSender +
                ", sessionHeaders=" + sessionHeaders +
                "]";
    }
}

