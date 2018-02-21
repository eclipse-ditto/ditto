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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * TODO doc
 */
@Deprecated
final class ImmutablePayloadMapperMessage implements PayloadMapperMessage {

    @Nullable private final String contentType;
    @Nullable private final ByteBuffer rawData;
    @Nullable private final String stringData;
    private final Map<String, String> headers;

    /**
     *
     * @param contentType
     * @param rawData
     * @param stringData
     * @param headers
     */
    ImmutablePayloadMapperMessage(@Nullable final String contentType, @Nullable final ByteBuffer rawData,
            @Nullable final String stringData, final Map<String, String> headers) {
        this.contentType = contentType;
        this.rawData = rawData;
        this.stringData = stringData;
        this.headers = headers;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public Optional<ByteBuffer> getRawData() {
        return Optional.ofNullable(rawData);
    }

    @Override
    public Optional<String> getStringData() {
        return Optional.ofNullable(stringData);
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutablePayloadMapperMessage)) {
            return false;
        }
        final ImmutablePayloadMapperMessage that = (ImmutablePayloadMapperMessage) o;
        return Objects.equals(contentType, that.contentType) &&
                Objects.equals(rawData, that.rawData) &&
                Objects.equals(stringData, that.stringData) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, rawData, stringData, headers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "contentType=" + contentType +
                ", rawData=" + rawData +
                ", stringData=" + stringData +
                ", headers=" + headers +
                "]";
    }
}
