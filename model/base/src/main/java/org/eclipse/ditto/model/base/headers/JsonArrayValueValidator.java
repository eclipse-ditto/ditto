/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;

/**
 * This validator parses a CharSequence to a {@link JsonArray} and ensures that the JSON array <em>contains only string
 * items.</em>
 * If validation fails, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class JsonArrayValueValidator extends AbstractHeaderValueValidator {

    private JsonArrayValueValidator() {
        super(JsonArray.class::equals);
    }

    /**
     * Returns an instance of {@code JsonArrayValueValidator}.
     *
     * @return the instance.
     */
    static JsonArrayValueValidator getInstance() {
        return new JsonArrayValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        if (containsNonStringArrayValues(tryToParseJsonArray(definition, value.toString()))) {
            final String msgTemplate = "JSON array for <{0}> contained non-string values!";
            throw DittoHeaderInvalidException
                    .newCustomMessageBuilder(MessageFormat.format(msgTemplate, definition.getKey()))
                    .build();
        }
    }

    private static JsonArray tryToParseJsonArray(final HeaderDefinition definition, final String value) {
        try {
            return JsonArray.of(value);
        } catch (final JsonParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "JSON array").build();
        }
    }

    private static boolean containsNonStringArrayValues(final Iterable<JsonValue> jsonArray) {
        for (final JsonValue jsonValue : jsonArray) {
            if (!jsonValue.isString()) {
                return true;
            }
        }
        return false;
    }

}
