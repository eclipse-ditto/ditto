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
package org.eclipse.ditto.messages.model;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.things.model.ThingId;

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
     * Returns the extra data included via signal enrichment.
     *
     * @return the extra data.
     */
    Optional<JsonObject> getExtra();

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
    ThingId getEntityId();

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
     * Returns the HTTP status of this message.
     *
     * @return the status.
     * @since 2.0.0
     */
    Optional<HttpStatus> getHttpStatus();

}
