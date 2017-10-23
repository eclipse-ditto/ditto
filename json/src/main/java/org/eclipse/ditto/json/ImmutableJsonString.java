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

import javax.annotation.concurrent.Immutable;

/**
 * An immutable JSON string. It differs from a Java string by being surrounded by escaped quote characters. For example
 * the Java string {@code "foo"} would be {@code "\"foo\""} as JSON string.
 */
@Immutable
final class ImmutableJsonString extends AbstractMinimalJsonValueWrapper {

    private ImmutableJsonString(final com.eclipsesource.json.JsonValue toWrap) {
        super(toWrap);
        if (!toWrap.isString()) {
            throw new IllegalArgumentException("Is not a string: " + toWrap.toString());
        }
    }

    /**
     * Returns a new {@code ImmutableJsonString} instance based on the given value.
     *
     * @param minimalJsonValue the value from which to create a new JSON string.
     * @return a new JSON string.
     * @throws NullPointerException if {@code minimalJsonValue} is {@code null}.
     * @throws IllegalArgumentException if {@code minimalJsonValue} is not a string.
     * @see JsonValue#isString()
     */
    public static ImmutableJsonString of(final com.eclipsesource.json.JsonValue minimalJsonValue) {
        return new ImmutableJsonString(minimalJsonValue);
    }

}
