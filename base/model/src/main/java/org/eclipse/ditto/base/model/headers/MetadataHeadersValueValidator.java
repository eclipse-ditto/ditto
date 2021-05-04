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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;

/**
 * This validator parses a CharSequence to a {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders}.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} with detailed description is thrown.
 *
 * @since 1.2.0
 */
@Immutable
final class MetadataHeadersValueValidator extends AbstractHeaderValueValidator {

    private MetadataHeadersValueValidator() {
        super(JsonArray.class::equals);
    }

    static MetadataHeadersValueValidator getInstance() {
        return new MetadataHeadersValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            MetadataHeaders.parseMetadataHeaders(value);
        } catch (final JsonParseException | JsonMissingFieldException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "MetadataHeaders")
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
