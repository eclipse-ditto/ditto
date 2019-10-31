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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;

/**
 * Defines the protocol messages used to control emitting of events and messages via WebSocket.
 */
public enum ProtocolMessages {

    /**
     * Message indicating that the Websocket should start emitting twin events.
     */
    START_SEND_EVENTS("START-SEND-EVENTS", StreamingType.EVENTS),
    /**
     * Message indicating that the Websocket should stop emitting twin events.
     */
    STOP_SEND_EVENTS("STOP-SEND-EVENTS", StreamingType.EVENTS),

    /**
     * Message indicating that the Websocket should start emitting live messages.
     */
    START_SEND_MESSAGES("START-SEND-MESSAGES", StreamingType.MESSAGES),
    /**
     * Message indicating that the Websocket should stop emitting live messages.
     */
    STOP_SEND_MESSAGES("STOP-SEND-MESSAGES", StreamingType.MESSAGES),

    /**
     * Message indicating that the Websocket should start emitting live commands.
     */
    START_SEND_LIVE_COMMANDS("START-SEND-LIVE-COMMANDS", StreamingType.LIVE_COMMANDS),
    /**
     * Message indicating that the Websocket should stop emitting live commands.
     */
    STOP_SEND_LIVE_COMMANDS("STOP-SEND-LIVE-COMMANDS", StreamingType.LIVE_COMMANDS),

    /**
     * Message indicating that the Websocket should start emitting live events.
     */
    START_SEND_LIVE_EVENTS("START-SEND-LIVE-EVENTS",StreamingType.LIVE_EVENTS),
    /**
     * Message indicating that the Websocket should stop emitting live events.
     */
    STOP_SEND_LIVE_EVENTS("STOP-SEND-LIVE-EVENTS", StreamingType.LIVE_EVENTS),

    /**
     * Message indicating that a new JWT token was send.
     */
    JWT_TOKEN("JWT-TOKEN", null);

    static final String PARAMETER_SEPARATOR = "?";

    private final String identifier;
    @Nullable private final StreamingType streamingType;

    /**
     * Constructor.
     * @param identifier the string identifier that is sent over the wire
     * @param streamingType the associated {@link StreamingType}
     */
    ProtocolMessages(final String identifier, @Nullable final StreamingType streamingType) {
        this.identifier = identifier;
        this.streamingType = streamingType;
    }

    String getIdentifier() {
        return identifier;
    }

    Optional<StreamingType> getStreamingType() {
        return Optional.ofNullable(streamingType);
    }

    /**
     * @param message message to be checked for a protocol message
     * @return {@code true} if the given message starts with the protocol message identifier
     */
    boolean matches(final String message) {
        return message != null && message.startsWith(getIdentifier());
    }

    /**
     * @param message message to be checked for a protocol message
     * @return {@code true} if the given message starts with the protocol message identifier incl. parameter separator
     */
    boolean matchesWithParameters(final String message) {
        return message != null && message.startsWith(getIdentifier() + PARAMETER_SEPARATOR);
    }

    @Override
    public String toString() {
        return getIdentifier();
    }
}
