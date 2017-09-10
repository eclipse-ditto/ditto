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
package org.eclipse.ditto.model.messages;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.HttpStatusCode;

/**
 * Represents a {@code Message} <em>FROM</em> or <em>TO</em> a {@code Thing} or a {@code Feature}.
 *
 * @param <T> the type of the message's payload.
 */
public interface Message<T> {

    /**
     * Returns a mutable builder with a fluent API for building an immutable {@code Message}.
     *
     * @param messageHeaders the headers of the message to be built.
     * @param <T> the type of the Message's payload.
     * @return the new builder.
     * @throws NullPointerException if {@code messageHeaders} is {@code null}.
     */
    static <T> MessageBuilder<T> newBuilder(final MessageHeaders messageHeaders) {
        return MessagesModelFactory.newMessageBuilder(messageHeaders);
    }

    /**
     * Returns the headers of this message which includes client specific ones as well as user defined headers for this
     * message.
     *
     * @return the headers of this message.
     */
    MessageHeaders getHeaders();

    /**
     * Returns the payload of the message as provided by the message sender (may be empty if the sender has provided no
     * payload).
     *
     * @return the message payload.
     * @see #getRawPayload()
     */
    Optional<T> getPayload();

    /**
     * Returns the raw payload of the message as provided by the message sender (may be empty if the sender has provided
     * no payload).
     *
     * @return the raw message payload.
     * @see #getPayload()
     */
    Optional<ByteBuffer> getRawPayload();

    /**
     * Returns the MessageResponseConsumer which is invoked with a potential response message or an error.
     *
     * @return the MessageResponseConsumer which is invoked with a potential response message or an error.
     */
    Optional<MessageResponseConsumer<?>> getResponseConsumer();

    /**
     * Returns the direction of the message, specifying if the message has been sent <em>FROM</em> a {@code Thing} (or
     * its {@code Feature}), or <em>TO</em> a {@code Thing} (or its {@code Feature}).
     *
     * @return the message direction.
     */
    MessageDirection getDirection();

    /**
     * Returns the ID of the {@code Thing} from/to which this message is sent.
     *
     * @return the thing ID.
     */
    String getThingId();

    /**
     * Returns the subject of the message as provided by the message sender.
     *
     * @return the message subject.
     */
    String getSubject();

    /**
     * Returns the ID of the {@code Feature} from/to which this message is sent (may be empty if the message is not sent
     * from or addressed to a specific feature).
     *
     * @return the feature ID.
     */
    Optional<String> getFeatureId();

    /**
     * Returns the content-type of the payload as provided by the message sender (may be empty if the message has no
     * payload).
     *
     * @return the content type.
     */
    Optional<String> getContentType();

    /**
     * Returns the timeout of the message.
     *
     * @return the timeout.
     */
    Optional<Duration> getTimeout();

    /**
     * Returns the timestamp of the message.
     *
     * @return the timestamp.
     */
    Optional<OffsetDateTime> getTimestamp();

    /**
     * Returns the correlation ID of the message.
     *
     * @return the correlation ID.
     */
    Optional<String> getCorrelationId();

    /**
     * Returns the authorization context of the message.
     *
     * @return the authorization context of the message.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Returns the status code of the message.
     *
     * @return the status code.
     */
    Optional<HttpStatusCode> getStatusCode();

}
