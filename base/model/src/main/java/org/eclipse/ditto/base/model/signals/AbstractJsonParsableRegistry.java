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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.NotSerializableException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonExceptionBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Abstract implementation of {@link JsonParsableRegistry}.
 *
 * @param <T> the type to parse.
 */
@Immutable
public abstract class AbstractJsonParsableRegistry<T> implements JsonParsableRegistry<T> {

    private static final ParseInnerJson UNSUPPORTED = new ParseInnerJsonUnsupported();

    private final Map<String, JsonParsable<T>> parseStrategies;

    /**
     * Constructs a new {@code AbstractJsonParsableRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected AbstractJsonParsableRegistry(final Map<String, JsonParsable<T>> parseStrategies) {
        checkNotNull(parseStrategies, "parse strategies");
        this.parseStrategies = Collections.unmodifiableMap(new HashMap<>(parseStrategies));
    }

    @Override
    public Set<String> getTypes() {
        return parseStrategies.keySet();
    }

    /**
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonTypeNotParsableException if the {@code jsonObject}'s {@code type} was unknown to the parser.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonString} does not contain a valid JSON object.
     */
    @Override
    public T parse(final String jsonString, final DittoHeaders dittoHeaders) {
        return DittoJsonException.wrapJsonRuntimeException(jsonString, dittoHeaders, this::fromJson);
    }

    @Override
    public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoJsonException.wrapJsonRuntimeException(jsonObject, dittoHeaders, this::fromJson);
    }

    @Override
    public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders, final ParseInnerJson parseInnerJson) {
        return DittoJsonException.wrapJsonRuntimeException(jsonObject, dittoHeaders,
                (object, headers) -> fromJson(object, headers, parseInnerJson));
    }

    private T fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject, dittoHeaders, UNSUPPORTED);
    }

    private T fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromJson(jsonObject, dittoHeaders, UNSUPPORTED);
    }

    private T fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders, final ParseInnerJson inner) {
        final String type = resolveType(jsonObject);
        final JsonParsable<T> jsonObjectParsable = parseStrategies.get(type);

        if (null != jsonObjectParsable) {
            try {
                return jsonObjectParsable.parse(jsonObject, dittoHeaders, inner);
            } catch (final JsonRuntimeException jre) {
                final JsonExceptionBuilder<?> builder = JsonRuntimeException.newBuilder(jre.getErrorCode())
                        .message("Error when parsing Json type '" + type + "': " + jre.getMessage())
                        .cause(jre.getCause());
                jre.getDescription().ifPresent(builder::description);
                jre.getHref().ifPresent(builder::href);
                // rethrow after enhancing the exception with the type which failed to parse
                throw (JsonRuntimeException) builder.build();
            }
        } else {
            throw JsonTypeNotParsableException.newBuilder(type, getClass().getSimpleName())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Function that resolves the type out of the given JSON object.
     *
     * @param jsonObject the JSON object to resolve the type from.
     * @return the resolved type.
     */
    protected abstract String resolveType(final JsonObject jsonObject);

    private static final class ParseInnerJsonUnsupported implements ParseInnerJson {

        @Override
        public Jsonifiable<?> parseInnerJson(final JsonObject jsonObject) throws NotSerializableException {
            throw new UnsupportedOperationException();
        }
    }
}
