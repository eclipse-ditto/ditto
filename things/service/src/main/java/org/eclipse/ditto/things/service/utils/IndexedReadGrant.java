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
package org.eclipse.ditto.things.service.utils;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Immutable model representing indexed read grants.
 * Uses integer indices to reference subjects, reducing header size.
 */
public record IndexedReadGrant(
        Map<String, List<Integer>> pathToIndices,
        Map<Integer, String> subjectsByIndex
) {

    /**
     * Creates an empty IndexedReadGrant.
     *
     * @return empty IndexedReadGrant
     */
    public static IndexedReadGrant empty() {
        return new IndexedReadGrant(Map.of(), Map.of());
    }

    /**
     * Converts the paths mapping to JSON format for the read grant header.
     * Format: { "/path1": [0, 1], "/path2": [1], ... }
     * Note: Paths are stored without leading slashes internally to avoid nested JSON structure,
     * but are output with leading slashes in the header format.
     *
     * @return JSON object with path -> [indices] mapping
     */
    public JsonObject pathsToJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (final Map.Entry<String, List<Integer>> entry : pathToIndices.entrySet()) {
            final String path = entry.getKey();
            final String pathWithSlash = "/" + path;
            final JsonKey pathKey = JsonKey.of(pathWithSlash);
            final JsonArrayBuilder indicesBuilder = JsonFactory.newArrayBuilder();
            entry.getValue().stream().sorted().forEach(indicesBuilder::add);
            builder.set(pathKey, indicesBuilder.build());
        }
        return builder.build();
    }

    /**
     * Converts the subjects index to JSON format for the subjects header.
     * Format: { "0": "subject1", "1": "subject2", ... }
     *
     * @return JSON object with index -> subjectId mapping
     */
    public JsonObject subjectsToJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        subjectsByIndex.forEach((index, subjectId) ->
                builder.set(String.valueOf(index), JsonFactory.newValue(subjectId))
        );
        return builder.build();
    }

    /**
     * Checks if this indexed grant is empty.
     *
     * @return true if no grants are present
     */
    public boolean isEmpty() {
        return pathToIndices.isEmpty() && subjectsByIndex.isEmpty();
    }
}

