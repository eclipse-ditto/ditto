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

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;

/**
 * This validator parses a CharSequence to an boolean value.
 * If parsing fails, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class BooleanValueValidator extends AbstractHeaderValueValidator {

    private BooleanValueValidator() {
        super(valueType -> boolean.class.equals(valueType) || Boolean.class.equals(valueType));
    }

    static BooleanValueValidator getInstance() {
        return new BooleanValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final String trueString = Boolean.TRUE.toString();
        final String falseString = Boolean.FALSE.toString();
        final String valueString = value.toString();
        if (!trueString.equals(valueString) && !falseString.equals(valueString)) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "boolean").build();
        }
    }

}
