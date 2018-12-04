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
package org.eclipse.ditto.json;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Default implementation of {@link DittoJsonHandler} which uses {@link JsonArrayBuilder} and {@link JsonObjectBuilder}
 * for creating a new {@link JsonArray} or {@link JsonObject}.
 */
@NotThreadSafe
final class DefaultDittoJsonHandler extends DittoJsonHandler<JsonArrayBuilder, JsonObjectBuilder, JsonValue> {

    private JsonValue jsonValue;

    private DefaultDittoJsonHandler() {
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
    public JsonArrayBuilder startArray() {
        return ImmutableJsonArrayBuilder.newInstance();
    }

    @Override
    public JsonObjectBuilder startObject() {
        return ImmutableJsonObjectBuilder.newInstance();
    }

    @Override
    public void endNull() {
        jsonValue = ImmutableJsonNull.getInstance();
    }

    @Override
    public void endBoolean(final boolean value) {
        jsonValue = value ? ImmutableJsonBoolean.TRUE : ImmutableJsonBoolean.FALSE;
    }

    @Override
    public void endString(final String string) {
        jsonValue = ImmutableJsonString.of(string);
    }

    @Override
    public void endNumber(final String string) {
        jsonValue = getNumberFor(string);
    }

    private static JsonNumber getNumberFor(final String string) {
        if (isDecimal(string)) {
            return parseToDouble(string);
        }
        return parseToIntegerOrLong(string);
    }

    private static boolean isDecimal(final String string) {
        return string.contains(".") || string.contains("e") || string.contains("E");
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
    public void endArray(final JsonArrayBuilder arrayBuilder) {
        jsonValue = arrayBuilder.build();
    }

    @Override
    public void endObject(final JsonObjectBuilder objectBuilder) {
        jsonValue = objectBuilder.build();
    }

    @Override
    public void endArrayValue(final JsonArrayBuilder arrayBuilder) {
        if (null != jsonValue) {
            arrayBuilder.add(jsonValue);
        }
    }

    @Override
    public void endObjectValue(final JsonObjectBuilder objectBuilder, final String name) {
        if (null != jsonValue) {
            objectBuilder.set(name, jsonValue);
        }
    }

    @Override
    protected JsonValue getValue() {
        return jsonValue;
    }

}
