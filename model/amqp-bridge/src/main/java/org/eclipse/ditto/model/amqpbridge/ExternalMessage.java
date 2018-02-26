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
package org.eclipse.ditto.model.amqpbridge;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

/**
 * Simple wrapper around the headers and the payload received from external AMQP (0.9 or 1.0) sources.
 */
public interface ExternalMessage {

    /**
     * @return the headers of the ExternalMessage
     */
    Map<String, String> getHeaders();

    /**
     * @param key the header key
     * @param value the header value
     * @return new instance of {@link ExternalMessage} including the provided header
     */
    ExternalMessage withHeader(String key, String value);

    /**
     * @param additionalHeaders headers added to message headers
     * @return new instance of {@link ExternalMessage} including the provided headers
     */
    ExternalMessage withHeaders(Map<String, String> additionalHeaders);

    /**
     * @param key the key to look up in the headers
     * @return the optional value of the specified header {@code key}
     */
    Optional<String> findHeader(String key);

    /**
     * @param key the key to look up in the headers case insensitively
     * @return the optional value of the specified header {@code key}
     */
    Optional<String> findHeaderIgnoreCase(String key);

    /**
     * @return whether this ExternalMessage is a text message
     */
    boolean isTextMessage();

    /**
     * @return whether this ExternalMessage is a bytes message
     */
    boolean isBytesMessage();

    /**
     * @return the text payload
     */
    Optional<String> getTextPayload();

    /**
     * @return the bytes payload
     */
    Optional<ByteBuffer> getBytePayload();

    /**
     * @return the MessageType of this ExternalMessage
     */
    MessageType getMessageType();

    /**
     * @return the PayloadType of this ExternalMessage
     */
    PayloadType getPayloadType();

    /**
     * @return whether this ExternalMessage is a CommandResponse
     */
    boolean isCommandResponse();

    /**
     * @return whether this ExternalMessage is an Event
     */
    boolean isEvent();

    /**
     * @return whether this ExternalMessage is a Command
     */
    boolean isCommand();

    /**
     * @return whether this ExternalMessage is an Error
     */
    boolean isError();

    /**
     * @return whether this ExternalMessage is a Message
     */
    boolean isMessage();

    /**
     * The known types of ExternalMessages.
     */
    enum MessageType {
        COMMAND,
        EVENT,
        RESPONSE,
        MESSAGE,
        ERRORS
    }

    /**
     * The known payload types of ExternalMessages.
     */
    enum PayloadType {
        TEXT,
        BYTES,
        UNKNOWN
    }
}
