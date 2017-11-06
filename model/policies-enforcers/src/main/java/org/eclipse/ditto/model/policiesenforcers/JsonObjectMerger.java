/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Package-private function to merge 2 Json objects into 1.
 */
@Immutable
final class JsonObjectMerger implements BiFunction<JsonObject, JsonObject, JsonObject> {

    /**
     * Merge 2 JSON objects recursively into one. In case of conflict, the first object is more important.
     *
     * @param jsonObject1 the first json object to merge, overrides conflicting fields.
     * @param jsonObject2 the second json object to merge.
     * @return the merged json object.
     */
    @Override
    public JsonObject apply(final JsonObject jsonObject1, final JsonObject jsonObject2) {
        return mergeJsonObjects(jsonObject1, jsonObject2);
    }

    private static JsonObject mergeJsonObjects(final JsonObject object1, final JsonObject object2) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        // add fields of jsonObject1
        object1.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value1 = jsonField.getValue();
            final Optional<JsonValue> maybeValue2 = object2.getValue(key);
            if (maybeValue2.isPresent()) {
                builder.set(key, mergeJsonValues(value1, maybeValue2.get()));
            } else {
                builder.set(jsonField);
            }
        });

        // add fields of jsonObject2 not present in jsonObject0
        object2.forEach(jsonField -> {
            if (!object1.contains(jsonField.getKey())) {
                builder.set(jsonField);
            }
        });

        return builder.build();
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

}
