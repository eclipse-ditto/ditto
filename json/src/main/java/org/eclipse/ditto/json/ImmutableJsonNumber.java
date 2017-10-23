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
 * An immutable JSON number. As JSON does not define fine grained number types like Java ({@code int}, {@code float},
 * {@code long} etc.) a JSON number is represented as a Java {@link Number} object.
 */
@Immutable
final class ImmutableJsonNumber extends AbstractMinimalJsonValueWrapper {

    private ImmutableJsonNumber(final com.eclipsesource.json.JsonValue toWrap) {
        super(toWrap);
        if (!toWrap.isNumber()) {
            throw new IllegalArgumentException("Is not a number: " + toWrap.toString());
        }
    }

    /**
     * Returns a new instance of {@code ImmutableJsonNumber} for the given JSON number value.
     *
     * @param minimalJsonValue the value from which to create a new JSON number.
     * @return a new JSON number.
     * @throws NullPointerException if {@code minimalJsonValue} is {@code null}.
     * @throws IllegalArgumentException if {@code minimalJsonValue} is not a number.
     */
    public static ImmutableJsonNumber of(final com.eclipsesource.json.JsonValue minimalJsonValue) {
        return new ImmutableJsonNumber(minimalJsonValue);
    }

}
