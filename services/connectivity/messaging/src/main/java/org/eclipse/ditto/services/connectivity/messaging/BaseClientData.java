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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * The data the {@link BaseClientActor} has in its different {@link BaseClientState States}.
 */
@Immutable
public final class BaseClientData {

    private final ConnectionId connectionId;
    private final Connection connection;
    private final ConnectivityStatus connectionStatus;
    private final ConnectivityStatus desiredConnectionStatus;
    @Nullable private final String connectionStatusDetails;
    private final Instant inConnectionStatusSince;
    private final List<Pair<ActorRef, DittoHeaders>> sessionSenders;

    /**
     * Constructs new instance of BaseClientData, the data of the {@link BaseClientActor}.
     *
     * @param connectionId the ID of the {@link Connection}.
     * @param connection the optional {@link Connection}.
     * @param connectionStatus the current {@link ConnectivityStatus} of the Connection.
     * @param desiredConnectionStatus the desired {@link ConnectivityStatus} of the Connection.
     * @param connectionStatusDetails the optional details about the ConnectionStatus.
     * @param inConnectionStatusSince the instant since when the Client is in its current ConnectionStatus.
     */
    BaseClientData(final ConnectionId connectionId, final Connection connection,
            final ConnectivityStatus connectionStatus,
            final ConnectivityStatus desiredConnectionStatus,
            @Nullable final String connectionStatusDetails,
            final Instant inConnectionStatusSince) {
        this(connectionId, connection, connectionStatus, desiredConnectionStatus, connectionStatusDetails,
                inConnectionStatusSince, Collections.emptyList());
    }

    private BaseClientData(final ConnectionId connectionId, final Connection connection,
            final ConnectivityStatus connectionStatus,
            final ConnectivityStatus desiredConnectionStatus,
            @Nullable final String connectionStatusDetails,
            final Instant inConnectionStatusSince,
            final List<Pair<ActorRef, DittoHeaders>> sessionSenders) {
        this.connectionId = connectionId;
        this.connection = connection;
        this.connectionStatus = connectionStatus;
        this.desiredConnectionStatus = desiredConnectionStatus;
        this.connectionStatusDetails = connectionStatusDetails;
        this.inConnectionStatusSince = inConnectionStatusSince;
        this.sessionSenders = Collections.unmodifiableList(new ArrayList<>(sessionSenders));
    }

    /**
     * @return the ID of the Connection
     */
    public ConnectionId getConnectionId() {
        return connectionId;
    }

    /**
     * @return the managed Connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * @return the current connection status
     */
    public ConnectivityStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * @return the desired connection status
     */
    ConnectivityStatus getDesiredConnectionStatus() {
        return desiredConnectionStatus;
    }

    /**
     * @return the details description about the current connection status
     */
    Optional<String> getConnectionStatusDetails() {
        return Optional.ofNullable(connectionStatusDetails);
    }

    /**
     * @return the time since when the connection is in the current status
     */
    Instant getInConnectionStatusSince() {
        return inConnectionStatusSince;
    }

    /**
     * @return the Pairs of session senders (including DittoHeaders per sender ActorRef)
     */
    List<Pair<ActorRef, DittoHeaders>> getSessionSenders() {
        return sessionSenders;
    }

    /**
     * @return the DittoHeaders from the most recently added {@code sessionSenders}
     */
    public DittoHeaders getLastSessionHeaders() {
        if (sessionSenders.isEmpty()) {
            return DittoHeaders.empty();
        } else {
            return sessionSenders.get(sessionSenders.size() - 1).second();
        }
    }

    /**
     * Updates the managed connection returning a new instance of BaseClientData.
     *
     * @param connection the new connection to use
     * @return the new instance of BaseClientData
     */
    public BaseClientData setConnection(final Connection connection) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSenders);
    }

    /**
     * Updates the current connection status returning a new instance of BaseClientData.
     *
     * @param connectionStatus the new connection status to use
     * @return the new instance of BaseClientData
     */
    public BaseClientData setConnectionStatus(final ConnectivityStatus connectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, Instant.now(), sessionSenders);
    }

    /**
     * Updates the desired connection staus returning a new instance of BaseClientData.
     *
     * @param desiredConnectionStatus the new desired connection status to use
     * @return the new instance of BaseClientData
     */
    BaseClientData setDesiredConnectionStatus(final ConnectivityStatus desiredConnectionStatus) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSenders);
    }

    public BaseClientData setConnectionStatusDetails(@Nullable final String connectionStatusDetails) {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSenders);
    }

    /**
     * Adds the passed {@code origin} sender with the passed {@code dittoHeaders} to the managed {@code sessionSenders}
     * returning a new instance of BaseClientData.
     *
     * @param origin the sender to add
     * @param dittoHeaders the DittoHeaders to add for the passed sender
     * @return the new instance of BaseClientData
     */
    BaseClientData addSessionSender(@Nullable final ActorRef origin, final DittoHeaders dittoHeaders) {
        if (origin != null) {
            final List<Pair<ActorRef, DittoHeaders>> newSessionSenders = new ArrayList<>(sessionSenders);
            newSessionSenders.add(Pair.create(origin, dittoHeaders));
            return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                    connectionStatusDetails, inConnectionStatusSince, newSessionSenders);
        } else {
            return this;
        }
    }

    /**
     * Remove info related to the last command. Should be called when entering a stable state (UNKNOWN, CONNECTED,
     * DISCONNECTED).
     *
     * @return data without info related to the last command.
     */
    BaseClientData resetSession() {
        return new BaseClientData(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, Collections.emptyList());
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
                Objects.equals(sessionSenders, that.sessionSenders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, connection, connectionStatus, desiredConnectionStatus,
                connectionStatusDetails, inConnectionStatusSince, sessionSenders);
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
                ", sessionSenders=" + sessionSenders +
                "]";
    }
}

