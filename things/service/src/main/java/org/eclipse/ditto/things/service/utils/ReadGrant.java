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
package org.eclipse.ditto.things.service.utils;

import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable model representing read grants: which subjects can read which paths.
 * Maps JsonPointer paths to sets of subject IDs.
 */
public record ReadGrant(Map<JsonPointer, Set<String>> pointerToSubjects) {

    /**
     * Creates an empty ReadGrant.
     *
     * @return empty ReadGrant
     */
    public static ReadGrant empty() {
        return new ReadGrant(Map.of());
    }

    /**
     * Checks if this grant is empty.
     *
     * @return true if no grants are present
     */
    public boolean isEmpty() {
        return pointerToSubjects.isEmpty();
    }

    /**
     * Converts this read grant to a JSON object mapping paths to arrays of subject ID strings.
     * <p>
     * Example output: {@code {"/attributes": ["connection:foo"], "/definition": ["connection:foo", "connection:bar"]}}
     * <p>
     * This format is required by
     * {@code DittoCachingSignalEnrichmentFacade.filterAskedForFieldSelectorToGrantedFields()},
     * which matches authorization subject IDs directly against the array values in the
     * {@code PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT} header.
     *
     * @return JSON object with path keys and string subject ID arrays as values
     * @since 3.9.0
     */
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (final Map.Entry<JsonPointer, Set<String>> entry : pointerToSubjects.entrySet()) {
            final JsonKey pathKey = JsonKey.of(entry.getKey().toString());
            final JsonArray subjectsArray = entry.getValue().stream()
                    .sorted()
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
            builder.set(pathKey, subjectsArray);
        }
        return builder.build();
    }
}

