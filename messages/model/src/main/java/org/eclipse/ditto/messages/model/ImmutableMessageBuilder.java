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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;

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
    @Nullable private JsonObject extra;

    private ImmutableMessageBuilder(final MessageHeaders theHeaders) {
        headers = theHeaders;
        rawPayload = null;
        payload = null;
        extra = null;
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
    public MessageBuilder<T> extra(@Nullable final JsonObject extra) {
        this.extra = extra;
        return this;
    }

    @Override
    public Message<T> build() {
        return ImmutableMessage.of(headers, rawPayload, payload, extra);
    }

}
