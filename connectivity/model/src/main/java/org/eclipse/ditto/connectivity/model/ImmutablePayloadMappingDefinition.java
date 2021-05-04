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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of a {@link PayloadMappingDefinition}.
 */
@Immutable
final class ImmutablePayloadMappingDefinition implements PayloadMappingDefinition {

    private final Map<String, MappingContext> definitions;

    private ImmutablePayloadMappingDefinition(final Map<String, MappingContext> definitions) {
        checkNotNull(definitions, "definitions");
        this.definitions = Collections.unmodifiableMap(new HashMap<>(definitions));
    }

    static PayloadMappingDefinition empty() {
        return new ImmutablePayloadMappingDefinition(Collections.emptyMap());
    }

    static PayloadMappingDefinition from(final Map<String, MappingContext> definitions) {
        return new ImmutablePayloadMappingDefinition(definitions);
    }

    @Override
    public Map<String, MappingContext> getDefinitions() {
        return definitions;
    }

    @Override
    public PayloadMappingDefinition withDefinition(final String id, final MappingContext mappingContext) {
        final Map<String, MappingContext> newDefinitions = new HashMap<>(this.definitions);
        newDefinitions.put(id, mappingContext);
        return new ImmutablePayloadMappingDefinition(newDefinitions);
    }

    @Override
    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    /**
     * Creates a new {@code PayloadMappingDefinition} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the PayloadMappingDefinition to be created.
     * @return a new PayloadMappingDefinition which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static PayloadMappingDefinition fromJson(final JsonObject jsonObject) {
        return ImmutablePayloadMappingDefinition.from(ConnectivityModelFactory.mappingsFromJson(jsonObject));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return definitions.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), e.getValue().toJson(schemaVersion, predicate)))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePayloadMappingDefinition that = (ImmutablePayloadMappingDefinition) o;
        return Objects.equals(definitions, that.definitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definitions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "definitions=" + definitions +
                "]";
    }
}
