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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.things.model.ThingId;

/**
 * An Immutable implementation of {@link Message}.
 *
 * @param <T> the type of the message's payload.
 */
@Immutable
final class ImmutableMessage<T> implements Message<T> {

    private final MessageHeaders headers;
    @Nullable private final ByteBuffer rawPayload;
    @Nullable private final T payload;
    @Nullable private final JsonObject extra;

    private ImmutableMessage(final MessageHeaders headers,
            @Nullable final ByteBuffer rawPayload,
            @Nullable final T payload,
            @Nullable final JsonObject extra) {

        this.headers = checkNotNull(headers, "headers");
        this.rawPayload = rawPayload != null ? ByteBufferUtils.clone(rawPayload) : null;
        this.payload = payload;
        this.extra = extra;
    }

    /**
     * Creates a new Message.
     *
     * @param <T> the type of the payload.
     * @param headers the headers of this message containing defined headers as well as custom headers.
     * @param rawPayload the raw payload of the message as provided by the message sender (maybe {@code null} if the
     * sender has provided no payload)
     * @param payload the payload of the message as provided by the message sender (maybe {@code null} if the sender has
     * provided no payload)
     * @param extra the extra (enriched) data of the message.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public static <T> Message<T> of(final MessageHeaders headers,
            @Nullable final ByteBuffer rawPayload,
            @Nullable final T payload,
            @Nullable final JsonObject extra) {

        return new ImmutableMessage<>(headers, rawPayload, payload, extra);
    }

    @Override
    public MessageHeaders getHeaders() {
        return headers;
    }

    @Override
    public Optional<T> getPayload() {
        return Optional.ofNullable(payload);
    }

    @Override
    public Optional<ByteBuffer> getRawPayload() {
        if (null != rawPayload) {
            return Optional.of(ByteBufferUtils.clone(rawPayload));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JsonObject> getExtra() {
        return Optional.ofNullable(extra);
    }

    @Override
    public MessageDirection getDirection() {
        return headers.getDirection();
    }

    @Override
    public ThingId getEntityId() {
        return headers.getEntityId();
    }

    @Override
    public String getSubject() {
        return headers.getSubject();
    }

    @Override
    public Optional<String> getFeatureId() {
        return headers.getFeatureId();
    }

    @Override
    public Optional<String> getContentType() {
        return headers.getContentType();
    }

    @Override
    public Optional<Duration> getTimeout() {
        return headers.getTimeout();
    }

    @Override
    public Optional<OffsetDateTime> getTimestamp() {
        return headers.getTimestamp();
    }

    @Override
    public Optional<String> getCorrelationId() {
        return headers.getCorrelationId();
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return headers.getAuthorizationContext();
    }

    @Override
    public Optional<HttpStatus> getHttpStatus() {
        return headers.getHttpStatus();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMessage<?> that = (ImmutableMessage<?>) o;
        return Objects.equals(rawPayload, that.rawPayload) &&
                Objects.equals(payload, that.payload) &&
                Objects.equals(extra, that.extra) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawPayload, payload, extra, headers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "rawPayload=" + rawPayload +
                ", payload=" + payload +
                ", extra=" + extra +
                ", headers=" + headers +
                "]";
    }

}
