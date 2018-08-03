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
package org.eclipse.ditto.services.utils.jwt;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.lang.Assert;
import io.jsonwebtoken.lang.Strings;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

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
    public Object deserialize(byte[] bytes) {

        Assert.notNull(bytes, "JSON byte array cannot be null");

        if (bytes.length == 0) {
            throw new DeserializationException("Invalid JSON: zero length byte array.");
        }

        try {
            return parse(new String(bytes, Strings.UTF_8));
        } catch (final Exception e) {
            throw new DeserializationException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    private static Object parse(final String json) {

        return toJavaObject(Json.parse(json));
    }

    private static Map<String, Object> toJavaMap(final JsonObject jsonObject) {
        return StreamSupport.stream(jsonObject.spliterator(), false)
                .collect(Collectors.toMap(
                        JsonObject.Member::getName,
                        member -> toJavaObject(member.getValue())));
    }

    private static List<Object> toJavaList(final JsonArray jsonArray) {
        return StreamSupport.stream(jsonArray.spliterator(), false)
                .map(JjwtDeserializer::toJavaObject)
                .collect(Collectors.toList());
    }

    private static Object toJavaObject(final JsonValue jsonValue) {
        Object result;
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
