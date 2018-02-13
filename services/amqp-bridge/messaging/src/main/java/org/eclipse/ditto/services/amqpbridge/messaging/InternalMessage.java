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
package org.eclipse.ditto.services.amqpbridge.messaging;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperMessage;


/**
 * Simple wrapper around the headers and the payload received from external AMQP source.
 * An instance of this message can be forwarded to the {@link CommandProcessorActor} for further processing.
 */
public class InternalMessage {

    private final Map<String, String> headers;
    private final String textPayload;
    private final ByteBuffer bytePayload;

    private InternalMessage(final Builder builder) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.textPayload = builder.textPayload;
        this.bytePayload = builder.bytePayload;
    }

    private PayloadMapperMessage toPayloadMapperMessage() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Optional<String> getTextPayload() {
        return Optional.ofNullable(textPayload);
    }

    public Optional<ByteBuffer> getBytePayload() {
        return Optional.ofNullable(bytePayload);
    }

    public static class Builder {

        private Map<String, String> headers;
        private String textPayload;
        private ByteBuffer bytePayload;

        public Builder(final Map<String, String> headers) {
            this.headers = headers;
        }

        public Builder withText(final String text) {
            this.textPayload = text;
            return this;
        }

        public Builder withBytes(final byte[] bytes) {
            this.bytePayload = ByteBuffer.wrap(bytes);
            return this;
        }

        public Builder withBytes(final ByteBuffer bytes) {
            this.bytePayload = bytes;
            return this;
        }

        public InternalMessage build() {
            return new InternalMessage(this);
        }

    }
}
