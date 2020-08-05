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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.metadata.MetadataHeaderKey;

/**
 * Validates the parts of a metadata header, namely the key and the associated value.
 * If validation fails a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class MetadataHeaderValidator {

    private final String key;
    private final String value;

    private MetadataHeaderValidator(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns an instance of {@code MetadataHeaderValidator}.
     *
     * @param key the key to be validated.
     * @param value the value to be validated.
     * @return the instance.
     */
    public static MetadataHeaderValidator of(final CharSequence key, final CharSequence value) {
        return new MetadataHeaderValidator(key.toString(), value.toString());
    }

    void validate() {
        validateKey();
        validateValue();
    }

    private void validateKey() {
        try {
            MetadataHeaderKey.parse(key);
        } catch (final IllegalArgumentException e) {
            final String msgPattern = "The metadata header key <{0}> is invalid!";
            throw DittoHeaderInvalidException.newCustomMessageBuilder(MessageFormat.format(msgPattern, key))
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private void validateValue() {
        try {
            JsonFactory.readFrom(value);
        } catch (final JsonParseException e) {
            final String msgPattern = "The metadata header value <{0}> for key <{1}> is invalid!";
            throw DittoHeaderInvalidException.newCustomMessageBuilder(MessageFormat.format(msgPattern, value, key))
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

}
