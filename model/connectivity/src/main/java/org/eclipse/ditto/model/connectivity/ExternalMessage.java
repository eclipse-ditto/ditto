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
package org.eclipse.ditto.model.connectivity;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;

/**
 * Simple wrapper around the headers and the payload received from or sent to external AMQP (0.9 or 1.0)
 * sources/targets.
 */
public interface ExternalMessage {

    /**
     * Message header for the Content-Type.
     */
    String CONTENT_TYPE_HEADER = DittoHeaderDefinition.CONTENT_TYPE.getKey();

    /**
     * Message header for the reply to address.
     */
    String REPLY_TO_HEADER = "replyTo";

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
     * @return the optional value of the Content-Type header
     */
    default Optional<String> findContentType() {
        return findHeaderIgnoreCase(CONTENT_TYPE_HEADER);
    }

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
     * @return the TopicPath of this ExternalMessage, only makes sense for outgoing messages where the path was
     * already known.
     */
    Optional<String> getTopicPath();

    /**
     * @return the PayloadType of this ExternalMessage
     */
    PayloadType getPayloadType();

    /**
     * @return {@code true} if this message is a response
     */
    boolean isResponse();

    /**
     * The known payload types of ExternalMessages.
     */
    enum PayloadType {
        TEXT,
        BYTES,
        UNKNOWN
    }
}
