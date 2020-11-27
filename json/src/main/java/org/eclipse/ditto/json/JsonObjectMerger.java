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

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Package-private function to merge 2 Json objects into 1.
 */
@Immutable
final class JsonObjectMerger {

    /**
     * Merge 2 JSON objects recursively into one. In case of conflict, the first object is more important.
     *
     * @param jsonObject1 the first json object to merge, overrides conflicting fields.
     * @param jsonObject2 the second json object to merge.
     * @return the merged json object.
     */
    public static JsonObject mergeJsonObjects(final JsonObject jsonObject1, final JsonObject jsonObject2) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        if(jsonObject1.isNull() && jsonObject2.isNull()) {
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

    /**
     * Merge 2 JSON objects recursively into one and filter null values and empty objects.
     * In case of conflict, the first object is more important.
     *
     * @param jsonObject1 the first json object to merge, overrides conflicting fields.
     * @param jsonObject2 the second json object to merge.
     * @return the merged json object.
     */
    public static JsonObject mergeJsonObjectsAndFilterNullValuesAndEmptyObjects(final JsonObject jsonObject1, final JsonObject jsonObject2) {

        return filterNullValuesAndEmptyObjects(mergeJsonObjects(jsonObject1, jsonObject2));
    }

    private static JsonValue mergeJsonValues(final JsonValue value1, final JsonValue value2) {
        final JsonValue result;
        if (areJsonObjects(value1, value2)) {
            result = mergeJsonObjects(value1.asObject(), value2.asObject());
        } else if (areJsonArrays(value1, value2)) {
            result = mergeJsonArrays(value1.asArray(), value2.asArray());
        } else {
            result = value1;
        }

        return result;
    }

    private static boolean areJsonObjects(final JsonValue value1, final JsonValue value2) {
        return value1.isObject() && value2.isObject();
    }

    private static boolean areJsonArrays(final JsonValue value1, final JsonValue value2) {
        return value1.isArray() && value2.isArray();
    }

    private static JsonArray mergeJsonArrays(final JsonArray array1, final JsonArray array2) {
        final JsonArray longerArray = array1.getSize() >= array2.getSize() ? array1 : array2;
        final int longerSize = longerArray.getSize();
        final int shorterSize = Math.min(array1.getSize(), array2.getSize());
        final JsonArrayBuilder builder = JsonFactory.newArrayBuilder();
        for (int i = 0; i < shorterSize; ++i) {
            builder.add(mergeJsonValues(getOrThrow(array1, i), getOrThrow(array2, i)));
        }
        for (int i = shorterSize; i < longerSize; ++ i) {
            builder.add(getOrThrow(longerArray, i));
        }
        return builder.build();
    }

    private static JsonValue getOrThrow(final JsonArray jsonArray, final int index) {
        return jsonArray.get(index).orElseThrow(() -> {
            final String msgPattern = "JsonArray did not contain a value for index <{0}>!";
            return new NullPointerException(MessageFormat.format(msgPattern, index));
        });
    }

    private static JsonObject filterNullValuesAndEmptyObjects(final JsonObject jsonObject) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        jsonObject.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value = jsonField.getValue();
            final JsonValue result;

            if (value.isNull()) {
                return;
            } else if (value.isObject()) {
                result = filterNullValuesAndEmptyObjects(value.asObject());
                if (result.asObject().isEmpty()) {
                    return;
                }
            } else {
                result = value;
            }
            builder.set(key, result);
        });

        return builder.build();
    }

}
