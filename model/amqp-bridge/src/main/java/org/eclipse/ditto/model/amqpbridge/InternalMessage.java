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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;


/**
 * Simple wrapper around the headers and the payload received from external AMQP source.
 */
public class InternalMessage {

    private enum Type {
        TEXT,
        BYTES,
        UNKNOWN
    }

    private final Map<String, String> headers;
    private final Type type;

    @Nullable
    private final String textPayload;
    @Nullable
    private final ByteBuffer bytePayload;

    private InternalMessage(final Builder builder) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.type = builder.type;
        this.textPayload = builder.textPayload;
        this.bytePayload = builder.bytePayload;

    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Optional<String> findHeader(final String key) {
        return Optional.ofNullable(headers.get(key)).filter(s -> !s.isEmpty());
    }

    public Optional<String> findHeaderIgnoreCase(final String key) {
        return headers.entrySet().stream().filter(e -> key.equalsIgnoreCase(e.getKey())).findFirst()
                .map(Map.Entry::getValue);
    }

    public boolean isTextMessage() {
        return Type.TEXT.equals(type);
    }

    public boolean isBytesMessage() {
        return Type.BYTES.equals(type);
    }

    public Optional<String> getTextPayload() {
        return Optional.ofNullable(textPayload);
    }

    public Optional<ByteBuffer> getBytePayload() {
        return Optional.ofNullable(bytePayload);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final InternalMessage that = (InternalMessage) o;
        return Objects.equals(headers, that.headers) &&
                Objects.equals(textPayload, that.textPayload) &&
                Objects.equals(bytePayload, that.bytePayload) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, textPayload, bytePayload, type);
    }



    public static class Builder {

        private final Map<String, String> headers;
        private Type type = Type.UNKNOWN;
        @Nullable
        private String textPayload;
        @Nullable
        private ByteBuffer bytePayload;

        public Builder(final Map<String, String> headers) {
            this.headers = headers;
        }

        public Builder withText(@Nullable final String text) {
            this.type = Type.TEXT;
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
            this.type = Type.BYTES;
            this.bytePayload = bytes;
            this.textPayload = null;
            return this;
        }

        public InternalMessage build() {
            return new InternalMessage(this);
        }

    }
}
