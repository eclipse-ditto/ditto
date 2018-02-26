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
package org.eclipse.ditto.model.amqpbridge;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Mutable builder for building new instances of ExternalMessage.
 */
final class MutableExternalMessageBuilder implements ExternalMessageBuilder {

    private final Map<String, String> headers;
    private final ExternalMessage.MessageType messageType;
    private ExternalMessage.PayloadType payloadType = ExternalMessage.PayloadType.UNKNOWN;
    @Nullable
    private String textPayload;
    @Nullable
    private ByteBuffer bytePayload;

    /**
     * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code message}.
     *
     * @param message the ExternalMessage to use for initialization.
     */
    MutableExternalMessageBuilder(final ExternalMessage message) {
        this.headers = new HashMap<>(message.getHeaders());
        this.bytePayload = message.getBytePayload().orElse(null);
        this.textPayload = message.getTextPayload().orElse(null);
        this.messageType = message.getMessageType();
        this.payloadType = message.getPayloadType();
    }

    /**
     * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code headers} and {@code messageType}.
     *
     * @param headers the headers to use for initialization.
     * @param messageType the messageType to use for initialization.
     */
    MutableExternalMessageBuilder(final Map<String, String> headers, final ExternalMessage.MessageType messageType) {
        this.headers = headers;
        this.messageType = messageType;
    }

    @Override
    public ExternalMessageBuilder withAdditionalHeaders(final String key, final String value) {
        headers.put(key, value);
        return this;
    }

    @Override
    public ExternalMessageBuilder withAdditionalHeaders(final Map<String, String> additionalHeaders) {
        headers.putAll(additionalHeaders);
        return this;
    }

    @Override
    public ExternalMessageBuilder withText(@Nullable final String text) {
        this.payloadType = ExternalMessage.PayloadType.TEXT;
        this.textPayload = text;
        this.bytePayload = null;
        return this;
    }

    @Override
    public ExternalMessageBuilder withBytes(@Nullable final byte[] bytes) {
        if (Objects.isNull(bytes)) {
            return withBytes((ByteBuffer) null);
        } else {
            return withBytes(ByteBuffer.wrap(bytes));
        }
    }

    @Override
    public ExternalMessageBuilder withBytes(@Nullable final ByteBuffer bytes) {
        this.payloadType = ExternalMessage.PayloadType.BYTES;
        this.bytePayload = bytes;
        this.textPayload = null;
        return this;
    }

    @Override
    public ExternalMessage build() {
        return new ImmutableExternalMessage(headers, messageType, payloadType, textPayload, bytePayload);
    }

}
