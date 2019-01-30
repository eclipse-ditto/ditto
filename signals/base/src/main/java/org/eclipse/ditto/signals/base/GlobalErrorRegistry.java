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
package org.eclipse.ditto.signals.base;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Contains all strategies to deserialize subclasses of {@link DittoRuntimeException} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private static final GlobalErrorRegistry instance = new GlobalErrorRegistry();

    private GlobalErrorRegistry() {
        super(getParseRegistries());
    }

    private static Map<String, JsonParsable<DittoRuntimeException>> getParseRegistries() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseRegistries =
                new JsonParsableExceptionRegistry().getParseRegistries();
        parseRegistries.putAll(new DittoJsonExceptionRegistry().getDittoJsonParseRegistries());

        return parseRegistries;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(DittoRuntimeException.JsonFields.ERROR_CODE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(DittoRuntimeException.JsonFields.ERROR_CODE.getPointer().toString())
                        .build());
    }

    public static GlobalErrorRegistry getInstance() {
        return instance;
    }


    /**
     * Contains all strategies to deserialize {@link DittoJsonException} from a combination of
     * {@link JsonObject} and {@link DittoHeaders}.
     */
    private static class DittoJsonExceptionRegistry {

        private final Map<String, JsonParsable<DittoRuntimeException>> dittoJsonParseRegistries = new HashMap<>();

        private DittoJsonExceptionRegistry() {

            dittoJsonParseRegistries.put(JsonParseException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonParseException.newBuilder().message(getMessage(jsonObject)).build(), dittoHeaders));

            dittoJsonParseRegistries.put(JsonMissingFieldException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonMissingFieldException.newBuilder().message(getMessage(jsonObject)).build(),
                            dittoHeaders));

            dittoJsonParseRegistries.put(JsonFieldSelectorInvalidException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonFieldSelectorInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                            dittoHeaders));

            dittoJsonParseRegistries.put(JsonPointerInvalidException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonPointerInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                            dittoHeaders));
        }

        private static String getMessage(final JsonObject jsonObject) {
            return jsonObject.getValue(DittoJsonException.JsonFields.MESSAGE)
                    .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                            .fieldName(DittoRuntimeException.JsonFields.MESSAGE.getPointer().toString()).build());
        }

        private Map<String, JsonParsable<DittoRuntimeException>> getDittoJsonParseRegistries() {
            return dittoJsonParseRegistries;
        }

    }

    /**
     * Contains all strategies to deserialize {@link DittoRuntimeException} annotated with {@link JsonParsableException}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static class JsonParsableExceptionRegistry {

        private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
        private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

        private final Map<String, JsonParsable<DittoRuntimeException>> parseRegistries = new HashMap<>();


        private JsonParsableExceptionRegistry() {

            final Iterable<Class<?>> jsonParsableExceptions = ClassIndex.getAnnotated(JsonParsableException.class);
            jsonParsableExceptions.forEach(parsableException -> {
                final JsonParsableException fromJsonAnnotation =
                        parsableException.getAnnotation(JsonParsableException.class);
                try {
                    final String methodName = fromJsonAnnotation.method();
                    final String errorCode = fromJsonAnnotation.errorCode();
                    final Method method = parsableException
                            .getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);

                    appendMethodToParseStrategies(errorCode, method);

                } catch (NoSuchMethodException e) {
                    // TODO: Log warning or throw an exception?
                }
            });
        }

        private void appendMethodToParseStrategies(final String errorCode, final Method method) {
            parseRegistries.put(errorCode,
                    (jsonObject, dittoHeaders) -> {
                        try {
                            return (DittoRuntimeException) method.invoke(null, jsonObject, dittoHeaders);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw JsonTypeNotParsableException.newBuilder(errorCode, getClass().getSimpleName())
                                    .dittoHeaders(dittoHeaders)
                                    .build();
                        }
                    });
        }

        private Map<String, JsonParsable<DittoRuntimeException>> getParseRegistries() {
            return new HashMap<>(parseRegistries);
        }
    }
}
