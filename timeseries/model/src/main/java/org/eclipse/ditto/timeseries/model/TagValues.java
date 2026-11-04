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
package org.eclipse.ditto.timeseries.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Tag values keep the JSON type of the Thing field they were taken from, so they must be scalars: a string, a
 * number or a boolean.
 */
final class TagValues {

    private TagValues() {
        throw new AssertionError();
    }

    static boolean isTagValue(final JsonValue value) {
        // JsonNull reports isObject() and isArray() as true, so it is excluded first.
        return !value.isNull() && !value.isObject() && !value.isArray();
    }

    static Map<String, JsonValue> copyOf(final Map<String, JsonValue> tags, final String argumentName) {
        checkNotNull(tags, argumentName);
        for (final Map.Entry<String, JsonValue> entry : tags.entrySet()) {
            checkNotNull(entry.getValue(), argumentName + " value");
            if (!isTagValue(entry.getValue())) {
                throw new IllegalArgumentException("The value of " + argumentName + " <" + entry.getKey() +
                        "> must be a string, number or boolean but was: " + entry.getValue());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    static Map<String, JsonValue> fromJson(final JsonObject tagsJson, final String fieldName) {
        final Map<String, JsonValue> result = new LinkedHashMap<>(tagsJson.getSize());
        for (final JsonField field : tagsJson) {
            if (!isTagValue(field.getValue())) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message("The value of <" + fieldName + "/" + field.getKeyName() +
                                "> must be a string, number or boolean but was: " + field.getValue())
                        .build());
            }
            result.put(field.getKeyName(), field.getValue());
        }
        return result;
    }

    static JsonObject toJson(final Map<String, JsonValue> tags) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        tags.forEach((key, value) -> builder.set(JsonKey.of(key), value));
        return builder.build();
    }

}
