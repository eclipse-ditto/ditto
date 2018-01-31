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
package org.eclipse.ditto.services.amqpbridge.mapping;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * TODO doc
 */
public final class ImmutablePayloadMapperMessage implements PayloadMapperMessage {


    private final ByteBuffer rawData;
    private final String stringData;
    private final Map<String, String> headers;

    public ImmutablePayloadMapperMessage(@Nullable final ByteBuffer rawData, @Nullable final String stringData,
            final Map<String, String> headers) {
        this.rawData = rawData;
        this.stringData = stringData;
        this.headers = headers;
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutablePayloadMapperMessage)) return false;
        final ImmutablePayloadMapperMessage that = (ImmutablePayloadMapperMessage) o;
        return Objects.equals(rawData, that.rawData) &&
                Objects.equals(stringData, that.stringData) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawData, stringData, headers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "rawData=" + rawData +
                ", stringData=" + stringData +
                ", headers=" + headers +
                "]";
    }
}
