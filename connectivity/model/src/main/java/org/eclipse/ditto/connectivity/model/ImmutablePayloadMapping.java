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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of a {@link PayloadMapping}.
 */
@Immutable
final class ImmutablePayloadMapping implements PayloadMapping {

    private final List<String> mappings;

    private ImmutablePayloadMapping(final List<String> mappings) {
        checkNotNull(mappings, "mappings");
        this.mappings = Collections.unmodifiableList(new ArrayList<>(mappings));
    }

    static PayloadMapping empty() {
        return new ImmutablePayloadMapping(Collections.emptyList());
    }

    static PayloadMapping from(final List<String> mappings) {
        return new ImmutablePayloadMapping(mappings);
    }

    @Override
    public List<String> getMappings() {
        return mappings;
    }


    @Override
    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    @Override
    @SuppressWarnings("squid:S3252")
    public JsonArray toJson() {
        return JsonArray.of(mappings);
    }

    /**
     * Creates a new {@code PayloadMapping} object from the specified JSON object.
     *
     * @param jsonArray a JSON array which provides the data for the PayloadMapping to be created.
     * @return a new PayloadMapping which is initialised with the extracted data from {@code jsonArray}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static PayloadMapping fromJson(final JsonArray jsonArray) {
        return ImmutablePayloadMapping.from(jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toList()));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePayloadMapping that = (ImmutablePayloadMapping) o;
        return Objects.equals(mappings, that.mappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappings);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mappings=" + mappings +
                "]";
    }
}
