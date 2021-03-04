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
package org.eclipse.ditto.model.messages;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * This validator parses a CharSequence to a {@link java.time.OffsetDateTime}.
 * If parsing fails, a {@link org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class TimestampValueValidator extends AbstractHeaderValueValidator {

    private static final TimestampValueValidator INSTANCE = new TimestampValueValidator();

    private TimestampValueValidator() {
        super(String.class::equals);
    }

    /**
     * Returns an instance of {@code TimestampValueValidator}.
     *
     * @return the instance.
     */
    static TimestampValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            parseOffsetDateTime(value);
        } catch (final DateTimeParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "timestamp").build();
        }
    }

    @SuppressWarnings("squid:S2201")
    private static void parseOffsetDateTime(final CharSequence value) {
        OffsetDateTime.parse(value.toString());
    }

}
