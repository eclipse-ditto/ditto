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
package org.eclipse.ditto.model.amqpbridge;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;


/**
 * Simple wrapper around the headers and the payload received from external AMQP source.
 */
public final class InternalMessage {

    public enum MessageType {
        COMMAND,
        EVENT,
        RESPONSE
    }

    private enum PayloadType {
        TEXT,
        BYTES,
        UNKNOWN
    }

    private final Map<String, String> headers;
    private final MessageType messageType;
    private final PayloadType payloadType;

    @Nullable
    private final String textPayload;
    @Nullable
    private final ByteBuffer bytePayload;

    private InternalMessage(final Builder builder) {
        this.headers = builder.headers;
        this.textPayload = builder.textPayload;
        this.bytePayload = builder.bytePayload;
        this.payloadType = builder.payloadType;
        this.messageType = builder.messageType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @param key the header key
     * @param value the header value
     * @return new instance of {@link InternalMessage} including the provided header
     */
    public InternalMessage withHeader(final String key, final String value) {
        return new Builder(this).withAdditionalHeaders(key, value).build();
    }

    /**
     * @param additionalHeaders headers added to message headers
     * @return new instance of {@link InternalMessage} including the provided headers
     */
    public InternalMessage withHeader(final Map<String, String> additionalHeaders) {
        return new Builder(this).withAdditionalHeaders(additionalHeaders).build();
    }

    public Optional<String> findHeader(final String key) {
        return Optional.ofNullable(headers.get(key)).filter(s -> !s.isEmpty());
    }

    public Optional<String> findHeaderIgnoreCase(final String key) {
        return headers.entrySet().stream().filter(e -> key.equalsIgnoreCase(e.getKey())).findFirst()
                .map(Map.Entry::getValue);
    }

    public boolean isTextMessage() {
        return PayloadType.TEXT.equals(payloadType);
    }

    public boolean isBytesMessage() {
        return PayloadType.BYTES.equals(payloadType);
    }

    public Optional<String> getTextPayload() {
        return Optional.ofNullable(textPayload);
    }

    public Optional<ByteBuffer> getBytePayload() {
        return Optional.ofNullable(bytePayload);
    }

    public MessageType getMessageType() {
        return messageType;
    }
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final InternalMessage that = (InternalMessage) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(textPayload, that.textPayload) &&
                Objects.equals(bytePayload, that.bytePayload) &&
                payloadType == that.payloadType &&
                messageType == that.messageType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, textPayload, bytePayload, payloadType, messageType);
    }

    public static class Builder {

        private final Map<String, String> headers;
        private final MessageType messageType;
        private PayloadType payloadType = PayloadType.UNKNOWN;
        @Nullable
        private String textPayload;
        @Nullable
        private ByteBuffer bytePayload;

        public static Builder newCommand(final Map<String, String> headers) {
            return new Builder(headers, MessageType.COMMAND);
        }

        public static Builder newEvent(final Map<String, String> headers) {
            return new Builder(headers, MessageType.EVENT);
        }

        public static Builder newResponse(final Map<String, String> headers) {
            return new Builder(headers, MessageType.RESPONSE);
        }

        public static Builder from(final InternalMessage message) {
            return new Builder(message);
        }

        public static Builder from(final Map<String, String> headers, final MessageType messageType) {
            return new Builder(headers, messageType);
        }

        private Builder(final InternalMessage message) {
            this.headers = new HashMap<>(message.headers);
            this.bytePayload = message.bytePayload;
            this.textPayload = message.textPayload;
            this.messageType = message.messageType;
        }

        private Builder(final Map<String, String> headers, final MessageType messageType) {
            this.headers = headers;
            this.messageType = messageType;
        }


        public Builder withAdditionalHeaders(final String key, final String value) {
            headers.put(key, value);
            return this;
        }

        public Builder withAdditionalHeaders(final Map<String, String> additionalHeaders) {
            headers.putAll(additionalHeaders);
            return this;
        }

        public Builder withText(@Nullable final String text) {
            this.payloadType = PayloadType.TEXT;
            this.textPayload = text;
            this.bytePayload = null;
            return this;
        }

        public Builder withBytes(@Nullable final byte[] bytes) {
            if (Objects.isNull(bytes)) {
                withBytes((ByteBuffer) null);
            } else {
                withBytes(ByteBuffer.wrap(bytes));
            }
            return this;
        }

        public Builder withBytes(@Nullable final ByteBuffer bytes) {
            this.payloadType = PayloadType.BYTES;
            this.bytePayload = bytes;
            this.textPayload = null;
            return this;
        }

        public InternalMessage build() {
            return new InternalMessage(this);
        }

    }

    @Override
    public String toString() {
        return "InternalMessage{" +
                "headers=" + headers +
                ", textPayload='" + textPayload + '\'' +
                ", bytePayload='" +
                (bytePayload == null ? "null" : ("'<binary> (size :" + bytePayload.position() + ")")) + "'" +
                ", payloadType=" + payloadType +
                ", messageType=" + messageType +
                '}';
    }
}
