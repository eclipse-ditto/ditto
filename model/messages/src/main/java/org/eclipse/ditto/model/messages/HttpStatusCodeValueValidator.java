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

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderValueValidators;
import org.eclipse.ditto.model.base.headers.ValueValidator;

/**
 * This validator parses a CharSequence to an integer value and checks if that int is a known {@link HttpStatusCode}.
 * If validation fails, a {@link org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class HttpStatusCodeValueValidator extends AbstractHeaderValueValidator {

    private HttpStatusCodeValueValidator() {
        super(valueType -> int.class.equals(valueType) || Integer.class.equals(valueType));
    }

    /**
     * Returns an instance of {@code HttpStatusCodeValueValidator}.
     *
     * @return the instance.
     */
    static ValueValidator getInstance() {
        return HeaderValueValidators.getIntValidator().andThen(new HttpStatusCodeValueValidator());
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final Optional<HttpStatusCode> httpStatusCodeOptional = HttpStatusCode.forInt(parseInt(value));
        if (!httpStatusCodeOptional.isPresent()) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "HTTP status code").build();
        }
    }

    private static int parseInt(final CharSequence value) {

        // at this point the value was already checked to be an int
        return Integer.parseInt(value.toString());
    }

}
