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

import static java.util.Objects.requireNonNull;

/**
 * Abstract base implementation of JSON number types.
 * This class provides common functionality in order to keep its sub-classes as small as possible.
 *
 * @param <T> the type of the number.
 */
abstract class AbstractJsonNumber<T extends Number> extends AbstractJsonValue implements JsonNumber {

    private final T value;

    /**
     * Constructs a new {@code AbstractJsonNumber} object.
     *
     * @param value the actual Number value.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    protected AbstractJsonNumber(final T value) {
        this.value = requireNonNull(value, "The value must not be null!");
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    public boolean isInt() {
        return false;
    }

    @Override
    public int asInt() {
        if (isInt()) {
            return value.intValue();
        }
        throw new NumberFormatException("This JSON value is not an int: " + value);
    }

    @Override
    public boolean isLong() {
        return false;
    }

    @Override
    public long asLong() {
        if (isLong()) {
            return value.longValue();
        }
        throw new NumberFormatException("This JSON value is not a long: " + value);
    }

    @Override
    public boolean isDouble() {
        return true;
    }

    @Override
    public double asDouble() {
        return value.doubleValue();
    }

    /**
     * Returns the value of this number as Java type.
     *
     * @return the value.
     */
    T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
