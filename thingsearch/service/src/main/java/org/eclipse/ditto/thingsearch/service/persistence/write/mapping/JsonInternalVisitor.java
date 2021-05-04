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

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonValue;

/**
 * Package-private visitor that descends into objects and arrays.
 */
interface JsonInternalVisitor<T> {


    T nullValue();

    T bool(boolean value);

    T string(String value);

    T number(JsonNumber value);

    T array(Stream<T> values);

    T object(Stream<Map.Entry<String, T>> values);

    default T eval(final JsonValue value) {
        final T result;
        if (value.isNull()) {
            result = nullValue();
        } else if (value.isObject()) {
            result = object(value.asObject().stream().map(jsonField ->
                    new AbstractMap.SimpleEntry<>(jsonField.getKeyName(), eval(jsonField.getValue()))));
        } else if (value.isArray()) {
            result = array(value.asArray().stream().map(this::eval));
        } else if (value.isString()) {
            result = string(value.asString());
        } else if (value.isBoolean()) {
            result = bool(value.asBoolean());
        } else if (value.isNumber()) {
            result = number((JsonNumber) value);
        } else {
            throw new UnsupportedOperationException("Unsupported JSON value: " + value);
        }
        return result;
    }
}
