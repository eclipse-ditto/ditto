/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Package-private visitor that descends into objects and arrays.
 */
interface JsonObjectVisitor<T> {

    T nullValue(JsonPointer key);

    T bool(JsonPointer key, boolean value);

    T string(JsonPointer key, String value);

    T number(JsonPointer key, JsonNumber value);

    T array(JsonPointer key, Stream<T> values);

    T object(JsonPointer key, Stream<T> values);

    default T eval(final JsonValue value) {
        return value(JsonPointer.empty(), value);
    }

    default T value(final JsonPointer key, final JsonValue value) {
        final T result;
        if (value.isNull()) {
            result = nullValue(key);
        } else if (value.isObject()) {
            result = object(key, value.asObject()
                    .stream()
                    .map(jsonField -> value(key.addLeaf(jsonField.getKey()), jsonField.getValue())));
        } else if (value.isArray()) {
            result = array(key, value.asArray().stream().map(element -> value(key, element)));
        } else if (value.isString()) {
            result = string(key, value.asString());
        } else if (value.isBoolean()) {
            result = bool(key, value.asBoolean());
        } else if (value.isNumber()) {
            result = number(key, (JsonNumber) value);
        } else {
            throw new UnsupportedOperationException("Unsupported JSON value: " + value);
        }
        return result;
    }
}
