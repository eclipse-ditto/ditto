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
 * This validator parses a CharSequence to an long value.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class LongValueValidator extends AbstractHeaderValueValidator {

    private static final LongValueValidator INSTANCE = new LongValueValidator();

    private LongValueValidator() {
        super(valueType -> long.class.equals(valueType) || Long.class.equals(valueType));
    }

    /**
     * Returns an instance of {@code LongValueValidator}.
     *
     * @return the instance.
     */
    static LongValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            Long.parseLong(value.toString());
        } catch (final NumberFormatException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "long").build();
        }
    }

}
