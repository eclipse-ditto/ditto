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
package org.eclipse.ditto.json.assertions;

import java.util.function.Consumer;

import org.eclipse.ditto.json.JsonValue;

/**
 * This interface defines methods for verifying aspects which are common to all implementations of {@link JsonValue}.
 */
public interface JsonValueAssertable<S extends JsonValueAssertable<S>> {

    /**
     * Verifies that the actual JSON value is an array.
     *
     * @return this assert to allow method chaining.
     */
    S isArray();

    /**
     * Verifies that the actual JSON value is not an array.
     *
     * @return this assert to allow method chaining.
     */
    S isNotArray();

    /**
     * Verifies that the actual JSON value is an object.
     *
     * @return this assert to allow method chaining.
     */
    S isObject();

    /**
     * Verifies that the actual JSON value is not an object.
     *
     * @return this assert to allow method chaining.
     */
    S isNotObject();

    /**
     * Verifies that the actual JSON value is a boolean.
     *
     * @return this assert to allow method chaining.
     */
    S isBoolean();

    /**
     * Verifies that the actual JSON value is not a boolean.
     *
     * @return this assert to allow method chaining.
     */
    S isNotBoolean();

    /**
     * Verifies that the actual JSON value is not {@code null}.
     *
     * @return this assert to allow method chaining.
     */
    S isNullLiteral();

    /**
     * Verifies that the actual JSON value is not {@code null}.
     *
     * @return this assert to allow method chaining.
     */
    S isNotNullLiteral();

    /**
     * Verifies that the actual JSON value is a number.
     *
     * @return this assert to allow method chaining.
     */
    S isNumber();

    /**
     * Verifies that the actual JSON value is not a number.
     *
     * @return this assert to allow method chaining.
     */
    S isNotNumber();

    /**
     * Verifies that the actual JSON value is a string.
     *
     * @return this assert to allow method chaining.
     */
    S isString();

    /**
     * Verifies that the actual JSON value is not a string.
     *
     * @return this assert to allow method chaining.
     */
    S isNotString();

    /**
     * Verifies that the specified Consumer leads to an {@link UnsupportedOperationException}.
     *
     * @param methodReference the method whose invocation is expected to throw an unsupported operation exception.
     * @return this assert to allow method chaining.
     */
    S doesNotSupport(Consumer<JsonValue> methodReference);

}
