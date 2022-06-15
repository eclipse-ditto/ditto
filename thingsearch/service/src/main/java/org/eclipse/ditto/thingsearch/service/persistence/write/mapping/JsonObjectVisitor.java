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

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Package-private visitor that descends into objects and arrays.
 */
interface JsonObjectVisitor<T extends JsonValue> {

    Optional<T> nullValue(JsonPointer key);

    Optional<T> bool(JsonPointer key, boolean value);

    Optional<T> string(JsonPointer key, String value);

    Optional<T> number(JsonPointer key, JsonNumber value);

    Optional<T> array(JsonPointer key, Stream<JsonValue> jsonArray);

    Optional<T> object(JsonPointer key, Stream<JsonField> jsonObject);

    default Optional<T> eval(final JsonValue value) {
        return value(JsonPointer.empty(), value);
    }

    default Optional<T> value(final JsonPointer key, final JsonValue value) {
        final Optional<T> result;
        if (value.isNull()) {
            result = nullValue(key);
        } else if (value.isObject()) {
            result = object(key, value.asObject()
                    .stream()
                    .flatMap(jsonField -> value(key.addLeaf(jsonField.getKey()), jsonField.getValue())
                            .map(newValue -> JsonField.newInstance(jsonField.getKey(), newValue)).stream()));
        } else if (value.isArray()) {
            result = array(key, value.asArray().stream().flatMap(element -> value(key, element).stream()));
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
