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
package org.eclipse.ditto.json;

import java.lang.ref.SoftReference;

import javax.annotation.Nullable;

/**
 * Abstract base implementation of {@link JsonValue}. This class merely exists to save work when writing other
 * implementations of {@code JsonValue}. With this abstract implementation only the particular methods have to be
 * re-implemented. Also the string representation of this value is cached which should have positive impacts on
 * overall performance.
 */
abstract class AbstractImmutableJsonValue implements JsonValue {

    private static final String NOT_A_NUMBER = "This JSON value is not a number: ";

    @Nullable private SoftReference<String> stringRepresentation;

    /**
     * Constructs a new {@code AbstractImmutableJsonValue} object.
     */
    protected AbstractImmutableJsonValue() {
        stringRepresentation = null;
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
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
    }

    @Override
    public long asLong() {
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
    }

    @Override
    public double asDouble() {
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
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
    public String toString() {
      /*
       * Lazy initialisation of the string representation of this JSON value by using the Single-Check-Idiom.
       * This preserves immutability of this class (and its children) as no state change is visible for users of this
       * class. However in rare cases the string representation is created two times.
       */
        if (stringRepresentation == null || stringRepresentation.get() == null) {
            stringRepresentation = new SoftReference<>(createStringRepresentation());
        }
        return stringRepresentation.get();
    }

    protected abstract String createStringRepresentation();

}
