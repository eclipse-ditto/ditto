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
package org.eclipse.ditto.json;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable representation of a JSON number, which is known to fit in Java {@code long}.
 */
@Immutable
final class ImmutableJsonLong extends AbstractJsonNumber<Long> {

    private ImmutableJsonLong(final long value) {
        super(value);
    }

    /**
     * Returns an instance of {@code ImmutableJsonLong}.
     *
     * @return the instance.
     */
    public static ImmutableJsonLong of(final long value) {
        return new ImmutableJsonLong(value);
    }

    @Override
    public boolean isInt() {
        final Long value = getValue();
        return value.intValue() == value;
    }

    @Override
    public boolean isLong() {
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AbstractJsonNumber) {
            final AbstractJsonNumber<?> that = (AbstractJsonNumber<?>) o;
            if (that.isLong()) {
                return Objects.equals(getValue(), that.asLong());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        final Long value = getValue();
        if (isInt()) {
            return value.intValue();
        }
        return value.hashCode();
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        serializationContext.writeNumber(getValue());
    }

    @Override
    public long getUpperBoundForStringSize() {
        return 21; // 20 digits for the decimal representation of 2^64 plus a potential '-' character
    }
}
