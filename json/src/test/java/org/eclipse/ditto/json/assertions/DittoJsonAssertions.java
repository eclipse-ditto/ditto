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
package org.eclipse.ditto.json.assertions;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Project specific {@link Assertions} to extends the set of assertions which are provided by FEST.
 */
public class DittoJsonAssertions extends Assertions {

    /**
     * Returns an assert for the given {@link JsonValue}.
     *
     * @param jsonValue the object to be checked.
     * @return an assert for {@code jsonValue}.
     */
    public static JsonValueAssert assertThat(final JsonValue jsonValue) {
        return new JsonValueAssert(jsonValue);
    }

    /**
     * Returns an assert for the given {@link JsonArray}.
     *
     * @param jsonArray the object to be checked.
     * @return an assert for {@code jsonArray}.
     */
    public static JsonArrayAssert assertThat(final JsonArray jsonArray) {
        return new JsonArrayAssert(jsonArray);
    }

    /**
     * Returns an assert for the given {@link JsonObject}.
     *
     * @param jsonObject the object to be checked.
     * @return an assert for {@code jsonObject}.
     */
    public static JsonObjectAssert assertThat(final JsonObject jsonObject) {
        return new JsonObjectAssert(jsonObject);
    }

    /**
     * Returns an assert for verifying the given {@link JsonPointer}.
     *
     * @param jsonPointer the JSON pointer to be verified.
     * @return an assert for {@code jsonPointer}.
     */
    public static JsonPointerAssert assertThat(final JsonPointer jsonPointer) {
        return new JsonPointerAssert(jsonPointer);
    }

}
