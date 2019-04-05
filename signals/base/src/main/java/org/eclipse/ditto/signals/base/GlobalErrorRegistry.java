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
package org.eclipse.ditto.signals.base;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
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

    private static final GlobalErrorRegistry INSTANCE = new GlobalErrorRegistry();

    private GlobalErrorRegistry() {
        super(getParseRegistries());
    }

    private static Map<String, JsonParsable<DittoRuntimeException>> getParseRegistries() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseRegistries =
                new JsonParsableExceptionRegistry().getParseRegistries();
        parseRegistries.putAll(new DittoJsonExceptionRegistry().getDittoJsonParseRegistries());

        return parseRegistries;
    }

    /**
     * Gets an instance of GlobalErrorRegistry.
     *
     * @return the instance.
     */
    public static GlobalErrorRegistry getInstance() {
        return INSTANCE;
    }


    /**
     * Contains all strategies to deserialize {@link DittoJsonException} from a combination of
     * {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class DittoJsonExceptionRegistry {

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
    private static final class JsonParsableExceptionRegistry {

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
                } catch (final NoSuchMethodException e) {
                    throw new DeserializationStrategyNotFoundError(parsableException, e);
                }
            });
        }

        private void appendMethodToParseStrategies(final String errorCode, final Method method) {
            parseRegistries.put(errorCode, (jsonObject, dittoHeaders) -> {
                try {
                    return (DittoRuntimeException) method.invoke(null, jsonObject, dittoHeaders);
                } catch (final IllegalAccessException | InvocationTargetException e) {
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
