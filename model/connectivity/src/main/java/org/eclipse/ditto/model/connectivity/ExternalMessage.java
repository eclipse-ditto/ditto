/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
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
     * Message header for the reply to address. MUST be lower-case.
     * "reply-to" is a standard internet message header (RFC-5322).
     */
    String REPLY_TO_HEADER = "reply-to";

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
     * @return the PayloadType of this ExternalMessage
     */
    PayloadType getPayloadType();

    /**
     * @return {@code true} if this message is a response
     */
    boolean isResponse();

    /**
     * @return {@code true} if this message is an error
     */
    boolean isError();

    /**
     * @return the {@link AuthorizationContext} assigned to this message
     */
    Optional<AuthorizationContext> getAuthorizationContext();

    /**
     * @return the required data to apply the enforcement (if enforcement is enabled), empty otherwie
     */
    Optional<ThingIdEnforcement> getThingIdEnforcement();

    /**
     * The known payload types of ExternalMessages.
     */
    enum PayloadType {
        TEXT,
        BYTES,
        UNKNOWN
    }
}
