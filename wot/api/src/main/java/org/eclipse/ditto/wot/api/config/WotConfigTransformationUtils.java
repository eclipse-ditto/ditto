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
package org.eclipse.ditto.wot.api.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Utility class for transforming WoT validation configuration between different formats.
 * This class provides methods for converting HOCON configuration structure to the proper
 * JSON structure expected by validation config parsers.
 * <p>
 * The class is immutable and thread-safe.
 *
 * @since 3.8.0
 */
@Immutable
public final class WotConfigTransformationUtils {

    private WotConfigTransformationUtils() {
        throw new AssertionError();
    }

    /**
     * Converts a HOCON configuration structure to the proper JSON structure expected by
     * ThingValidationConfig.fromJson() and FeatureValidationConfig.fromJson() methods.
     * This version generically converts all keys from kebab-case to camelCase recursively
     * and handles flat field names by converting them to nested structures.
     *
     * @param configOverridesJson the JSON object containing config overrides in HOCON format
     * @return the transformed JSON object with camelCase keys and proper nested structure
     */
    public static JsonObject convertHoconConfigOverridesToValidationConfigJson(final JsonObject configOverridesJson) {
        return convertKeysToCamelCaseAndStructure(configOverridesJson);
    }

    private static JsonObject convertKeysToCamelCaseAndStructure(final JsonObject json) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        for (final var key : json.getKeys()) {
            final String keyStr = key.toString();
            final var value = json.getValue(key).orElseThrow();

            if (value.isObject()) {
                // Recursively process nested objects
                builder.set(kebabToCamel(keyStr), convertKeysToCamelCaseAndStructure(value.asObject()));
            } else if (value.isArray()) {
                // Recursively process arrays
                builder.set(kebabToCamel(keyStr), convertArrayKeysToCamelCase(value.asArray()));
            } else {
                // Handle flat field names that should be nested
                if (keyStr.startsWith("enforce-") || keyStr.startsWith("forbid-")) {
                    // This is a flat field that should be nested
                    final String[] parts = keyStr.split("-", 2);
                    final String prefix = parts[0]; // "enforce" or "forbid"
                    final String fieldName = kebabToCamel(parts[1]); // Convert the rest to camelCase

                    // Get or create the nested object
                    JsonObject nestedObj = builder.build().getValue(prefix).map(JsonValue::asObject).orElse(JsonFactory.newObject());
                    JsonObjectBuilder nestedBuilder = JsonFactory.newObjectBuilder(nestedObj);
                    nestedBuilder.set(fieldName, value);

                    builder.set(prefix, nestedBuilder.build());
                } else {
                    // Regular field, just convert to camelCase
                    builder.set(kebabToCamel(keyStr), value);
                }
            }
        }

        return builder.build();
    }

    private static org.eclipse.ditto.json.JsonArray convertArrayKeysToCamelCase(final org.eclipse.ditto.json.JsonArray array) {
        final org.eclipse.ditto.json.JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
        for (var value : array) {
            if (value.isObject()) {
                arrayBuilder.add(convertKeysToCamelCaseAndStructure(value.asObject()));
            } else if (value.isArray()) {
                arrayBuilder.add(convertArrayKeysToCamelCase(value.asArray()));
            } else {
                arrayBuilder.add(value);
            }
        }
        return arrayBuilder.build();
    }

    private static String kebabToCamel(String input) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : input.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
} 