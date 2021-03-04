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

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

/**
 * This implementation of {@link JsonFieldDefinition} is meant to be used for all
 * {@link JsonValue} types to make parsing a JsonObject easier, and most notably, type safe.
 *
 * A {@code null} value is not mapped to a Java {@code null} but remains a JSON null; thus
 * {@link #getAsJavaType(JsonValue, java.util.function.Function)} never returns {@code null}.
 *
 * @param <T> the type of this definition's value type.
 */
@Immutable
final class JsonValueFieldDefinition<T extends JsonValue> extends AbstractJsonFieldDefinition<T> {

    private JsonValueFieldDefinition(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, Boolean> checkJavaTypeFunction,
            final Function<JsonValue, T> mappingFunction,
            final JsonFieldMarker ... markers) {

        super(pointer, valueType, checkJavaTypeFunction, mappingFunction, markers);
    }

    /**
     * Returns a new instance of {@code TypeSafeJsonFieldDefinition}.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param valueType the type of the value of the defined JSON field.
     * @param checkJavaTypeFunction the function which checks if a given JsonValue represents the expected Java type.
     * @param mappingFunction the function for converting a JsonValue into the value type.
     * @param markers optional markers which add user defined semantics to the defined JSON field.
     * @param <T> the type of value type.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static <T extends JsonValue> JsonValueFieldDefinition<T> newInstance(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, Boolean> checkJavaTypeFunction,
            final Function<JsonValue, T> mappingFunction,
            final JsonFieldMarker ... markers) {

        requireNonNull(mappingFunction, "The mapping function must not be null!");

        return new JsonValueFieldDefinition<>(pointer, valueType, checkJavaTypeFunction, mappingFunction, markers);
    }

    @Override
    protected T getAsJavaType(final JsonValue jsonValue, final Function<JsonValue, T> mappingFunction) {
        return mappingFunction.apply(jsonValue);
    }

}
