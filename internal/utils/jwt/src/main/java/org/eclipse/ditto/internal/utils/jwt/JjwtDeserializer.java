/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.jwt;

import java.io.Reader;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.JsonValueParser;

import io.jsonwebtoken.io.AbstractDeserializer;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;

/**
 * JJWT library Deserializer implementation which translates JSON strings to Java Objects (e.g. Maps).
 */
@Immutable
public final class JjwtDeserializer extends AbstractDeserializer<Map<String, ?>> {

    private static Deserializer<Map<String, ?>> instance;

    /**
     * @return the instance of {@link JjwtDeserializer}.
     */
    public static Deserializer<Map<String, ?>> getInstance() {
        if (instance == null) {
            instance = new JjwtDeserializer();
        }
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, ?> doDeserialize(final Reader reader) throws Exception {
        try {
            return (Map<String, ?>) toJavaObject(JsonValueParser.fromReader().apply(reader));
        } catch (final Exception e) {
            throw new DeserializationException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> toJavaMap(final JsonObject jsonObject) {
        return jsonObject.stream()
                .collect(Collectors.toMap(JsonField::getKeyName, field -> toJavaObject(field.getValue())));
    }

    private static List<Object> toJavaList(final JsonArray jsonArray) {
        return jsonArray.stream()
                .map(JjwtDeserializer::toJavaObject)
                .toList();
    }

    private static Object toJavaObject(final JsonValue jsonValue) {
        final Object result;

        if (null == jsonValue) {
            result = null;
        } else if (jsonValue.isNull()) {
            result = null;
        } else if (jsonValue.isString()) {
            result = jsonValue.asString();
        } else if (jsonValue.isBoolean()) {
            result = jsonValue.asBoolean();
        } else if (jsonValue.isNumber()) {
            if (jsonValue.isInt()) {
                result = jsonValue.asInt();
            } else if (jsonValue.isLong()) {
                result = jsonValue.asLong();
            } else {
                result = jsonValue.asDouble();
            }
        } else if (jsonValue.isObject()) {
            result = toJavaMap(jsonValue.asObject());
        } else if (jsonValue.isArray()) {
            result = toJavaList(jsonValue.asArray());
        } else {
            throw new IllegalStateException(
                    MessageFormat.format("Failed to convert {0} to a Java object", jsonValue));
        }

        return result;
    }

}
