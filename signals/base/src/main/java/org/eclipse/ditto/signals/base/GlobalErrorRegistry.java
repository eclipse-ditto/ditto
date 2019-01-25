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
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Collects
 */
@Immutable
public final class GlobalErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private static final GlobalErrorRegistry instance = new GlobalErrorRegistry();

    private GlobalErrorRegistry() {
        super(getParseRegistries());
    }

    private static Map<String, JsonParsable<DittoRuntimeException>> getParseRegistries() {
        return JsonParsableExceptionRegistry.getParseRegistries();
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

    private static class JsonParsableExceptionRegistry {

        private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
        private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

        private static final JsonParsableExceptionRegistry instance = new JsonParsableExceptionRegistry();

        private final Map<String, JsonParsable<DittoRuntimeException>> parseRegistries = new HashMap<>();


        private JsonParsableExceptionRegistry() {

            final List<Class<? extends DittoRuntimeException>> jsonParsableExceptions = getJsonParsableExceptions();
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
                    // TODO: Log warning or Throw an appropriate exception?
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

        private List<Class<? extends DittoRuntimeException>> getJsonParsableExceptions() {
            final Iterable<Class<?>> jsonParsableClasses = ClassIndex.getAnnotated(JsonParsableException.class);
            final ArrayList<Class<? extends DittoRuntimeException>> jsonParsableExceptions = new ArrayList<>();
            for (Class<?> classFromJson : jsonParsableClasses) {
                if (DittoRuntimeException.class.isAssignableFrom(classFromJson)) {
                    jsonParsableExceptions.add(classFromJson.asSubclass(DittoRuntimeException.class));
                }
            }
            return jsonParsableExceptions;
        }

        static Map<String, JsonParsable<DittoRuntimeException>> getParseRegistries() {
            return instance.parseRegistries;
        }
    }
}
