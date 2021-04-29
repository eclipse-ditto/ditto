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
package org.eclipse.ditto.messages.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;

/**
 * This validator parses a CharSequence to a {@link java.time.OffsetDateTime}.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
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
