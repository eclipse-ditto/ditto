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
package org.eclipse.ditto.signals.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonExceptionBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Abstract implementation of {@link JsonParsableRegistry}.
 *
 * @param <T> the type to parse.
 */
@Immutable
public abstract class AbstractJsonParsableRegistry<T> implements JsonParsableRegistry<T> {

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
     * @throws DittoJsonException if {@code jsonString} does not contain a valid JSON object.
     */
    @Override
    public T parse(final String jsonString, final DittoHeaders dittoHeaders) {
        return DittoJsonException.wrapJsonRuntimeException(jsonString, dittoHeaders, this::fromJson);
    }

    @Override
    public T parse(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoJsonException.wrapJsonRuntimeException(jsonObject, dittoHeaders, this::fromJson);
    }

    private T fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject, dittoHeaders);
    }

    private T fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String type = resolveType(jsonObject);
        final JsonParsable<T> jsonObjectParsable = parseStrategies.get(type);

        if (null != jsonObjectParsable) {
            try {
                return jsonObjectParsable.parse(jsonObject, dittoHeaders);
            } catch (final JsonRuntimeException jre) {
                final JsonExceptionBuilder builder = JsonRuntimeException.newBuilder(jre.getErrorCode())
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
}
