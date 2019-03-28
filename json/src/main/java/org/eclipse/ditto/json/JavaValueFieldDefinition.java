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

import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This is a JsonFieldDefinition for an arbitrary Java value which is supported by JSON.
 */
@Immutable
final class JavaValueFieldDefinition<T> extends AbstractJsonFieldDefinition<T> {

    private JavaValueFieldDefinition(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, Boolean> checkJavaTypeFunction,
            final Function<JsonValue, T> mappingFunction,
            final JsonFieldMarker... markers) {

        super(pointer, valueType, checkJavaTypeFunction, mappingFunction, markers);
    }

    /**
     * Returns a new instance of {@code JsonValueFieldDefinition} with the specified pointer and markers.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param valueType the type of the value of the defined JSON field.
     * @param checkJavaTypeFunction the function which checks if a given JsonValue represents the expected Java type.
     * @param mappingFunction the function for converting a JsonValue into the value type.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static <T> JavaValueFieldDefinition<T> newInstance(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, Boolean> checkJavaTypeFunction,
            final Function<JsonValue, T> mappingFunction,
            final JsonFieldMarker... markers) {

        return new JavaValueFieldDefinition<>(pointer, valueType, checkJavaTypeFunction, mappingFunction, markers);
    }

    @Nullable
    @Override
    protected T getAsJavaType(final JsonValue jsonValue, final Function<JsonValue, T> mappingFunction) {
        if (jsonValue.isNull()) {
            return null;
        }
        return mappingFunction.apply(jsonValue);
    }

}
