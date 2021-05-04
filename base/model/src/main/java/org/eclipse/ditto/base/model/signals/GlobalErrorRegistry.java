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
package org.eclipse.ditto.base.model.signals;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Contains all strategies to deserialize subclasses of {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 */
@Immutable
public final class GlobalErrorRegistry
        extends AbstractGlobalJsonParsableRegistry<DittoRuntimeException, JsonParsableException>
        implements ErrorRegistry<DittoRuntimeException> {

    private static final GlobalErrorRegistry INSTANCE = new GlobalErrorRegistry(new DittoJsonExceptionRegistry());

    private GlobalErrorRegistry(final DittoJsonExceptionRegistry dittoJsonExceptionRegistry) {
        super(
                DittoRuntimeException.class,
                JsonParsableException.class,
                new ExceptionParsingStrategyFactory(),
                dittoJsonExceptionRegistry.getDittoJsonParseRegistries()
        );
    }

    /**
     * Gets an instance of GlobalErrorRegistry.
     *
     * @return the instance.
     */
    public static GlobalErrorRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(DittoRuntimeException.JsonFields.ERROR_CODE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(DittoRuntimeException.JsonFields.ERROR_CODE.getPointer().toString())
                        .build());
    }

    @Override
    public DittoRuntimeException parse(final String jsonString, final DittoHeaders dittoHeaders) {
        try {
            return super.parse(jsonString, dittoHeaders);
        } catch (final JsonTypeNotParsableException e) {
            return UnknownDittoRuntimeException.fromJson(JsonObject.of(jsonString), dittoHeaders);
        }
    }

    @Override
    public DittoRuntimeException parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        try {
            return super.parse(jsonObject, dittoHeaders);
        } catch (final JsonTypeNotParsableException e) {
            return UnknownDittoRuntimeException.fromJson(jsonObject, dittoHeaders);
        }
    }

    @Override
    public DittoRuntimeException parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            final ParseInnerJson parseInnerJson) {
        try {
            return super.parse(jsonObject, dittoHeaders, parseInnerJson);
        } catch (final JsonTypeNotParsableException e) {
            return UnknownDittoRuntimeException.fromJson(jsonObject, dittoHeaders);
        }
    }

    /**
     * Contains all strategies to deserialize {@link org.eclipse.ditto.base.model.exceptions.DittoJsonException} from a combination of
     * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class DittoJsonExceptionRegistry {

        private final Map<String, JsonParsable<DittoRuntimeException>> dittoJsonParseRegistries = new HashMap<>();

        private DittoJsonExceptionRegistry() {
            dittoJsonParseRegistries.put(JsonParseException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonParseException.newBuilder()
                                    .message(getMessage(jsonObject))
                                    .description(getDescription(jsonObject))
                                    .build(), dittoHeaders));

            dittoJsonParseRegistries.put(JsonMissingFieldException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonMissingFieldException.newBuilder()
                                    .message(getMessage(jsonObject))
                                    .description(getDescription(jsonObject))
                                    .build(),
                            dittoHeaders));

            dittoJsonParseRegistries.put(JsonFieldSelectorInvalidException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonFieldSelectorInvalidException.newBuilder()
                                    .message(getMessage(jsonObject))
                                    .description(getDescription(jsonObject))
                                    .build(),
                            dittoHeaders));

            dittoJsonParseRegistries.put(JsonPointerInvalidException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonPointerInvalidException.newBuilder()
                                    .message(getMessage(jsonObject))
                                    .description(getDescription(jsonObject))
                                    .build(),
                            dittoHeaders));

            dittoJsonParseRegistries.put(JsonKeyInvalidException.ERROR_CODE,
                    (jsonObject, dittoHeaders) -> new DittoJsonException(
                            JsonKeyInvalidException.newBuilder()
                                    .message(getMessage(jsonObject))
                                    .description(getDescription(jsonObject))
                                    .build(),
                            dittoHeaders));
        }

        @Nullable
        private static String getMessage(final JsonObject jsonObject) {
            return jsonObject.getValue(DittoRuntimeException.JsonFields.MESSAGE).orElse(null);
        }

        @Nullable
        private static String getDescription(final JsonObject jsonObject) {
            return jsonObject.getValue(DittoRuntimeException.JsonFields.DESCRIPTION).orElse(null);
        }

        private Map<String, JsonParsable<DittoRuntimeException>> getDittoJsonParseRegistries() {
            return dittoJsonParseRegistries;
        }

    }

    /**
     * Contains all strategies to deserialize {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} annotated with {@link org.eclipse.ditto.base.model.json.JsonParsableException}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class ExceptionParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<DittoRuntimeException, JsonParsableException> {

        private ExceptionParsingStrategyFactory() {}

        @Override
        protected String getKeyFor(final JsonParsableException annotation) {
            return annotation.errorCode();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableException annotation) {
            return annotation.method();
        }
    }

}
