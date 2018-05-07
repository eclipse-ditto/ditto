/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link ExternalMessage}.
 */
@Immutable
final class ImmutableExternalMessage implements ExternalMessage {

    private final Map<String, String> headers;
    @Nullable private final String topicPath;
    private final boolean response;
    private final PayloadType payloadType;

    @Nullable private final String textPayload;
    @Nullable private final ByteBuffer bytePayload;

    ImmutableExternalMessage(final Map<String, String> headers,
            @Nullable final String topicPath,
            final boolean response,
            final PayloadType payloadType,
            @Nullable final String textPayload,
            @Nullable final ByteBuffer bytePayload) {

        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.topicPath = topicPath;
        this.response = response;
        this.payloadType = payloadType;
        this.textPayload = textPayload;
        this.bytePayload = bytePayload;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public ExternalMessage withHeader(final String key, final String value) {
        return ConnectivityModelFactory.newExternalMessageBuilder(this).withAdditionalHeaders(key, value).build();
    }

    @Override
    public ExternalMessage withHeaders(final Map<String, String> additionalHeaders) {
        return ConnectivityModelFactory.newExternalMessageBuilder(this).withAdditionalHeaders(additionalHeaders).build();
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
        return PayloadType.TEXT.equals(payloadType);
    }

    @Override
    public boolean isBytesMessage() {
        return PayloadType.BYTES.equals(payloadType);
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
    public Optional<String> getTopicPath() {
        return Optional.ofNullable(topicPath);
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableExternalMessage that = (ImmutableExternalMessage) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(textPayload, that.textPayload) &&
                Objects.equals(bytePayload, that.bytePayload) &&
                Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(response, that.response) &&
                payloadType == that.payloadType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, textPayload, bytePayload, payloadType, response, topicPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "headers=" + headers +
                ", topicPath=" + topicPath +
                ", response=" + response +
                ", payloadType=" + payloadType +
                ", textPayload=" + textPayload +
                ", bytePayload=" +
                (bytePayload == null ? "null" : ("<binary> (size :" + bytePayload.array().length + ")")) + "'" +
                "]";
    }
}
