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

import javax.annotation.Nonnull;

/**
 * Abstract base implementation of {@link JsonValue}.
 * This class merely exists to save work when writing other implementations of {@code JsonValue}.
 * With this abstract implementation only the particular methods have to be re-implemented.
*/
abstract class AbstractJsonValue implements JsonValue {

    /**
     * Constructs a new {@code AbstractJsonValue} object.
     */
    protected AbstractJsonValue() {
        super();
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isBoolean() {
        return false;
    }

    @Override
    public boolean isNumber() {
        return false;
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

    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean asBoolean() {
        throw new UnsupportedOperationException("This JSON value is not a boolean: " + toString());
    }

    @Override
    public int asInt() {
        throw new UnsupportedOperationException("This JSON value is not an int: " + toString());
    }

    @Override
    public long asLong() {
        throw new UnsupportedOperationException("This JSON value is not a long: " + toString());
    }

    @Override
    public double asDouble() {
        throw new UnsupportedOperationException("This JSON value is not a double: " + toString());
    }

    @Override
    public String asString() {
        throw new UnsupportedOperationException("This JSON value is not a string: " + toString());
    }

    @Override
    public JsonObject asObject() {
        throw new UnsupportedOperationException("This JSON value is not an object: " + toString());
    }

    @Override
    public JsonArray asArray() {
        throw new UnsupportedOperationException("This JSON value is not an array: " + toString());
    }

    @Override
    @Nonnull
    public abstract String toString();

}
