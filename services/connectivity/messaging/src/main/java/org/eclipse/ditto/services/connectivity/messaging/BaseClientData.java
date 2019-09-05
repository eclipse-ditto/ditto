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
package org.eclipse.ditto.services.connectivity.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;

import akka.actor.ActorRef;

/**
 * The data the {@link BaseClientActor} has in its different {@link BaseClientState States}.
 */
@Immutable
public final class BaseClientData {

    private final String connectionId;
    private final Connection connection;
    private final ConnectivityStatus connectionStatus;
    private final ConnectivityStatus desiredConnectionStatus;
    @Nullable private final String connectionStatusDetails;
    private final Instant inConnectionStatusSince;
    @Nullable private final ActorRef sessionSender;
    @Nullable private final DittoHeaders sessionHeaders;
    private final InitializationState initializationState;

    /**
     * Constructs new instance of BaseClientData, the data of the {@link BaseClientActor}.
     *
     * @param connectionId the ID of the {@link Connection}.
     * @param connection the optional {@link Connection}.
     * @param connectionStatus the current {@link ConnectivityStatus} of the Connection.
     * @param desiredConnectionStatus the desired {@link ConnectivityStatus} of the Connection.
     * @param connectionStatusDetails the optional details about the ConnectionStatus.
     * @param inConnectionStatusSince the instant since when the Client is in its current ConnectionStatus.
     * @param sessionSender the ActorRef which caused the latest state data change.
     * @param initializationState the current state of initialization
     */
    BaseClientData(final String connectionId, final Connection connection,
            final ConnectivityStatus connectionStatus,
            final ConnectivityStatus desiredConnectionStatus,
            @Nullable final String connectionStatusDetails,
            final Instant inConnectionStatusSince,
            @Nullable final ActorRef sessionSender,
            @Nullable final DittoHeaders sessionHeaders,
            final InitializationState initializationState) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.connectionStatus = connectionStatus;
        this.desiredConnectionStatus = desiredConnectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
        this.inConnectionStatusSince = inConnectionStatusSince;
        this.sessionSender = sessionSender;
        this.sessionHeaders = sessionHeaders;
        this.initializationState = initializationState;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Connection getConnection() {
        return connection;
    }

    public ConnectivityStatus getConnectionStatus() {
        return connectionStatus;
    }

    ConnectivityStatus getDesiredConnectionStatus() {
        return desiredConnectionStatus;
    }

    Optional<String> getConnectionStatusDetails() {
        return Optional.ofNullable(connectionStatusDetails);
    }

    Instant getInConnectionStatusSince() {
        return inConnectionStatusSince;
    }

    Optional<ActorRef> getSessionSender() {
        return Optional.ofNullable(sessionSender);
    }

    public DittoHeaders getSessionHeaders() {
        return Optional.ofNullable(sessionHeaders).orElseGet(DittoHeaders::empty);
    }

    InitializationState getInitializationState() {
        return initializationState;
    }

    public BaseClientData setConnection(final Connection connection) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders, initializationState);
    }

    public BaseClientData setConnectionStatus(final ConnectivityStatus connectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, Instant.now(), sessionSender, sessionHeaders, initializationState);
    }

    BaseClientData setDesiredConnectionStatus(final ConnectivityStatus desiredConnectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders, initializationState);
    }

    public BaseClientData setConnectionStatusDetails(@Nullable final String connectionStatusDetails) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders, initializationState);
    }

    BaseClientData setSessionSender(@Nullable final ActorRef origin) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, origin, sessionHeaders, initializationState);
    }

    BaseClientData setSessionHeaders(@Nullable final DittoHeaders lastCommandHeaders) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, lastCommandHeaders,
                initializationState);
    }

    BaseClientData setInitializationState(final InitializationState newState) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders, newState);
    }

    /**
     * Remove info related to the last command. Should be called when entering a stable state (UNKNOWN, CONNECTED,
     * DISCONNECTED).
     *
     * @return data without info related to the last command.
     */
    BaseClientData resetSession() {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, null, null,
                new InitializationState(connection.getProcessorPoolSize()));
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
                Objects.equals(sessionHeaders, that.sessionHeaders) &&
                Objects.equals(initializationState, that.initializationState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSender, sessionHeaders, initializationState);
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
                ", initializationState=" + initializationState +
                "]";
    }
}

