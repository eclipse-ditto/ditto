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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;

/**
 * This validator parses a CharSequence to a {@link org.eclipse.ditto.json.JsonObject}.</em>
 * If validation fails, a {@link org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class JsonObjectValueValidator extends AbstractHeaderValueValidator {

    private JsonObjectValueValidator() {
        super(JsonObject.class::equals);
    }

    /**
     * Returns an instance of {@code JsonObjectValueValidator}.
     *
     * @return the instance.
     */
    static JsonObjectValueValidator getInstance() {
        return new JsonObjectValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        tryToParseJsonObject(definition, value.toString());
    }

    private static JsonObject tryToParseJsonObject(final HeaderDefinition definition, final String value) {
        try {
            return JsonObject.of(value);
        } catch (final JsonParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "JSON object").build();
        }
    }

}
