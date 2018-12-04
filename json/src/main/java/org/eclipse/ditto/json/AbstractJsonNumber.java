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

import static java.util.Objects.requireNonNull;

import java.util.Objects;

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
    public boolean isLong() {
        return false;
    }

    @Override
    public boolean isDouble() {
        return false;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractJsonNumber<?> that = (AbstractJsonNumber<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
