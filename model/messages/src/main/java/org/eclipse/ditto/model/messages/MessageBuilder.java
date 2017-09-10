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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder with a fluent API for an immutable {@link Message}.
 *
 * @param <T> the type of the Message's payload
 */
@NotThreadSafe
public interface MessageBuilder<T> {

    /**
     * Returns a new builder for {@link MessageHeaders}.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     */
    static MessageHeadersBuilder newHeadersBuilder(final MessageDirection direction, final CharSequence thingId,
            final CharSequence subject) {

        return MessagesModelFactory.newHeadersBuilder(direction, thingId, subject);
    }

    /**
     * Sets the payload of the Message.
     *
     * @param payload the payload of the Message.
     * @return this builder to allow method chaining.
     * @throws UnsupportedOperationException if the parameter was already predefined for the Message to build.
     */
    MessageBuilder<T> payload(@Nullable T payload);

    /**
     * Sets the raw payload of the Message.
     *
     * @param rawPayload the raw payload of the Message.
     * @return this builder to allow method chaining.
     * @throws UnsupportedOperationException if the parameter was already predefined for the Message to build.
     */
    MessageBuilder<T> rawPayload(@Nullable ByteBuffer rawPayload);

    /**
     * Adds a {@code responseConsumer} which is invoked with a potential response Message to the built Message.
     *
     * @param responseConsumer Consumer to invoke for a potential response Message.
     * @return this builder to allow method chaining.
     */
    MessageBuilder<T> responseConsumer(@Nullable MessageResponseConsumer<?> responseConsumer);

    /**
     * Creates a new immutable {@link Message} containing all properties which were set to this builder beforehand.
     *
     * @return the new Message.
     */
    Message<T> build();

}
