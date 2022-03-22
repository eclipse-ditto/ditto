/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Package-private function to merge 2 {@link org.eclipse.ditto.json.JsonObject}s into 1.
 */
@Immutable
final class JsonObjectMerger {

    private JsonObjectMerger() {}

    /**
     * Merge 2 JSON objects recursively into one. In case of conflict, the first object is more important.
     * If the JSON objects contains JSON arrays than the array from {@code jsonObject1} is used.
     *
     * @param jsonObject1 the first json object to merge, overrides conflicting fields.
     * @param jsonObject2 the second json object to merge.
     * @return the merged json object.
     */
    public static JsonObject mergeJsonObjects(final JsonObject jsonObject1, final JsonObject jsonObject2) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        if (jsonObject1.isNull() && jsonObject2.isNull()) {
            return JsonFactory.nullObject();
        }

        // add fields of jsonObject1
        jsonObject1.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value1 = jsonField.getValue();
            final Optional<JsonValue> maybeValue2 = jsonObject2.getValue(key);

            if (maybeValue2.isPresent()) {
                builder.set(key, mergeJsonValues(value1, maybeValue2.get()));
            } else {
                builder.set(jsonField);
            }
        });

        // add fields of jsonObject2 not present in jsonObject1
        jsonObject2.forEach(jsonField -> {
            if (!jsonObject1.contains(jsonField.getKey())) {
                builder.set(jsonField);
            }
        });

        return builder.build();
    }

    private static JsonValue mergeJsonValues(final JsonValue value1, final JsonValue value2) {
        final JsonValue result;
        if (value1.isObject() && value2.isObject()) {
            result = mergeJsonObjects(value1.asObject(), value2.asObject());
        } else if (value1.isArray() && value2.isArray()) {
            // take jsonArray from jsonObject1 - jsonArrays will not get merged
            result = value1.asArray();
        } else {
            result = value1;
        }

        return result;
    }

}
