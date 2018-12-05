/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

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
            final AbstractJsonNumber that = (AbstractJsonNumber) o;
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

}
