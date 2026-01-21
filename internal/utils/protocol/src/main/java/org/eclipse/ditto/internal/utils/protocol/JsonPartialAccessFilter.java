/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
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

    private static final String SUBJECTS_KEY = "subjects";
    private static final String PATHS_KEY = "paths";

    private JsonPartialAccessFilter() {
        // No instantiation
    }

    /**
     * Parses a JSON object containing partial access paths from headers.
     * Supports indexed format: { "subjects": ["subj1", "subj2"], "paths": { "/path1": [0, 1], "/path2": [1] } }
     *
     * @param jsonObject the JSON object from headers (must contain "subjects" and "paths" keys)
     * @return map of subject IDs to accessible paths
     */
    public static Map<String, List<JsonPointer>> parsePartialAccessPaths(final JsonObject jsonObject) {
        if (!jsonObject.contains(SUBJECTS_KEY) || !jsonObject.contains(PATHS_KEY)) {
            return Map.of();
        }
        final IndexedPartialAccessPaths indexed = parseIndexedPartialAccessPaths(jsonObject);
        return expandIndexed(indexed);
    }

    /**
     * Expands an IndexedPartialAccessPaths into a map of subject IDs to their accessible paths.
     *
     * @param indexed the indexed partial access paths
     * @return map of subject IDs to accessible paths
     */
    public static Map<String, List<JsonPointer>> expandIndexed(final IndexedPartialAccessPaths indexed) {
        if (indexed == null || indexed.isEmpty()) {
            return Map.of();
        }

        final Map<String, List<JsonPointer>> subjectMap = new LinkedHashMap<>();

        for (final Map.Entry<JsonPointer, List<Integer>> entry : indexed.paths().entrySet()) {
            final JsonPointer pointer = entry.getKey();
            for (final Integer idx : entry.getValue()) {
                if (idx >= 0 && idx < indexed.subjects().size()) {
                    final String subject = indexed.subjects().get(idx);
                    subjectMap.computeIfAbsent(subject, k -> new ArrayList<>()).add(pointer);
                }
            }
        }

        return subjectMap;
    }

    /**
     * Parses the indexed format of partial access paths.
     * Format: { "subjects": ["subj1", "subj2"], "paths": { "/path1": [0, 1], "/path2": [1] } }
     *
     * @param jsonObject the indexed JSON object
     * @return IndexedPartialAccessPaths model
     */
    static IndexedPartialAccessPaths parseIndexedPartialAccessPaths(final JsonObject jsonObject) {
        final List<String> subjects = jsonObject.getValue(SUBJECTS_KEY)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElse(JsonFactory.newArray())
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .toList();

        final JsonObject pathsObject = jsonObject.getValue(PATHS_KEY)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());

        final Map<JsonPointer, List<Integer>> paths = new LinkedHashMap<>();
        extractPathsRecursively(pathsObject, JsonPointer.empty(), paths);

        return new IndexedPartialAccessPaths(subjects, paths);
    }

    /**
     * Recursively extracts paths and indices from a potentially nested JSON object.
     * Handles the case where JsonKey.of("/path") creates nested structure.
     *
     * @param jsonObject the JSON object (may be nested)
     * @param currentPath the current path prefix (for recursion)
     * @param paths the result map to populate
     */
    private static void extractPathsRecursively(
            final JsonObject jsonObject,
            final JsonPointer currentPath,
            final Map<JsonPointer, List<Integer>> paths) {

        for (final JsonField field : jsonObject) {
            final String keyString = field.getKey().toString();
            if (keyString.isEmpty()) {
                if (field.getValue().isObject()) {
                    extractPathsRecursively(field.getValue().asObject(), currentPath, paths);
                }
                continue;
            }
            
            final JsonPointer fieldPath = currentPath.isEmpty()
                    ? JsonPointer.of(keyString)
                    : currentPath.append(JsonPointer.of(keyString));
            final JsonValue value = field.getValue();

            if (value.isArray()) {
                final List<Integer> indexes = value.asArray()
                        .stream()
                        .filter(JsonValue::isNumber)
                        .map(JsonValue::asInt)
                        .toList();
                if (!indexes.isEmpty()) {
                    paths.put(fieldPath, indexes);
                }
            } else if (value.isObject()) {
                extractPathsRecursively(value.asObject(), fieldPath, paths);
            }
        }
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

        final JsonObject filteredRoot = filterJsonRecursive(jsonObject, ROOT_PATH, pathTrie);

        if (filteredRoot.isEmpty()) {
            return JsonFactory.newObject();
        }

        return filteredRoot;
    }

    /**
     * Recursively filters a JSON object based on accessible paths.
     * Uses a PathTrie for efficient prefix matching.
     *
     * @param jsonObject the JSON object to filter
     * @param currentPath the current path in the JSON structure
     * @param pathTrie the PathTrie for efficient path matching
     * @return filtered JSON object
     */
    private static JsonObject filterJsonRecursive(final JsonObject jsonObject,
            final JsonPointer currentPath,
            final PathTrie pathTrie) {

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        for (final JsonField field : jsonObject) {
            final JsonPointer fieldPath = currentPath.isEmpty()
                    ? JsonPointer.of("/" + field.getKey())
                    : currentPath.append(JsonPointer.of("/" + field.getKey()));

            final boolean isAccessible = pathTrie.isExactMatch(fieldPath) ||
                    pathTrie.hasAccessibleDescendant(fieldPath);

            if (isAccessible) {
                if (field.getValue().isObject()) {
                    final JsonObject filteredNested = filterJsonRecursive(
                            field.getValue().asObject(), fieldPath, pathTrie);
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

