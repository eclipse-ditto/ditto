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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable representation of a JSON number, which is known to fit in Java {@code int}.
 */
@Immutable
final class ImmutableJsonInt extends AbstractJsonNumber<Integer> {

    private ImmutableJsonInt(final int value) {
        super(value);
    }

    /**
     * Returns an instance of {@code ImmutableJsonInt}.
     *
     * @return the instance.
     */
    public static ImmutableJsonInt of(final int value) {
        return new ImmutableJsonInt(value);
    }

    @Override
    public boolean isInt() {
        return true;
    }

    @Override
    public boolean isLong() {
        return true;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AbstractJsonNumber) {
            final AbstractJsonNumber<?> that = (AbstractJsonNumber<?>) o;
            if (that.isInt()) {
                return Objects.equals(getValue(), that.asInt());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getValue();
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        serializationContext.writeNumber(getValue());
    }

    @Override
    public long getUpperBoundForStringSize() {
        return 11; // 10 digits for the decimal representation of 2^32 plus one character for a potential '-'
    }
}
