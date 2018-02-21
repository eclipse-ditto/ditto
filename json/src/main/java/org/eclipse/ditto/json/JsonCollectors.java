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
package org.eclipse.ditto.json;

import java.util.stream.Collector;

import javax.annotation.concurrent.Immutable;

/**
 * This class provides implementations of {@link Collector} that implement various useful reduction operations, such as
 * accumulating JSON fields into JSON objects etc.
 */
@Immutable
public final class JsonCollectors {

    /*
     * Inhibit instantiation.
     */
    private JsonCollectors() {
        throw new AssertionError();
    }

    /**
     * Returns a {@code Collector} that accumulates the input JSON fields into a new {@code JsonObject}.
     *
     * @return a {@code Collector} which collects all the JSON fields into a {@code JsonObject}, in encounter order.
     */
    public static Collector<JsonField, JsonObjectBuilder, JsonObject> fieldsToObject() {
        return Collector.of(JsonFactory::newObjectBuilder, JsonObjectBuilder::set, JsonObjectBuilder::setAll,
                JsonObjectBuilder::build);
    }

    /**
     * Returns a {@code Collector} that accumulates the input JSON objects into a new {@code JsonObject}.
     *
     * @return a {@code Collector} which collects all the JSON object into a {@code JsonObject}, in encounter order.
     */
    public static Collector<JsonObject, JsonObjectBuilder, JsonObject> objectsToObject() {
        return Collector.of(JsonFactory::newObjectBuilder, JsonObjectBuilder::setAll, JsonObjectBuilder::setAll,
                JsonObjectBuilder::build);
    }

    /**
     * Returns a {@code Collector} that accumulates the key names of input JSON fields into a new {@code JsonArray}. The
     * values are hereby dismissed.
     *
     * @return a {@code Collector} which collects the key names of all the JSON fields into a {@code JsonArray}, in
     * encounter order.
     */
    public static Collector<JsonField, JsonArrayBuilder, JsonArray> fieldKeysToArray() {
        return Collector.of(JsonFactory::newArrayBuilder, (arrayBuilder, field) -> arrayBuilder.add(field.getKeyName()),
                JsonArrayBuilder::addAll, JsonArrayBuilder::build);
    }

    /**
     * <p>
     * Returns a {@code Collector} that accumulates the values of input JSON fields into a new {@code JsonArray}. The
     * keys are hereby dismissed.
     * </p>
     * <p>
     * <em>Caution:</em> Due to the fact that each field of a JSON object can have a different type the resulting
     * array will also consist of values of different types!
     * </p>
     *
     * @return a {@code Collector} which collects the values of all the JSON fields into a {@code JsonArray}, in
     * encounter order.
     */
    public static Collector<JsonField, JsonArrayBuilder, JsonArray> fieldValuesToArray() {
        return Collector.of(JsonFactory::newArrayBuilder, (arrayBuilder, field) -> arrayBuilder.add(field.getValue()),
                JsonArrayBuilder::addAll, JsonArrayBuilder::build);
    }

    /**
     * Returns a {@code Collector} that accumulates the input JSON values into a new {@code JsonArray}.
     *
     * @return a {@code Collector} which collects all the JSON values into a {@code JsonArray}, in encounter order.
     */
    public static Collector<JsonValue, JsonArrayBuilder, JsonArray> valuesToArray() {
        return Collector.of(JsonFactory::newArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll,
                JsonArrayBuilder::build);
    }

}
