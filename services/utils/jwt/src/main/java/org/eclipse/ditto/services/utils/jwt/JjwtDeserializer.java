/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.jwt;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;

import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;

/**
 * JJWT library Deserializer implementation which translates JSON strings to Java Objects (e.g. Maps).
 */
@Immutable
public final class JjwtDeserializer implements Deserializer {

    private static Deserializer instance;

    /**
     * @return the instance of {@link JjwtDeserializer}.
     */
    public static Deserializer getInstance() {
        if (instance == null) {
            instance = new JjwtDeserializer();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object deserialize(final byte[] bytes) {

        ConditionChecker.argumentNotNull(bytes, "JSON byte array cannot be null");

        if (bytes.length == 0) {
            throw new DeserializationException("Invalid JSON: zero length byte array.");
        }

        try {
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new DeserializationException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    private static Object parse(final String json) {

        return toJavaObject(JsonFactory.readFrom(json));
    }

    private static Map<String, Object> toJavaMap(final JsonObject jsonObject) {
        return jsonObject.stream()
                .collect(Collectors.toMap(
                        JsonField::getKeyName,
                        field -> toJavaObject(field.getValue())));
    }

    private static List<Object> toJavaList(final JsonArray jsonArray) {
        return jsonArray.stream()
                .map(JjwtDeserializer::toJavaObject)
                .collect(Collectors.toList());
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
            final Double doubleValue = jsonValue.asDouble();
            if (doubleValue.intValue() == doubleValue) {
                result = doubleValue.intValue();
            } else if (doubleValue.longValue() == doubleValue) {
                result = doubleValue.longValue();
            } else {
                result = doubleValue;
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
