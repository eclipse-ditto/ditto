/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * This {@link HeaderEntryFilter} reads headers which are of {@code serializationType}
 * {@code JsonArray} as comma-separated list of values so that the read header entry does not need to be in the JSON
 * representation of a JSON array (that would be: {@code "\"["foo","bar"]\""}), but instead simply: {@code "foo,bar"}.
 *
 * @since 1.1.0
 */
@Immutable
final class ReadJsonArrayHeadersFilter extends AbstractHeaderEntryFilter {

    private static final String COMMA_SPLIT_REGEX = ",";

    private final Map<String, HeaderDefinition> headerDefinitions;

    private ReadJsonArrayHeadersFilter(final Map<String, HeaderDefinition> headerDefinitions) {
        this.headerDefinitions = Collections.unmodifiableMap(new HashMap<>(headerDefinitions));
    }

    /**
     * Returns an instance of {@code ReadJsonArrayHeaderFilter} which reads headers which are of
     * {@code serializationType} {@code JsonArray} as comma-separated list of values.
     *
     * @param headerDefinitions the header definitions for determining whether a header entry is of
     * {@code serializationType} {@code JsonArray} in order to apply the simplified format.
     * @return the instance.
     * @throws NullPointerException if {@code headerDefinitions} is {@code null}.
     */
    public static ReadJsonArrayHeadersFilter getInstance(final Map<String, HeaderDefinition> headerDefinitions) {
        return new ReadJsonArrayHeadersFilter(checkNotNull(headerDefinitions, "headerDefinitions"));
    }

    @Nullable
    @Override
    protected String filterValue(final String key, final String value) {
        String result = value;
        if (isJsonArrayDefinition(key)) {
            final JsonArray jsonArray = getJsonArray(value);
            result = jsonArray.toString();
        }
        return result;
    }

    private boolean isJsonArrayDefinition(final String headerKey) {
        @Nullable final HeaderDefinition headerDefinition = headerDefinitions.get(headerKey.toLowerCase());
        boolean result = false;
        if (null != headerDefinition) {
            result = JsonArray.class.equals(headerDefinition.getSerializationType());
        }
        return result;
    }

    private static JsonArray getJsonArray(final String headerValue) {
        final JsonArray result;
        if (headerValue.isEmpty()) {
            result = JsonArray.empty();
        } else {
            result = tryToParseJsonArray(headerValue);
        }
        return result;
    }

    private static JsonArray tryToParseJsonArray(final String headerValue) {
        try {
            return JsonArray.of(headerValue);
        } catch (final JsonParseException e) {
            // when the passed in header value is not yet a JsonArray, fall back to assuming that it was provided as
            // comma separated list and build an array based on that assumption:
            return Arrays.stream(headerValue.split(COMMA_SPLIT_REGEX))
                    .filter(string -> !string.isEmpty())
                    .map(String::trim)
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
        }
    }

}
