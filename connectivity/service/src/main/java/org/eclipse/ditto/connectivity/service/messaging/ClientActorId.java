/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.ditto.connectivity.model.ConnectionId;

import akka.actor.ActorRef;

/**
 * Identifier of a client actor consisting of a connection ID and a number.
 */
public record ClientActorId(ConnectionId connectionId, int clientNumber) {

    private static final char SEPARATOR = ':';

    @Override
    public String toString() {
        return String.format("%s%c%d", connectionId, SEPARATOR, clientNumber);
    }

    /**
     * Create a client actor ID from the actor name.
     *
     * @param actorRef The actor ref.
     * @return The client actor ID.
     * @throws java.lang.IllegalArgumentException if the actor name does not contain the separator character.
     * @throws java.lang.NumberFormatException if the client number cannot be parsed as an integer.
     */
    public static ClientActorId fromActorName(final ActorRef actorRef) {
        final var actorName = URLDecoder.decode(actorRef.path().name(), StandardCharsets.UTF_8);
        final int separatorIndex = actorName.lastIndexOf(SEPARATOR);
        if (separatorIndex < 0) {
            final var errorMessage = String.format("Client actor ID is missing '%c': <%s>", SEPARATOR, actorName);
            throw new IllegalArgumentException(errorMessage);
        }
        final var connectionId = ConnectionId.of(actorName.substring(0, separatorIndex));
        final int clientNumber = Integer.parseInt(actorName.substring(separatorIndex + 1));
        return new ClientActorId(connectionId, clientNumber);
    }
}
