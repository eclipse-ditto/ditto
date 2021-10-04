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

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * This validator parses a CharSequence to a boolean value.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class BooleanValueValidator extends AbstractHeaderValueValidator {

    private static final BooleanValueValidator INSTANCE = new BooleanValueValidator();

    private BooleanValueValidator() {
        super(valueType -> boolean.class.equals(valueType) || Boolean.class.equals(valueType));
    }

    static BooleanValueValidator getInstance() {
        return INSTANCE;
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
