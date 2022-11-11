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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Implementation of {@link ExternalMessage} that SHOULD NOT be modified
 * because objects of this class are sent as messages between actors.
 */
final class UnmodifiableExternalMessage implements ExternalMessage {

    private final Map<String, String> headers;
    private final boolean response;
    private final boolean error;
    private final PayloadType payloadType;

    @Nullable private final PayloadMapping payloadMapping;
    @Nullable private final String textPayload;
    @Nullable private final ByteBuffer bytePayload;
    @Nullable private final AuthorizationContext authorizationContext;
    @Nullable private final TopicPath topicPath;
    @Nullable private final EnforcementFilter<Signal<?>> enforcementFilter;
    @Nullable private final HeaderMapping headerMapping;
    @Nullable private final String sourceAddress;
    @Nullable private final Source source;

    private final DittoHeaders internalHeaders;

    private UnmodifiableExternalMessage(final Builder builder) {
        headers = Map.copyOf(builder.headers);
        response = builder.response;
        error = builder.error;
        payloadType = builder.payloadType;
        textPayload = builder.textPayload;
        bytePayload = builder.bytePayload;
        authorizationContext = builder.authorizationContext;
        topicPath = builder.topicPath;
        enforcementFilter = builder.enforcementFilter;
        headerMapping = builder.headerMapping;
        payloadMapping = builder.payloadMapping;
        sourceAddress = builder.sourceAddress;
        source = builder.source;
        internalHeaders = builder.internalHeaders;
    }

    static ExternalMessageBuilder newBuilder(final Map<String, String> headers) {
        return new Builder(headers);
    }

    static ExternalMessageBuilder newBuilder(final ExternalMessage externalMessage) {
        return new Builder(externalMessage);
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public ExternalMessage withHeader(final String key, final String value) {
        return newBuilder(this).withAdditionalHeaders(key, value).build();
    }

    @Override
    public ExternalMessage withHeaders(final Map<String, String> additionalHeaders) {
        return newBuilder(this)
                .withAdditionalHeaders(additionalHeaders)
                .build();
    }

    @Override
    public ExternalMessage withTopicPath(final TopicPath topicPath) {
        return newBuilder(this).withTopicPath(topicPath).build();
    }

    @Override
    public Optional<String> findHeader(final String key) {
        return Optional.ofNullable(headers.get(key)).filter(s -> !s.isEmpty());
    }

    @Override
    public Optional<String> findHeaderIgnoreCase(final String key) {
        return headers.entrySet()
                .stream()
                .filter(e -> key.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    @Override
    public boolean isTextMessage() {
        return PayloadType.TEXT.equals(payloadType) || PayloadType.TEXT_AND_BYTES.equals(payloadType);
    }

    @Override
    public boolean isBytesMessage() {
        return PayloadType.BYTES.equals(payloadType) || PayloadType.TEXT_AND_BYTES.equals(payloadType);
    }

    @Override
    public Optional<String> getTextPayload() {
        return Optional.ofNullable(textPayload);
    }

    @Override
    public Optional<ByteBuffer> getBytePayload() {
        return Optional.ofNullable(bytePayload);
    }

    @Override
    public PayloadType getPayloadType() {
        return payloadType;
    }

    @Override
    public boolean isResponse() {
        return response;
    }

    @Override
    public boolean isError() {
        return error;
    }

    @Override
    public Optional<AuthorizationContext> getAuthorizationContext() {
        return Optional.ofNullable(authorizationContext);
    }

    @Override
    public Optional<TopicPath> getTopicPath() {
        return Optional.ofNullable(topicPath);
    }

    @Override
    public Optional<EnforcementFilter<Signal<?>>> getEnforcementFilter() {
        return Optional.ofNullable(enforcementFilter);
    }

    @Override
    public Optional<HeaderMapping> getHeaderMapping() {
        return Optional.ofNullable(headerMapping);
    }

    @Override
    public Optional<PayloadMapping> getPayloadMapping() {
        return Optional.ofNullable(payloadMapping);
    }

    @Override
    public Optional<String> getSourceAddress() {
        return Optional.ofNullable(sourceAddress);
    }

    @Override
    public Optional<Source> getSource() {
        return Optional.ofNullable(source);
    }

    @Override
    public DittoHeaders getInternalHeaders() {
        return internalHeaders;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (UnmodifiableExternalMessage) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(textPayload, that.textPayload) &&
                Objects.equals(bytePayload, that.bytePayload) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(enforcementFilter, that.enforcementFilter) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                Objects.equals(payloadMapping, that.payloadMapping) &&
                Objects.equals(sourceAddress, that.sourceAddress) &&
                Objects.equals(source, that.source) &&
                Objects.equals(response, that.response) &&
                Objects.equals(error, that.error) &&
                payloadType == that.payloadType &&
                Objects.equals(internalHeaders, that.internalHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, textPayload, bytePayload, payloadType, response, error, authorizationContext,
                topicPath, enforcementFilter, headerMapping, payloadMapping, sourceAddress, internalHeaders, source);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "headers=" + headers +
                ", response=" + response +
                ", error=" + error +
                ", authorizationContext=" + authorizationContext +
                ", topicPath=" + topicPath +
                ", enforcement=" + enforcementFilter +
                ", headerMapping=" + headerMapping +
                ", payloadMapping=" + payloadMapping +
                ", sourceAddress=" + sourceAddress +
                ", source=" + source +
                ", payloadType=" + payloadType +
                ", textPayload=" + textPayload +
                ", bytePayload=" +
                (bytePayload == null ? "null" : "<binary> (" + bytePayload.remaining() + "bytes)") + "'" +
                ", internalHeaders=" + internalHeaders +
                "]";
    }

    @NotThreadSafe
    private static final class Builder implements ExternalMessageBuilder {

        private Map<String, String> headers;
        private boolean response;
        private boolean error;
        private ExternalMessage.PayloadType payloadType;
        @Nullable private String textPayload;
        @Nullable private ByteBuffer bytePayload;
        @Nullable private AuthorizationContext authorizationContext;
        @Nullable private TopicPath topicPath;
        @Nullable private EnforcementFilter<Signal<?>> enforcementFilter;
        @Nullable private HeaderMapping headerMapping;
        @Nullable private PayloadMapping payloadMapping;
        @Nullable private String sourceAddress;
        @Nullable private Source source;
        private DittoHeaders internalHeaders;

        /**
         * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code message}.
         *
         * @param message the ExternalMessage to use for initialization.
         */
        Builder(final ExternalMessage message) {
            checkNotNull(message, "message");
            headers = new HashMap<>(message.getHeaders());
            bytePayload = message.getBytePayload().orElse(null);
            textPayload = message.getTextPayload().orElse(null);
            payloadType = message.getPayloadType();
            response = message.isResponse();
            error = message.isError();
            authorizationContext = message.getAuthorizationContext().orElse(null);
            topicPath = message.getTopicPath().orElse(null);
            enforcementFilter = message.getEnforcementFilter().orElse(null);
            headerMapping = message.getHeaderMapping().orElse(null);
            payloadMapping = message.getPayloadMapping().orElse(null);
            sourceAddress = message.getSourceAddress().orElse(null);
            source = message.getSource().orElse(null);
            internalHeaders = message.getInternalHeaders();
        }

        /**
         * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code headers}.
         *
         * @param headers the headers to use for initialization.
         */
        Builder(final Map<String, String> headers) {
            this.headers = new HashMap<>(checkNotNull(headers, "headers"));
            response = false;
            error = false;
            payloadType = PayloadType.UNKNOWN;
            textPayload = null;
            bytePayload = null;
            authorizationContext = null;
            topicPath = null;
            enforcementFilter = null;
            headerMapping = null;
            payloadMapping = null;
            sourceAddress = null;
            source = null;
            internalHeaders = DittoHeaders.empty();
        }

        @Override
        public Builder withAdditionalHeaders(final String key, final String value) {
            headers.put(key, value);
            return this;
        }

        @Override
        public Builder withAdditionalHeaders(final Map<String, String> additionalHeaders) {
            headers.putAll(checkNotNull(additionalHeaders, "additionalHeaders"));
            return this;
        }

        @Override
        public Builder withHeaders(final Map<String, String> headers) {
            this.headers = new HashMap<>(checkNotNull(headers));
            return this;
        }

        @Override
        public Builder withText(@Nullable final String text) {
            payloadType = ExternalMessage.PayloadType.TEXT;
            textPayload = text;
            bytePayload = null;
            return this;
        }

        @Override
        public Builder withBytes(@Nullable final byte[] bytes) {
            if (null == bytes) {
                return withBytes((ByteBuffer) null);
            } else {
                return withBytes(ByteBuffer.wrap(bytes));
            }
        }

        @Override
        public Builder withBytes(@Nullable final ByteBuffer bytes) {
            payloadType = ExternalMessage.PayloadType.BYTES;
            bytePayload = bytes;
            textPayload = null;
            return this;
        }

        @Override
        public Builder withTextAndBytes(@Nullable final String text, @Nullable final byte[] bytes) {
            payloadType = ExternalMessage.PayloadType.TEXT_AND_BYTES;
            textPayload = text;
            if (Objects.isNull(bytes)) {
                bytePayload = null;
            } else {
                bytePayload = ByteBuffer.wrap(bytes);
            }
            return this;
        }

        @Override
        public Builder withTextAndBytes(@Nullable final String text, @Nullable final ByteBuffer bytes) {
            payloadType = ExternalMessage.PayloadType.TEXT_AND_BYTES;
            textPayload = text;
            bytePayload = bytes;
            return this;
        }

        @Override
        public Builder withAuthorizationContext(final AuthorizationContext authorizationContext) {
            this.authorizationContext = authorizationContext;
            return this;
        }

        @Override
        public Builder withTopicPath(final TopicPath topicPath) {
            this.topicPath = topicPath;
            return this;
        }

        @Override
        public <F extends EnforcementFilter<Signal<?>>> Builder withEnforcement(
                @Nullable final F enforcementFilter
        ) {
            this.enforcementFilter = enforcementFilter;
            return this;
        }

        @Override
        public Builder withHeaderMapping(@Nullable final HeaderMapping headerMapping) {
            this.headerMapping = headerMapping;
            return this;
        }

        @Override
        public Builder withPayloadMapping(final PayloadMapping payloadMapping) {
            this.payloadMapping = payloadMapping;
            return this;
        }

        @Override
        public Builder withSourceAddress(@Nullable final String sourceAddress) {
            this.sourceAddress = sourceAddress;
            return this;
        }

        @Override
        public Builder withSource(@Nullable final Source source) {
            this.source = source;
            return this;
        }

        @Override
        public Builder asResponse(final boolean response) {
            this.response = response;
            return this;
        }

        @Override
        public Builder asError(final boolean error) {
            this.error = error;
            return this;
        }

        @Override
        public Builder withInternalHeaders(final DittoHeaders internalHeaders) {
            this.internalHeaders = checkNotNull(internalHeaders, "internalHeaders");
            return this;
        }

        @Override
        public ExternalMessage build() {
            return new UnmodifiableExternalMessage(this);
        }

    }

}
