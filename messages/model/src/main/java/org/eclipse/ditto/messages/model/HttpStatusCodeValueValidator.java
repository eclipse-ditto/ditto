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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderValueValidators;
import org.eclipse.ditto.base.model.headers.ValueValidator;

/**
 * This validator parses a CharSequence to an integer value and checks if that int is a known {@link HttpStatus}.
 * If validation fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class HttpStatusCodeValueValidator extends AbstractHeaderValueValidator {

    private static final ValueValidator INSTANCE = HeaderValueValidators.getIntValidator()
            .andThen(new HttpStatusCodeValueValidator());

    private HttpStatusCodeValueValidator() {
        super(valueType -> int.class.equals(valueType) || Integer.class.equals(valueType));
    }

    /**
     * Returns an instance of {@code HttpStatusCodeValueValidator}.
     *
     * @return the instance.
     */
    static ValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            HttpStatus.getInstance(parseInt(value));
        } catch (final HttpStatusCodeOutOfRangeException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "HTTP status code")
                    .cause(e)
                    .build();
        }
    }

    private static int parseInt(final CharSequence value) {

        // at this point the value was already checked to be an int
        return Integer.parseInt(value.toString());
    }

}
