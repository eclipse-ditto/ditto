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

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * This abstract implementation of an immutable JSON value wraps a JSON value of the Minimal JSON library. This class
 * merely exists to reduce redundancy.
 */
abstract class AbstractMinimalJsonValueWrapper extends AbstractImmutableJsonValue {

    private final com.eclipsesource.json.JsonValue wrapped;

    /**
     * Constructs a new {@code AbstractMinimalJsonValueWrapper} object.
     *
     * @param toWrap the Minimal JSON value to be wrapped.
     * @throws NullPointerException if {@code toWrap} is {@code null}.
     */
    protected AbstractMinimalJsonValueWrapper(final com.eclipsesource.json.JsonValue toWrap) {
        wrapped = requireNonNull(toWrap, "The JSON value to wrap must not be null!");
    }

    @Override
    public boolean isNull() {
        return wrapped.isNull();
    }

    @Override
    public boolean isBoolean() {
        return wrapped.isBoolean();
    }

    @Override
    public boolean isNumber() {
        return wrapped.isNumber();
    }

    @Override
    public boolean isString() {
        return wrapped.isString();
    }

    @Override
    public boolean asBoolean() {
        return wrapped.asBoolean();
    }

    @Override
    public int asInt() {
        return wrapped.asInt();
    }

    @Override
    public long asLong() {
        return wrapped.asLong();
    }

    @Override
    public double asDouble() {
        return wrapped.asDouble();
    }

    @Override
    public String asString() {
        return wrapped.asString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractMinimalJsonValueWrapper that = (AbstractMinimalJsonValueWrapper) o;
        return Objects.equals(wrapped, that.wrapped);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrapped);
    }

    @Override
    protected String createStringRepresentation() {
        return wrapped.toString();
    }

}
