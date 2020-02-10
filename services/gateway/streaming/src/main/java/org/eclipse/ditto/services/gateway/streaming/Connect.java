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
package org.eclipse.ditto.services.gateway.streaming;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;

import akka.actor.ActorRef;

/**
 * Message to be sent in order to establish a new "streaming" connection via {@link StreamingActor}.
 */
public final class Connect {

    private final ActorRef eventAndResponsePublisher;
    private final String connectionCorrelationId;
    private final String type;
    private final JsonSchemaVersion jsonSchemaVersion;
    @Nullable private final Instant sessionExpirationTime;

    /**
     * Constructs a new {@link Connect} instance.
     *
     * @param eventAndResponsePublisher the ActorRef to the correlating {@link org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher}.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param type the type of the "streaming" connection to establish.
     * @param jsonSchemaVersion schema version of the request for the streaming session.
     * @param sessionExpirationTime how long to keep the session alive when idling.
     */
    public Connect(final ActorRef eventAndResponsePublisher, final String connectionCorrelationId,
            final String type, final JsonSchemaVersion jsonSchemaVersion,
            @Nullable final Instant sessionExpirationTime) {
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
        this.jsonSchemaVersion = jsonSchemaVersion;
        this.sessionExpirationTime = sessionExpirationTime;
    }

    public ActorRef getEventAndResponsePublisher() {
        return eventAndResponsePublisher;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    public String getType() {
        return type;
    }

    public Optional<Instant> getSessionExpirationTime() {
        return Optional.ofNullable(sessionExpirationTime);
    }

    public JsonSchemaVersion getJsonSchemaVersion() {
        return jsonSchemaVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Connect connect = (Connect) o;
        return Objects.equals(eventAndResponsePublisher, connect.eventAndResponsePublisher) &&
                Objects.equals(connectionCorrelationId, connect.connectionCorrelationId) &&
                Objects.equals(type, connect.type) &&
                Objects.equals(sessionExpirationTime, connect.sessionExpirationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventAndResponsePublisher, connectionCorrelationId, type, sessionExpirationTime);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "eventAndResponsePublisher=" + eventAndResponsePublisher +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", type=" + type +
                ", sessionExpirationTime=" + sessionExpirationTime +
                "]";
    }
}
