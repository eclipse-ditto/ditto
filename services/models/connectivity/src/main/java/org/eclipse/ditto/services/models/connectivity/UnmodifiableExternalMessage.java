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
package org.eclipse.ditto.services.models.connectivity;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.placeholders.EnforcementFilter;
import org.eclipse.ditto.protocoladapter.TopicPath;

/**
 * Implementation of {@link ExternalMessage} that SHOULD NOT be modified
 * because objects of this class are sent as messages between actors.
 */
final class UnmodifiableExternalMessage implements ExternalMessage {

    private final Map<String, String> headers;
    private final boolean response;
    private final boolean error;
    private final PayloadType payloadType;
    private final List<String> payloadMapping;

    @Nullable private final String textPayload;
    @Nullable private final ByteBuffer bytePayload;
    @Nullable private final AuthorizationContext authorizationContext;
    @Nullable private final TopicPath topicPath;
    @Nullable private final EnforcementFilter<CharSequence> enforcementFilter;
    @Nullable private final HeaderMapping headerMapping;
    @Nullable private final String sourceAddress;

    private final DittoHeaders internalHeaders;

    UnmodifiableExternalMessage(final Map<String, String> headers,
            final boolean response,
            final boolean error,
            final PayloadType payloadType,
            @Nullable final String textPayload,
            @Nullable final ByteBuffer bytePayload,
            @Nullable final AuthorizationContext authorizationContext,
            @Nullable final TopicPath topicPath,
            @Nullable final EnforcementFilter<CharSequence> enforcementFilter,
            @Nullable final HeaderMapping headerMapping,
            @Nullable final List<String> payloadMapping,
            @Nullable final String sourceAddress,
            final DittoHeaders internalHeaders) {

        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.response = response;
        this.error = error;
        this.payloadType = payloadType;
        this.textPayload = textPayload;
        this.bytePayload = bytePayload;
        this.authorizationContext = authorizationContext;
        this.topicPath = topicPath;
        this.enforcementFilter = enforcementFilter;
        this.headerMapping = headerMapping;
        this.payloadMapping = payloadMapping;
        this.sourceAddress = sourceAddress;
        this.internalHeaders = internalHeaders;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public ExternalMessage withHeader(final String key, final String value) {
        return new UnmodifiableExternalMessageBuilder(this).withAdditionalHeaders(key, value).build();
    }

    @Override
    public ExternalMessage withHeaders(final Map<String, String> additionalHeaders) {
        return new UnmodifiableExternalMessageBuilder(this)
                .withAdditionalHeaders(additionalHeaders)
                .build();
    }

    @Override
    public ExternalMessage withTopicPath(final TopicPath topicPath) {
        return new UnmodifiableExternalMessageBuilder(this).withTopicPath(topicPath).build();
    }

    @Override
    public Optional<String> findHeader(final String key) {
        return Optional.ofNullable(headers.get(key)).filter(s -> !s.isEmpty());
    }

    @Override
    public Optional<String> findHeaderIgnoreCase(final String key) {
        return headers.entrySet().stream().filter(e -> key.equalsIgnoreCase(e.getKey())).findFirst()
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
    public Optional<EnforcementFilter<CharSequence>> getEnforcementFilter() {
        return Optional.ofNullable(enforcementFilter);
    }

    @Override
    public Optional<HeaderMapping> getHeaderMapping() {
        return Optional.ofNullable(headerMapping);
    }

    @Override
    public List<String> getPayloadMapping() {
        return payloadMapping;
    }

    @Override
    public Optional<String> getSourceAddress() {
        return Optional.ofNullable(sourceAddress);
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
        final UnmodifiableExternalMessage that = (UnmodifiableExternalMessage) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(textPayload, that.textPayload) &&
                Objects.equals(bytePayload, that.bytePayload) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(enforcementFilter, that.enforcementFilter) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                Objects.equals(payloadMapping, that.payloadMapping) &&
                Objects.equals(sourceAddress, that.sourceAddress) &&
                Objects.equals(response, that.response) &&
                Objects.equals(error, that.error) &&
                payloadType == that.payloadType &&
                Objects.equals(internalHeaders, that.internalHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, textPayload, bytePayload, payloadType, response, error, authorizationContext,
                topicPath, enforcementFilter, headerMapping, payloadMapping, sourceAddress, internalHeaders);
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
                ", payloadType=" + payloadType +
                ", textPayload=" + textPayload +
                ", bytePayload=" +
                (bytePayload == null ? "null" : ("<binary> (" + bytePayload.remaining() + "bytes)")) + "'" +
                ", internalHeaders=" + internalHeaders +
                "]";
    }
}
