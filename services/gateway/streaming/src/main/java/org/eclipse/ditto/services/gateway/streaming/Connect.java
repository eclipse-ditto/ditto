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
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Objects;

import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;

import akka.actor.ActorRef;

/**
 * Message to be sent in order to establish a new "streaming" connection via {@link StreamingActor}.
 */
public final class Connect {

    private final ActorRef eventAndResponsePublisher;
    private final String connectionCorrelationId;
    private final String type;

    /**
     * Constructs a new {@link Connect} instance.
     *
     * @param eventAndResponsePublisher the ActorRef to the correlating {@link EventAndResponsePublisher}.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param type the type of the "streaming" connection to establish.
     */
    public Connect(final ActorRef eventAndResponsePublisher, final String connectionCorrelationId,
            final String type) {
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
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
                Objects.equals(type, connect.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventAndResponsePublisher, connectionCorrelationId, type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "eventAndResponsePublisher=" + eventAndResponsePublisher +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", type=" + type +
                "]";
    }
}
