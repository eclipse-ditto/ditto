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
package org.eclipse.ditto.connectivity.api;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;

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
    String REPLY_TO_HEADER = DittoHeaderDefinition.REPLY_TO.getKey();

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
     * @param topicPath the {@link TopicPath} to set in the new built ExternalMessage
     * @return new instance of {@link ExternalMessage} including the provided TopicPath
     */
    ExternalMessage withTopicPath(TopicPath topicPath);

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
     * @return the {@link TopicPath} assigned to this message
     */
    Optional<TopicPath> getTopicPath();

    /**
     * @return the required data to apply the enforcement (if enforcement is enabled), empty otherwise
     */
    Optional<EnforcementFilter<Signal<?>>> getEnforcementFilter();

    /**
     * @return the optional header mapping
     */
    Optional<HeaderMapping> getHeaderMapping();

    /**
     * @return the payload mapping that is applied for this message
     */
    Optional<PayloadMapping> getPayloadMapping();

    /**
     * @return optional source address, where this message was received
     */
    Optional<String> getSourceAddress();

    /**
     * @return optional source, where this message was received
     * @since 1.2.0
     */
    Optional<Source> getSource();

    /**
     * @return Ditto headers of the signal that created this external message if any.
     * Use those headers when sending error back into the Ditto cluster.
     */
    DittoHeaders getInternalHeaders();

    /**
     * The known payload types of ExternalMessages.
     */
    enum PayloadType {
        TEXT,
        BYTES,
        TEXT_AND_BYTES,
        UNKNOWN
    }
}
