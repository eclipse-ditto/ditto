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

/**
 * Abstract base class for {@link JsonObjectMerger} and {@link JsonValueMerger}.
 * This class holds some helper functions.
 */
abstract class AbstractJsonMerger {

    protected AbstractJsonMerger() {
    }

    protected static boolean areJsonObjects(final JsonValue value1, final JsonValue value2) {
        return value1.isObject() && value2.isObject();
    }

    protected static boolean areJsonArrays(final JsonValue value1, final JsonValue value2) {
        return value1.isArray() && value2.isArray();
    }

    protected static JsonObject filterNullValues(final JsonObject jsonObject) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        jsonObject.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value = jsonField.getValue();
            final JsonValue result;

            if (value.isNull()) {
                return;
            } else if (value.isObject()) {
                result = filterNullValues(value.asObject());
            } else {
                result = value;
            }
            builder.set(key, result);
        });

        return builder.build();
    }

}
