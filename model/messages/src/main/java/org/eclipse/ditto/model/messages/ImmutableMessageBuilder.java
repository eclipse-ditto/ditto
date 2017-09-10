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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder with a fluent API for an immutable {@link Message}.
 *
 * @param <T> the type of the payload of the Messages this builder builds.
 */
@NotThreadSafe
final class ImmutableMessageBuilder<T> implements MessageBuilder<T> {

    private final MessageHeaders headers;
    @Nullable private ByteBuffer rawPayload;
    @Nullable private T payload;
    @Nullable private MessageResponseConsumer<?> responseConsumer;

    private ImmutableMessageBuilder(final MessageHeaders theHeaders) {
        headers = theHeaders;
        rawPayload = null;
        payload = null;
        responseConsumer = null;
    }

    /**
     * Returns a new instance of {@code ImmutableMessageBuilder}.
     *
     * @param messageHeaders the headers of the message to be built.
     * @param <T> the type of the payload of the Messages this builder builds.
     * @return the new builder.
     * @throws NullPointerException if {@code messageHeaders} is {@code null}.
     */
    static <T> MessageBuilder<T> newInstance(final MessageHeaders messageHeaders) {
        return new ImmutableMessageBuilder<>(checkNotNull(messageHeaders, "message headers"));
    }

    @Override
    public MessageBuilder<T> payload(@Nullable final T payload) {
        this.payload = payload;
        return this;
    }

    @Override
    public MessageBuilder<T> rawPayload(@Nullable final ByteBuffer rawPayload) {
        this.rawPayload = rawPayload;
        return this;
    }

    @Override
    public MessageBuilder<T> responseConsumer(@Nullable final MessageResponseConsumer<?> responseConsumer) {
        this.responseConsumer = responseConsumer;
        return this;
    }

    @Override
    public Message<T> build() {
        return ImmutableMessage.of(headers, rawPayload, payload, responseConsumer);
    }

}
