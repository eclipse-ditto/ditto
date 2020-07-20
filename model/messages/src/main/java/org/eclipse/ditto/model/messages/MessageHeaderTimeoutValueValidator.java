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

import java.time.Duration;
import java.time.format.DateTimeParseException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderValueValidators;
import org.eclipse.ditto.model.base.headers.ValueValidator;

/**
 * This validator parses a CharSequence to a {@code long} value and checks if the parsed long is a valid
 * {@link Duration} of seconds.
 * If validation fails, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class MessageHeaderTimeoutValueValidator extends AbstractHeaderValueValidator {

    private MessageHeaderTimeoutValueValidator() {
        super(valueType -> long.class.equals(valueType) || Long.class.equals(valueType));
    }

    /**
     * Returns an instance of {@code TimeoutValueValidator}.
     *
     * @return the instance.
     */
    static ValueValidator getInstance() {
        return HeaderValueValidators.getLongValidator().andThen(new MessageHeaderTimeoutValueValidator());
    }

    @SuppressWarnings("squid:S2201")
    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            Duration.ofSeconds(parseLong(value));
        } catch (final DateTimeParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "timeout").build();
        }
    }

    private static long parseLong(final CharSequence value) {

        // at this point the value was already checked to be a long
        return Long.parseLong(value.toString());
    }

}
