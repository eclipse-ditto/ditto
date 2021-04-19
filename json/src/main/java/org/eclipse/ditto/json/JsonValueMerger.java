/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * Package-private function to merge 2 {@link org.eclipse.ditto.json.JsonValue}s into 1.
 * Implementation is conform to <a href="https://tools.ietf.org/html/rfc7396">RFC 7396</a>.
 */
@Immutable
final class JsonValueMerger extends AbstractJsonMerger {

    private JsonValueMerger() {
    }

    /**
     * Merge 2 JSON values recursively into one. In case of conflict, the first value is more important.
     *
     * @param value1 the first json value to merge, overrides conflicting fields.
     * @param value2 the second json value to merge.
     * @return the merged json value.
     */
    public static JsonValue mergeJsonValues(final JsonValue value1, final JsonValue value2) {
        final JsonValue result;
        if (areJsonObjects(value1, value2)) {
            result = mergeJsonObjects(value1.asObject(), value2.asObject());
        } else if (areJsonArrays(value1, value2)) {
            result = value1.asArray();
        } else {
            if (value1.isObject()) {
                result = filterNullValues(value1.asObject());
            } else {
                result = value1;
            }
        }

        return result;
    }

    private static JsonObject mergeJsonObjects(final JsonObject jsonObject1, final JsonObject jsonObject2) {

        if(jsonObject1.isNull() || (jsonObject1.isNull() && jsonObject2.isNull())) {
            return JsonFactory.nullObject();
        }

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        // add fields of jsonObject1
        jsonObject1.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value1 = jsonField.getValue();
            final Optional<JsonValue> maybeValue2 = jsonObject2.getValue(key);

            if (value1.isNull()) {
                return;
            }
            if (maybeValue2.isPresent()) {
                builder.set(key, mergeJsonValues(value1, maybeValue2.get()));
            } else {
                if (value1.isObject()) {
                    builder.set(key, filterNullValues(value1.asObject()));
                } else {
                    builder.set(jsonField);
                }
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

}
