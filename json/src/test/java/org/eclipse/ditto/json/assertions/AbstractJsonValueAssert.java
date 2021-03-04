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
package org.eclipse.ditto.json.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import java.util.function.Consumer;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract base implementation of {@link AbstractAssert} and {@link JsonValueAssertable} for all specific asserts for
 * JSON values.
 */
abstract class AbstractJsonValueAssert<S extends AbstractJsonValueAssert<S, A>, A extends JsonValue>
        extends AbstractAssert<S, A> implements JsonValueAssertable<S> {

    /**
     * Constructs a new {@code AbstractJsonValueAssert} object.
     *
     * @param actual the actual JSON value to be verified.
     * @param selfType the type of the implementation of this class.
     */
    protected AbstractJsonValueAssert(final A actual, final Class<?> selfType) {
        super(actual, selfType);
    }

    @Override
    public S isArray() {
        isNotNull();

        assertThat(actual.isArray())
                .overridingErrorMessage("Expected JSON value <%s> to be an array but it was not.", actual)
                .isTrue();

        return myself;
    }

    @Override
    public S isNotArray() {
        isNotNull();

        assertThat(actual.isArray())
                .overridingErrorMessage("Expected JSON value <%s> not to be an array but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S isObject() {
        isNotNull();

        assertThat(actual.isObject())
                .overridingErrorMessage("Expected JSON value <%s> to be an object but it was not.", actual)
                .isTrue();

        return myself;
    }

    @Override
    public S isNotObject() {
        isNotNull();

        assertThat(actual.isObject())
                .overridingErrorMessage("Expected JSON value <%s> not to be an object but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S isBoolean() {
        isNotNull();

        assertThat(actual.isBoolean())
                .overridingErrorMessage("Expected JSON value <%s> to be a boolean but it was not.", actual)
                .isTrue();

        return myself;
    }

    @Override
    public S isNotBoolean() {
        isNotNull();

        assertThat(actual.isBoolean())
                .overridingErrorMessage("Expected JSON value <%s> not to be a boolean but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S isNullLiteral() {
        isNotNull();

        assertThat(actual.isNull()).overridingErrorMessage(
                "Expected JSON value <%s> to be null literal but it was not.",
                actual).isTrue();

        return myself;
    }

    @Override
    public S isNotNullLiteral() {
        isNotNull();

        assertThat(actual.isNull())
                .overridingErrorMessage("Expected JSON value <%s> not to be null literal but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S isNumber() {
        isNotNull();

        assertThat(actual.isNumber())
                .overridingErrorMessage("Expected JSON value <%s> to be a number but it was not.", actual)
                .isTrue();

        return myself;
    }

    @Override
    public S isNotNumber() {
        isNotNull();

        assertThat(actual.isNumber())
                .overridingErrorMessage("Expected JSON value <%s> not to be a number but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S isString() {
        isNotNull();

        assertThat(actual.isString())
                .overridingErrorMessage("Expected JSON value <%s> to be a string but it was not.", actual)
                .isTrue();

        return myself;
    }

    @Override
    public S isNotString() {
        isNotNull();

        assertThat(actual.isString())
                .overridingErrorMessage("Expected JSON value <%s> not to be a string but it was.", actual)
                .isFalse();

        return myself;
    }

    @Override
    public S doesNotSupport(final Consumer<JsonValue> methodReference) {
        isNotNull();

        try {
            methodReference.accept(actual);
            failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(UnsupportedOperationException.class);
        }

        return myself;
    }

}
