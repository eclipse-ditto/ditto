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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
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
     * Supports both legacy format (subject -> paths) and indexed format (subjects array + paths -> indices).
     *
     * @param jsonObject the JSON object from headers
     * @return map of subject IDs to accessible paths
     */
    public static Map<String, List<JsonPointer>> parsePartialAccessPaths(final JsonObject jsonObject) {
        if (jsonObject.contains(SUBJECTS_KEY) && jsonObject.contains(PATHS_KEY)) {
            return parseIndexedPartialAccessPaths(jsonObject);
        } else {
            return parseLegacyPartialAccessPaths(jsonObject);
        }
    }

    /**
     * Parses the indexed format of partial access paths.
     * Format: { "subjects": ["subj1", "subj2"], "paths": { "/path1": [0, 1], "/path2": [1] } }
     *
     * @param jsonObject the indexed JSON object
     * @return map of subject IDs to accessible paths
     */
    private static Map<String, List<JsonPointer>> parseIndexedPartialAccessPaths(final JsonObject jsonObject) {
        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();

        final JsonArray subjectsArray = jsonObject.getValue(SUBJECTS_KEY)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElse(JsonFactory.newArray());

        final Map<Integer, String> indexToSubject = new LinkedHashMap<>();
        for (int i = 0; i < subjectsArray.getSize(); i++) {
            final JsonValue subjectValue = subjectsArray.get(i).orElse(null);
            if (subjectValue != null && subjectValue.isString()) {
                indexToSubject.put(i, subjectValue.asString());
            }
        }

        final JsonObject pathsObject = jsonObject.getValue(PATHS_KEY)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonFactory.newObject());

        extractPathsFromNestedObject(pathsObject, "", result, indexToSubject);

        return result;
    }

    /**
     * Recursively extracts paths and indices from a potentially nested JSON object.
     * Handles the case where JsonKey.of("/path") creates nested structure.
     *
     * @param jsonObject the JSON object (may be nested)
     * @param currentPath the current path prefix (for recursion)
     * @param result the result map to populate
     * @param indexToSubject the index to subject ID mapping
     */
    private static void extractPathsFromNestedObject(
            final JsonObject jsonObject,
            final String currentPath,
            final Map<String, List<JsonPointer>> result,
            final Map<Integer, String> indexToSubject) {

        for (final JsonField field : jsonObject) {
            final String key = field.getKey().toString();
            final String fullPath = currentPath.isEmpty() ? "/" + key : currentPath + "/" + key;
            final JsonValue value = field.getValue();

            if (value.isArray()) {
                final JsonArray indicesArray = value.asArray();
                final JsonPointer path = JsonPointer.of(fullPath);
                for (final JsonValue idxValue : indicesArray) {
                    if (idxValue.isNumber()) {
                        final int idx = idxValue.asInt();
                        final String subjectId = indexToSubject.get(idx);
                        if (subjectId != null) {
                            result.computeIfAbsent(subjectId, k -> new ArrayList<>()).add(path);
                        }
                    }
                }
            } else if (value.isObject()) {
                extractPathsFromNestedObject(value.asObject(), fullPath, result, indexToSubject);
            }
        }
    }

    /**
     * Parses the legacy format of partial access paths.
     * Format: { "subject1": ["/path1", "/path2"], "subject2": ["/path3"] }
     *
     * @param jsonObject the legacy JSON object
     * @return map of subject IDs to accessible paths
     */
    private static Map<String, List<JsonPointer>> parseLegacyPartialAccessPaths(final JsonObject jsonObject) {
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

