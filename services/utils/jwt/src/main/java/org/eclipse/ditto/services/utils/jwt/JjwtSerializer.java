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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import io.jsonwebtoken.lang.Collections;
import io.jsonwebtoken.lang.DateFormats;
import io.jsonwebtoken.lang.Objects;
import io.jsonwebtoken.lang.Strings;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * JJWT library Serializer implementation which translates Java Objects (e.g. Maps) to JSON strings.
 */
@Immutable
public final class JjwtSerializer<T> implements Serializer<T> {

    private static Serializer instance;

    /**
     * @return the instance of {@link JjwtSerializer}.
     */
    public static <T> Serializer<T> getInstance() {
        if (instance == null) {
            instance = new JjwtSerializer();
        }
        return instance;
    }

    @Override
    public byte[] serialize(T t) {

        try {
            return toJson(t).toString().getBytes(Strings.UTF_8);
        } catch (final SerializationException se) {
            throw se;
        } catch (final Exception e) {
            throw new SerializationException("Unable to serialize object of type " + t.getClass().getName() +
                    " to JSON: " + e.getMessage(), e);
        }
    }

    private static JsonValue toJson(final Object input) {

        if (input == null) {
            return Json.NULL;
        } else if (input instanceof Boolean) {
            return Json.value((boolean) input);
        } else if (input instanceof Byte || input instanceof Short || input instanceof Integer) {
            return Json.value((int) input);
        } else if (input instanceof Long) {
            return Json.value((long) input);
        } else if (input instanceof Float) {
            return Json.value((float) input);
        } else if (input instanceof Double) {
            return Json.value((double) input);
        } else if (input instanceof Character || input instanceof String || input instanceof Enum) {
            return Json.value(input.toString());
        } else if (input instanceof Calendar) {
            return Json.value(DateFormats.formatIso8601(((Calendar) input).getTime()));
        } else if (input instanceof Date) {
            return Json.value(DateFormats.formatIso8601((Date) input));
        } else if (input instanceof byte[]) {
            return Json.value(Encoders.BASE64.encode((byte[]) input));
        } else if (input instanceof char[]) {
            return Json.value(new String((char[]) input));
        } else if (input instanceof Map) {
            return toJsonObject((Map<?, ?>) input);
        } else if (input instanceof Collection) {
            return toJsonArray((Collection<?>) input);
        } else if (Objects.isArray(input)) {
            return toJsonArray(Collections.arrayToList(input));
        }

        throw new SerializationException("Unable to serialize object of type " + input.getClass().getName() +
                " to JSON using known heuristics.");
    }

    private static JsonObject toJsonObject(final Map<?, ?> map) {

        final JsonObject obj = new JsonObject();
        map.forEach((key, value) -> obj.add(String.valueOf(key), toJson(value)));
        return obj;
    }

    private static JsonArray toJsonArray(final Collection<?> c) {

        final JsonArray array = new JsonArray();
        c.stream().map(JjwtSerializer::toJson).forEach(array::add);
        return array;
    }

}
