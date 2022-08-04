/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonRuntimeException;

/**
 * This validator parses a CharSequence to a {@link org.eclipse.ditto.json.JsonFieldSelector}.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} with detailed description is thrown.
 *
 * @since 3.0.0
 */
@Immutable
final class JsonFieldSelectorValidator extends AbstractHeaderValueValidator {

    /**
     * Don't configure URL decoding as JsonParseOptions because Akka-Http already decodes the fields-param and we would
     * decode twice.
     */
    private static final JsonParseOptions JSON_FIELD_SELECTOR_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    private JsonFieldSelectorValidator() {
        super(String.class::equals);
    }

    static JsonFieldSelectorValidator getInstance() {
        return new JsonFieldSelectorValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            JsonFactory.newFieldSelector(value.toString(), JSON_FIELD_SELECTOR_PARSE_OPTIONS);
        } catch (final JsonFieldSelectorInvalidException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value,
                            "field selector")
                    .description(getDescription(e))
                    .cause(e)
                    .build();
        }
    }

    private static String getDescription(final JsonRuntimeException jsonRuntimeException) {
        final String message = jsonRuntimeException.getMessage();
        return jsonRuntimeException.getDescription().map(description -> message + " " + description).orElse(message);
    }

}
