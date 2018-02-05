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
package org.eclipse.ditto.model.amqpbridge;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link MappingContext}.
 */
@Immutable
final class ImmutableMappingContext implements MappingContext {

    private final String contentType;
    private final String mappingEngine;
    private final Map<String, String> options;


    private ImmutableMappingContext(final String contentType, final String mappingEngine,
            final Map<String, String> options) {

        this.contentType = contentType;
        this.mappingEngine = mappingEngine;
        this.options = Collections.unmodifiableMap(new HashMap<>(options));
    }

    /**
     * Returns a new {@code ImmutableMappingContext}.
     *
     * @param contentType
     * @param mappingEngine
     * @param options
     * @return
     */
    public static ImmutableMappingContext of(final String contentType, final String mappingEngine,
            final Map<String, String> options) {
        checkNotNull(contentType, "content-type");
        checkNotNull(mappingEngine, "mapping Engine");
        checkNotNull(options, "options");

        return new ImmutableMappingContext(contentType, mappingEngine, options);
    }

    /**
     * Creates a new {@code MappingContext} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the MappingContext to be created.
     * @return a new MappingContext which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static MappingContext fromJson(final JsonObject jsonObject) {
        final String contentType = jsonObject.getValueOrThrow(JsonFields.CONTENT_TYPE);
        final String mappingEngine = jsonObject.getValueOrThrow(JsonFields.MAPPING_ENGINE);
        final Map<String, String> options = jsonObject.getValueOrThrow(JsonFields.OPTIONS).stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));

        return of(contentType, mappingEngine, options);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.CONTENT_TYPE, contentType, predicate);
        jsonObjectBuilder.set(JsonFields.MAPPING_ENGINE, mappingEngine, predicate);
        jsonObjectBuilder.set(JsonFields.OPTIONS, options.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), JsonValue.of(e.getValue())))
                .collect(JsonCollectors.fieldsToObject()), predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getMappingEngine() {
        return mappingEngine;
    }

    @Override
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableMappingContext)) {
            return false;
        }
        final ImmutableMappingContext that = (ImmutableMappingContext) o;
        return Objects.equals(contentType, that.contentType) &&
                Objects.equals(mappingEngine, that.mappingEngine) &&
                Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, mappingEngine, options);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "contentType=" + contentType +
                ", mappingEngine=" + mappingEngine +
                ", options=" + options +
                "]";
    }
}
