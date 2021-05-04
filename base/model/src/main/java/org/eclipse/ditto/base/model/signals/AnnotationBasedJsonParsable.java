/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Responsible for deserialization of a class of type T.
 *
 * @param <T> the type of the class that should be deserialized.
 */
final class AnnotationBasedJsonParsable<T> implements JsonParsable<T> {

    private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
    private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;
    private static final Class<?> PARSE_INNER_JSON_PARAMETER = ParseInnerJson.class;

    private final String key;
    private final Method parseMethod;

    /**
     * Creates a new instance.
     *
     * @param key the API v2 key for this strategy.
     * @param parsedClass the class that should be deserialized.
     * @param parsingMethodName the name of the method that should be called on the given class in order to
     * deserialize.
     */
    AnnotationBasedJsonParsable(final String key,
            final Class<? extends T> parsedClass,
            final String parsingMethodName) {
        this.key = key;
        try {
            this.parseMethod = getParseMethod(parsedClass, parsingMethodName);
            final Class<?> returnType = parseMethod.getReturnType();
            if (!parsedClass.isAssignableFrom(returnType)) {
                throw new IllegalArgumentException(
                        String.format("Parse method is invalid. Return type <%s> of parse method must be assignable " +
                                "to parsed class: <%s>.", returnType.getSimpleName(), parsedClass.getSimpleName()));
            }
        } catch (final NoSuchMethodException e) {
            throw new DeserializationStrategyNotFoundError(parsedClass, e);
        }
    }

    /**
     * The API v2 key for this strategy.
     *
     * @return the API v2 key for this strategy.
     */
    public String getKey() {
        return key;
    }


    @SuppressWarnings("unchecked")
    @Override
    public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        try {
            return (T) parseMethod.invoke(null, jsonObject, dittoHeaders);
        } catch (final ClassCastException | IllegalAccessException e) {
            throw buildDittoJsonException(e, jsonObject, dittoHeaders);
        } catch (final InvocationTargetException e) {
            throw fromInvocationTargetException(e, jsonObject, dittoHeaders);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final ParseInnerJson parseInnerJson) {
        try {
            if (parseMethod.getParameterCount() == 3) {
                return (T) parseMethod.invoke(null, jsonObject, dittoHeaders, parseInnerJson);
            } else {
                return (T) parseMethod.invoke(null, jsonObject, dittoHeaders);
            }
        } catch (final ClassCastException | IllegalAccessException e) {
            throw buildDittoJsonException(e, jsonObject, dittoHeaders);
        } catch (final InvocationTargetException e) {
            throw fromInvocationTargetException(e, jsonObject, dittoHeaders);
        }
    }

    private DittoJsonException fromInvocationTargetException(final InvocationTargetException e,
            final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final Throwable targetException = e.getTargetException();

        if (targetException instanceof DittoRuntimeException) {
            throw (DittoRuntimeException) targetException;
        } else if (targetException instanceof JsonRuntimeException) {
            throw new DittoJsonException((JsonRuntimeException) targetException, dittoHeaders);
        }

        return buildDittoJsonException(targetException, jsonObject, dittoHeaders);
    }

    private DittoJsonException buildDittoJsonException(final Throwable cause,
            final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new DittoJsonException(JsonParseException.newBuilder()
                .message(String.format("Error during parsing json: <%s>", jsonObject.toString()))
                .cause(cause).build(),
                dittoHeaders);
    }

    private static Method getParseMethod(final Class<?> parsedClass, final String methodName)
            throws NoSuchMethodException {

        try {
            return parsedClass.getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER,
                    PARSE_INNER_JSON_PARAMETER);
        } catch (final NoSuchMethodException e) {
            return parsedClass.getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);
        }
    }
}
