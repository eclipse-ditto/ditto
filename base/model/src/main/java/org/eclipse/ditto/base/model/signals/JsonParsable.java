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
package org.eclipse.ditto.base.model.signals;

import java.io.NotSerializableException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;


/**
 * Classes which implement this interface are able to parse {@link T} from a {@link org.eclipse.ditto.json.JsonObject} and
 * {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 *
 * @param <T> the type to parse.
 */
@FunctionalInterface
public interface JsonParsable<T> {

    /**
     * Parses an instance of {@link T} from the given {@code jsonString} and {@code dittoHeaders}.
     *
     * @param jsonString the JSON String representation to be parsed.
     * @param dittoHeaders the headers of the command to be parsed.
     * @return the parsed instance of {@link T}
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     * @throws JsonTypeNotParsableException if the {@code jsonObject}'s {@code type} was unknown to the parser.
     */
    default T parse(final String jsonString, final DittoHeaders dittoHeaders) {
        return parse(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Parses an instance of {@link T} from the given {@code jsonObject} and {@code dittoHeaders}.
     *
     * @param jsonObject the JSON representation to be parsed.
     * @param dittoHeaders the headers of the command to be parsed.
     * @param parseInnerJson the function to parse inner JSON.
     * @return the parsed instance of {@link T}
     * @throws JsonTypeNotParsableException if the {@code jsonObject}'s {@code type} was unknown to the parser.
     */
    default T parse(JsonObject jsonObject, DittoHeaders dittoHeaders, ParseInnerJson parseInnerJson) {
        return parse(jsonObject, dittoHeaders);
    }

    /**
     * Parses an instance of {@link T} from the given {@code jsonObject} and {@code dittoHeaders}.
     *
     * @param jsonObject the JSON representation to be parsed.
     * @param dittoHeaders the headers of the command to be parsed.
     * @return the parsed instance of {@link T}
     * @throws JsonTypeNotParsableException if the {@code jsonObject}'s {@code type} was unknown to the parser.
     */
    T parse(JsonObject jsonObject, DittoHeaders dittoHeaders);

    /**
     * Functional interface to parse inner JSON for nested Jsonifiable.
     */
    @FunctionalInterface
    interface ParseInnerJson {

        /**
         * Function to parse inner JSON object.
         *
         * @param jsonObject the inner JSON object.
         * @return the deserialized Jsonifiable object.
         * @throws java.io.NotSerializableException if inner JSON cannot be deserialized.
         */
        Jsonifiable<?> parseInnerJson(JsonObject jsonObject) throws NotSerializableException;
    }
}
