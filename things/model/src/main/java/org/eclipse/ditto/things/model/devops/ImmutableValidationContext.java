/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.things.model.devops;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable value object representing the validation context for dynamic WoT validation configuration.
 * <p>
 * This class encapsulates the context (such as Thing ID, Feature ID, or other scoping information)
 * used to determine which dynamic validation config section applies. Instances are immutable and thread-safe.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableValidationContext implements ValidationContext {

    private static final String DITTO_HEADERS_PATTERNS_FIELD = "ditto-headers-patterns";
    private static final String THING_DEF_PATTERNS_FIELD = "thing-definition-patterns";
    private static final String FEATURE_DEF_PATTERNS_FIELD = "feature-definition-patterns";

    private static final JsonFieldDefinition<JsonArray> DITTO_HEADERS_PATTERNS_POINTER =
            JsonFactory.newJsonArrayFieldDefinition(DITTO_HEADERS_PATTERNS_FIELD);
    private static final JsonFieldDefinition<JsonArray> THING_DEF_PATTERNS_POINTER =
            JsonFactory.newJsonArrayFieldDefinition(THING_DEF_PATTERNS_FIELD);
    private static final JsonFieldDefinition<JsonArray> FEATURE_DEF_PATTERNS_POINTER =
            JsonFactory.newJsonArrayFieldDefinition(FEATURE_DEF_PATTERNS_FIELD);

    private final List<Map<String, String>> dittoHeadersPatterns;
    private final List<String> thingDefinitionPatterns;
    private final List<String> featureDefinitionPatterns;

    /**
     * Constructs a new {@code ImmutableValidationContext}.
     *
     * @param dittoHeadersPatterns list of header pattern maps.
     * @param thingDefinitionPatterns regex strings to match Thing definitions.
     * @param featureDefinitionPatterns regex strings to match Feature definitions.
     */
    private ImmutableValidationContext(
            final List<Map<String, String>> dittoHeadersPatterns,
            final List<String> thingDefinitionPatterns,
            final List<String> featureDefinitionPatterns) {
        this.dittoHeadersPatterns = Collections.unmodifiableList(dittoHeadersPatterns);
        this.thingDefinitionPatterns = Collections.unmodifiableList(thingDefinitionPatterns);
        this.featureDefinitionPatterns = Collections.unmodifiableList(featureDefinitionPatterns);
    }

    /**
     * Creates a new instance of {@code ImmutableValidationContext}.
     *
     * @param dittoHeadersPatterns list of header pattern maps.
     * @param thingDefinitionPatterns regex strings to match Thing definitions.
     * @param featureDefinitionPatterns regex strings to match Feature definitions.
     * @return a new instance with the specified values
     */
    public static ImmutableValidationContext of(
            final List<Map<String, String>> dittoHeadersPatterns,
            final List<String> thingDefinitionPatterns,
            final List<String> featureDefinitionPatterns
    ) {
        return new ImmutableValidationContext(dittoHeadersPatterns, thingDefinitionPatterns, featureDefinitionPatterns);
    }

    @Override
    public List<Map<String, String>> getDittoHeadersPatterns() {
        return dittoHeadersPatterns;
    }

    @Override
    public List<String> getThingDefinitionPatterns() {
        return thingDefinitionPatterns;
    }

    @Override
    public List<String> getFeatureDefinitionPatterns() {
        return featureDefinitionPatterns;
    }


    /**
     * Returns a JSON representation of this validation context.
     *
     * @return the JSON representation
     */
    @Override
    public JsonObject toJson() {
        final JsonArrayBuilder dittoHeaders = JsonFactory.newArrayBuilder();
        for (final Map<String, String> headerPattern : dittoHeadersPatterns) {
            final JsonObject headerObject = JsonFactory.newObjectBuilder()
                    .setAll(
                            headerPattern.entrySet().stream()
                                    .map(entry -> JsonFactory.newField(
                                            JsonFactory.newKey(entry.getKey()),
                                            JsonFactory.newValue(entry.getValue())
                                    ))
                                    .collect(Collectors.toList())
                    )
                    .build();
            dittoHeaders.add(headerObject);
        }

        final JsonObjectBuilder builder = JsonObject.newBuilder()
                .set(DITTO_HEADERS_PATTERNS_FIELD, dittoHeaders.build())
                .set(THING_DEF_PATTERNS_FIELD, JsonFactory.newArrayBuilder()
                        .addAll(thingDefinitionPatterns.stream()
                                .map(JsonFactory::newValue)
                                .collect(Collectors.toList()))
                        .build())
                .set(FEATURE_DEF_PATTERNS_FIELD, JsonFactory.newArrayBuilder()
                        .addAll(featureDefinitionPatterns.stream()
                                .map(JsonFactory::newValue)
                                .collect(Collectors.toList()))
                        .build());

        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableValidationContext} from a JSON object.
     *
     * @param json the JSON object to create the context from
     * @return a new instance created from the JSON object
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public static ImmutableValidationContext fromJson(final JsonObject json) {
        final List<Map<String, String>> headers = json.getValue(DITTO_HEADERS_PATTERNS_POINTER)
                .map(array -> array.stream()
                        .map(JsonValue::asObject)
                        .map(jsonObj -> {
                            final Map<String, String> map = new HashMap<>();
                            jsonObj.forEach(field -> map.put(field.getKey().toString(), field.getValue().asString()));
                            return map;
                        })
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        final List<String> thingDefs = json.getValue(THING_DEF_PATTERNS_POINTER)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        final List<String> featureDefs = json.getValue(FEATURE_DEF_PATTERNS_POINTER)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        return of(headers, thingDefs, featureDefs);
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableValidationContext that = (ImmutableValidationContext) o;
        return Objects.equals(dittoHeadersPatterns, that.dittoHeadersPatterns)
                && Objects.equals(thingDefinitionPatterns, that.thingDefinitionPatterns)
                && Objects.equals(featureDefinitionPatterns, that.featureDefinitionPatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeadersPatterns, thingDefinitionPatterns, featureDefinitionPatterns);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoHeadersPatterns=" + dittoHeadersPatterns +
                ", thingDefinitionPatterns=" + thingDefinitionPatterns +
                ", featureDefinitionPatterns=" + featureDefinitionPatterns +
                "]";
    }
}
