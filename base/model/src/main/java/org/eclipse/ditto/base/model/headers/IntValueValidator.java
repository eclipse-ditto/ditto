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
 * This validator parses a CharSequence to an integer value.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class IntValueValidator extends AbstractHeaderValueValidator {

    private static final IntValueValidator INSTANCE = new IntValueValidator();

    private IntValueValidator() {
        super(valueType -> int.class.equals(valueType) || Integer.class.equals(valueType));
    }

    static IntValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            Integer.parseInt(value.toString());
        } catch (final NumberFormatException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "int").build();
        }
    }

}
