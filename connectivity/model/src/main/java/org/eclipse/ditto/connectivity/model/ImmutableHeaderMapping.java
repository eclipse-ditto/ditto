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
package org.eclipse.ditto.connectivity.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of a {@link HeaderMapping}.
 */
@Immutable
final class ImmutableHeaderMapping implements HeaderMapping {

    private final Map<String, String> mapping;

    ImmutableHeaderMapping(final Map<String, String> mapping) {
        this.mapping = Collections.unmodifiableMap(new HashMap<>(mapping));
    }

    @Override
    public Map<String, String> getMapping() {
        return mapping;
    }

    /**
     * Creates a new {@code HeaderMapping} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the HeaderMapping to be created.
     * @return a new HeaderMapping which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static HeaderMapping fromJson(final JsonObject jsonObject) {
        return new ImmutableHeaderMapping(jsonObject.stream()
                .filter(f -> f.getValue().isString())
                .collect(Collectors.toMap(JsonField::getKeyName, jsonField -> jsonField.getValue().asString())));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final List<JsonField> fields =
                mapping.entrySet().stream().map(e -> JsonFactory.newField(JsonFactory.newKey(e.getKey()),
                        JsonFactory.newValue(e.getValue()))).collect(Collectors.toList());
        return JsonFactory.newObjectBuilder(fields).build();

    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableHeaderMapping that = (ImmutableHeaderMapping) o;
        return Objects.equals(mapping, that.mapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapping);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mapping=" + mapping +
                "]";
    }
}
