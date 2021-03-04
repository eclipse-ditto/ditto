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
package org.eclipse.ditto.json;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Default implementation of {@link DittoJsonHandler} which uses {@link JsonArrayBuilder} and {@link JsonObjectBuilder}
 * for creating a new {@link JsonArray} or {@link JsonObject}.
 */
@NotThreadSafe
final class DefaultDittoJsonHandler extends DittoJsonHandler<List<JsonValue>, List<JsonField>, JsonValue> {

    private static final int DEFAULT_INITIAL_STRING_BUILDER_CAPACITY = 512;
    private static final char DELIMITER = ',';

    private final Deque<StringBuilder> stringBuilders;
    private String valueString;
    private JsonValue jsonValue;

    private DefaultDittoJsonHandler() {
        stringBuilders = new ArrayDeque<>();
        valueString = null;
        jsonValue = null;
    }

    /**
     * Returns a new instance of {@code DefaultDittoJsonHandler}.
     * <p>
     * <em>The returned instance is not safe to be re-used!</em>
     * </p>
     *
     * @return the instance.
     */
    public static DefaultDittoJsonHandler newInstance() {
        return new DefaultDittoJsonHandler();
    }

    @Override
    public List<JsonValue> startArray() {
        final StringBuilder stringBuilder = new StringBuilder(DEFAULT_INITIAL_STRING_BUILDER_CAPACITY);
        stringBuilder.append('[');
        stringBuilders.push(stringBuilder);
        return new ArrayList<>();
    }

    @Override
    public List<JsonField> startObject() {
        final StringBuilder stringBuilder = new StringBuilder(DEFAULT_INITIAL_STRING_BUILDER_CAPACITY);
        stringBuilder.append('{');
        stringBuilders.push(stringBuilder);
        return new ArrayList<>();
    }

    @Override
    public void endNull() {
        jsonValue = ImmutableJsonNull.getInstance();
        valueString = "null";
    }

    @Override
    public void endBoolean(final boolean value) {
        jsonValue = value ? ImmutableJsonBoolean.TRUE : ImmutableJsonBoolean.FALSE;
        valueString = String.valueOf(value);
    }

    @Override
    public void endString(final String string) {
        jsonValue = ImmutableJsonString.of(string);
        valueString = getEscapedJsonString(string);
    }

    private static String getEscapedJsonString(final String javaString) {
        final UnaryOperator<String> javaStringToEscapeJsonString = JavaStringToEscapedJsonString.getInstance();
        return javaStringToEscapeJsonString.apply(javaString);
    }

    @Override
    public void endNumber(final String string) {
        jsonValue = getNumberFor(string);
        valueString = string;
    }

    private static JsonNumber getNumberFor(final String string) {
        if (isDecimal(string)) {
            return parseToDouble(string);
        }
        return parseToIntegerOrLong(string);
    }

    private static boolean isDecimal(final String string) {
        for (final char c : string.toCharArray()) {
            if ('.' == c || 'e' == c || 'E' == c) {
                return true;
            }
        }
        return false;
    }

    private static ImmutableJsonDouble parseToDouble(final String string) {
        return ImmutableJsonDouble.of(Double.parseDouble(string));
    }

    private static JsonNumber parseToIntegerOrLong(final String string) {
        try {
            return parseToInteger(string);
        } catch (final NumberFormatException e) {
            return parseToLong(string);
        }
    }

    private static ImmutableJsonInt parseToInteger(final String string) {
        return ImmutableJsonInt.of(Integer.parseInt(string));
    }

    private static ImmutableJsonLong parseToLong(final String string) {
        return ImmutableJsonLong.of(Long.parseLong(string));
    }

    @Override
    public void endArrayValue(final List<JsonValue> jsonValues) {
        jsonValues.add(jsonValue);
        final StringBuilder stringBuilder = stringBuilders.peek();
        if (null != stringBuilder) {
            stringBuilder.append(valueString);
            stringBuilder.append(DELIMITER);
        }
    }

    @Override
    public void endArray(final List<JsonValue> jsonValues) {
        final StringBuilder stringBuilder = stringBuilders.poll();
        if (null != stringBuilder) {
            if (!jsonValues.isEmpty()) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }
            stringBuilder.append(']');
            valueString = stringBuilder.toString();
        }
        jsonValue = ImmutableJsonArray.of(jsonValues, valueString);
    }

    @Override
    public void endObjectValue(final List<JsonField> jsonFields, final String name) {
        final JsonField jsonField = JsonField.newInstance(name, jsonValue);
        jsonFields.add(jsonField);
        final StringBuilder stringBuilder = stringBuilders.peek();
        if (null != stringBuilder) {
            stringBuilder.append(getEscapedJsonString(name));
            stringBuilder.append(':');
            stringBuilder.append(valueString);
            stringBuilder.append(DELIMITER);
        }
    }

    @Override
    public void endObject(final List<JsonField> jsonFields) {
        final StringBuilder stringBuilder = stringBuilders.poll();
        if (null != stringBuilder) {
            if (!jsonFields.isEmpty()) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }
            stringBuilder.append('}');
            valueString = stringBuilder.toString();
        }
        final Map<String, JsonField> fieldMap = new LinkedHashMap<>(jsonFields.size());
        for (final JsonField jsonField : jsonFields) {
            fieldMap.put(jsonField.getKeyName(), jsonField);
        }
        jsonValue = ImmutableJsonObject.of(fieldMap, valueString);
    }

    @Override
    protected JsonValue getValue() {
        return jsonValue;
    }

}
