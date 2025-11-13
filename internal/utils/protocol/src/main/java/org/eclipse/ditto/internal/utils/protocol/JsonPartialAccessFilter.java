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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Shared utility for filtering JSON objects based on partial access paths.
 * Used by both Gateway (SSE) and Connectivity (Kafka/WebSocket) services.
 */
public final class JsonPartialAccessFilter {

    private static final JsonPointer ROOT_PATH = JsonPointer.empty();

    private JsonPartialAccessFilter() {
        // No instantiation
    }

    /**
     * Parses a JSON object containing partial access paths from headers.
     *
     * @param jsonObject the JSON object from headers
     * @return map of subject IDs to accessible paths
     */
    public static Map<String, List<JsonPointer>> parsePartialAccessPaths(final JsonObject jsonObject) {
        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();
        for (final JsonField field : jsonObject) {
            final String subjectId = field.getKey().toString();
            if (field.getValue().isArray()) {
                final List<JsonPointer> paths = field.getValue().asArray().stream()
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .map(JsonPointer::of)
                        .toList();
                result.put(subjectId, paths);
            }
        }
        return result;
    }

    /**
     * Filters a JSON object to only include paths that are explicitly in {@code accessiblePaths}
     * or are parents of paths in {@code accessiblePaths}.
     *
     * @param jsonObject the JSON object to filter
     * @param accessiblePaths the set of accessible JsonPointer paths
     * @return filtered JSON object
     */
    public static JsonObject filterJsonByPaths(final JsonObject jsonObject,
            final Set<JsonPointer> accessiblePaths) {

        if (accessiblePaths.contains(ROOT_PATH)) {
            return jsonObject;
        }

        if (accessiblePaths.isEmpty()) {
            return JsonFactory.newObject();
        }

        final PathTrie pathTrie = PathTrie.fromPaths(accessiblePaths);

        final JsonObject filteredRoot = filterJsonRecursive(jsonObject, ROOT_PATH, accessiblePaths, pathTrie);

        if (filteredRoot.isEmpty()) {
            return JsonFactory.newObject();
        }

        return filteredRoot;
    }

    /**
     * Recursively filters a JSON object based on accessible paths.
     * Uses a PathTrie for efficient prefix matching.
     */
    private static JsonObject filterJsonRecursive(final JsonObject jsonObject,
            final JsonPointer currentPath,
            final Set<JsonPointer> accessiblePaths,
            final PathTrie pathTrie) {

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        for (final JsonField field : jsonObject) {
            final JsonPointer fieldPath = currentPath.isEmpty()
                    ? JsonPointer.of("/" + field.getKey())
                    : currentPath.append(JsonPointer.of("/" + field.getKey()));

            boolean isAccessible = pathTrie.isExactMatch(fieldPath);

            if (!isAccessible) {
                isAccessible = pathTrie.hasAccessibleDescendant(fieldPath);
            }

            if (isAccessible) {
                if (field.getValue().isObject()) {
                    final JsonObject filteredNested = filterJsonRecursive(
                            field.getValue().asObject(), fieldPath, accessiblePaths, pathTrie);
                    if (!filteredNested.isEmpty()) {
                        builder.set(field.getKey(), filteredNested);
                    }
                } else {
                    builder.set(field);
                }
            }
        }

        return builder.build();
    }
}

