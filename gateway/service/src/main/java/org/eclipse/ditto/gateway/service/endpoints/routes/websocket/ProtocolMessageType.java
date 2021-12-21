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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import java.text.MessageFormat;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.pubsub.StreamingType;

/**
 * Enumeration of the protocol message types used to control emitting of events and messages via WebSocket.
 */
public enum ProtocolMessageType {

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
    START_SEND_LIVE_EVENTS("START-SEND-LIVE-EVENTS", StreamingType.LIVE_EVENTS),

    /**
     * Message indicating that the Websocket should stop emitting live events.
     */
    STOP_SEND_LIVE_EVENTS("STOP-SEND-LIVE-EVENTS", StreamingType.LIVE_EVENTS),

    /**
     * Message indicating that a new JSON Web Token was send.
     */
    JWT("JWT-TOKEN", null),

    /**
     * Message indicating that the Websocket should start emitting policy announcements.
     *
     * @since 2.0.0
     */
    START_SEND_POLICY_ANNOUNCEMENTS("START-SEND-POLICY-ANNOUNCEMENTS", StreamingType.POLICY_ANNOUNCEMENTS),

    /**
     * Message indicating that the Websocket should stop emitting policy announcements.
     *
     * @since 2.0.0
     */
    STOP_SEND_POLICY_ANNOUNCEMENTS("STOP-SEND-POLICY-ANNOUNCEMENTS", StreamingType.POLICY_ANNOUNCEMENTS);

    private final String identifier;
    @Nullable private final StreamingType streamingType;

    /**
     * Constructor.
     *
     * @param identifier the string identifier that is sent over the wire
     * @param streamingType the associated {@link StreamingType}
     */
    ProtocolMessageType(final String identifier, @Nullable final StreamingType streamingType) {
        this.identifier = identifier;
        this.streamingType = streamingType;
    }

    /**
     * Returns the identifier of this protocol message type.
     *
     * @return the identifier.
     * @see #toString()
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Indicates whether this protocol message type denotes a messages which starts streaming.
     *
     * @return {@code true} if this protocol message type denotes a message for start sending, {@code false} else.
     */
    public boolean isStartSending() {
        return identifier.startsWith("START");
    }

    /**
     * Expects this ProtocolMessage to have a streaming type and tries to return the streaming type.
     * If this ProtocolMessage does not have a streaming type a NoSuchElementException is thrown.
     *
     * @return the streaming type.
     * @throws java.util.NoSuchElementException if this ProtocolMessage does not have a streaming type.
     */
    public StreamingType getStreamingTypeOrThrow() {
        if (null != streamingType) {
            return streamingType;
        }
        throw new NoSuchElementException(MessageFormat.format("{0} does not have a streaming type!", identifier));
    }

    /**
     * @return the same as {@link #getIdentifier()}
     */
    @Override
    public String toString() {
        return getIdentifier();
    }

}
