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
package org.eclipse.ditto.signals.base;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;


/**
 * Classes which implement this interface are able to parse {@link T} from a {@link JsonObject} and
 * {@link DittoHeaders}.
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
     * @return the parsed instance of {@link T}
     * @throws JsonTypeNotParsableException if the {@code jsonObject}'s {@code type} was unknown to the parser.
     */
    T parse(JsonObject jsonObject, DittoHeaders dittoHeaders);

}
