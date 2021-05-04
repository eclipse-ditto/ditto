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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link MappingContext}.
 */
@Immutable
final class ImmutableMappingContext implements MappingContext {

    private final String mappingEngine;
    private final JsonObject options;
    private final Map<String, String> incomingConditions;
    private final Map<String, String> outgoingConditions;

    private ImmutableMappingContext(final Builder builder) {
        this.mappingEngine = builder.mappingEngine;
        this.options = builder.options;
        this.incomingConditions = Collections.unmodifiableMap(new HashMap<>(
                builder.incomingConditions == null ? Collections.emptyMap() : builder.incomingConditions));
        this.outgoingConditions = Collections.unmodifiableMap(new HashMap<>(
                builder.outgoingConditions == null ? Collections.emptyMap() : builder.outgoingConditions));
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

        final String mappingEngine = jsonObject.getValueOrThrow(JsonFields.MAPPING_ENGINE);
        final JsonObject options = jsonObject.getValueOrThrow(JsonFields.OPTIONS);

        final Builder builder = new Builder(mappingEngine, options);

        builder.incomingConditions(
                jsonObject.getValue(JsonFields.INCOMING_CONDITIONS).orElse(JsonObject.empty()).stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().isString() ? e.getValue().asString() : e.getValue().toString())
                        ));

        builder.outgoingConditions(
                jsonObject.getValue(JsonFields.OUTGOING_CONDITIONS).orElse(JsonObject.empty()).stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().isString() ? e.getValue().asString() : e.getValue().toString())
                        ));

        return builder.build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.MAPPING_ENGINE, mappingEngine, predicate);

        jsonObjectBuilder.set(JsonFields.OPTIONS, options, predicate);

        if (!incomingConditions.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.INCOMING_CONDITIONS, incomingConditions.entrySet().stream()
                    .map(e -> JsonField.newInstance(e.getKey(), JsonValue.of(e.getValue())))
                    .collect(JsonCollectors.fieldsToObject()), predicate);
        }

        if (!outgoingConditions.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.OUTGOING_CONDITIONS, outgoingConditions.entrySet().stream()
                    .map(e -> JsonField.newInstance(e.getKey(), JsonValue.of(e.getValue())))
                    .collect(JsonCollectors.fieldsToObject()), predicate);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public String getMappingEngine() {
        return mappingEngine;
    }

    @Override
    public JsonObject getOptionsAsJson() {
        return options;
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return incomingConditions;
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return outgoingConditions;
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
        return Objects.equals(mappingEngine, that.mappingEngine) &&
                Objects.equals(options, that.options) &&
                Objects.equals(incomingConditions, that.incomingConditions) &&
                Objects.equals(outgoingConditions, that.outgoingConditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappingEngine, options, incomingConditions, outgoingConditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mappingEngine=" + mappingEngine +
                ", options=" + options +
                ", incomingConditions=" + incomingConditions +
                ", outgoingConditions=" + outgoingConditions +
                "]";
    }

    /**
     * Builder for {@code ImmutableMappingContext}.
     *
     * @since 1.3.0
     */
    @NotThreadSafe
    static final class Builder implements MappingContextBuilder {

        private String mappingEngine;
        private JsonObject options;
        @Nullable private Map<String, String> incomingConditions;
        @Nullable private Map<String, String> outgoingConditions;

        Builder(String mappingEngine, JsonObject options) {
            this.mappingEngine = mappingEngine;
            this.options = options;
        }

        @Override
        public MappingContextBuilder mappingEngine(final String mappingEngine) {
            this.mappingEngine = mappingEngine;
            return this;
        }

        @Override
        public MappingContextBuilder options(final JsonObject options) {
            this.options = options;
            return this;
        }

        @Override
        public MappingContextBuilder incomingConditions(final Map<String, String> incomingConditions) {
            this.incomingConditions = incomingConditions;
            return this;
        }

        @Override
        public MappingContextBuilder outgoingConditions(final Map<String, String> outgoingConditions) {
            this.outgoingConditions = outgoingConditions;
            return this;
        }

        @Override
        public MappingContext build() {
            checkNotNull(mappingEngine, "mappingEngine");
            checkNotNull(options, "options");
            return new ImmutableMappingContext(this);
        }
    }
}
