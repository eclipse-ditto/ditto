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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

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
        super(getParseStrategies());
    }

    private static Map<String, JsonParsable<DittoRuntimeException>> getParseStrategies() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies =
                new JsonParsableExceptionRegistry().getParseStrategies();
        parseStrategies.putAll(new DittoJsonExceptionRegistry().getDittoJsonParseRegistries());

        return parseStrategies;
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
    private static final class JsonParsableExceptionRegistry extends AbstractGlobalJsonParsableRegistry<DittoRuntimeException
                , JsonParsableException> {

        private JsonParsableExceptionRegistry() {
            super(DittoRuntimeException.class, JsonParsableException.class);
        }

        @Override
        protected String getV1FallbackKeyFor(final JsonParsableException annotation) {
            return annotation.errorCode();
        }

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
