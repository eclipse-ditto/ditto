/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * This {@link org.eclipse.ditto.protocoladapter.HeaderEntryFilter} reads headers which are of {@code serializationType}
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

        this.headerDefinitions = Collections.unmodifiableMap(checkNotNull(headerDefinitions, "headerDefinitions"));
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
        return new ReadJsonArrayHeadersFilter(headerDefinitions);
    }

    @Nullable
    @Override
    protected String filterValue(final String key, final String value) {
        @Nullable final HeaderDefinition headerDefinition = headerDefinitions.get(key);
        if (headerDefinition != null && headerDefinition.getSerializationType().equals(JsonArray.class)) {
            if (value.isEmpty()) {
                return JsonArray.empty().toString();
            } else {
                try {
                    JsonArray.of(value);
                    // using the format ["foo","bar"] still is valid, so let it pass:
                    return value;
                } catch (final JsonParseException e) {
                    // when the passed in header value is not yet a JsonArray, fall back to assuming that it was provided as
                    //  comma separated list and build an array based on that assumption:
                    return Stream.of(value.split(COMMA_SPLIT_REGEX))
                            .filter(string -> !string.isEmpty())
                            .map(JsonValue::of)
                            .collect(JsonCollectors.valuesToArray())
                            .toString();
                }
            }
        }
        return value;
    }

}
