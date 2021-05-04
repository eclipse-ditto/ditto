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

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;

/**
 * This validator checks if a CharSequence is a valid message direction that matches either
 * {@link MessageDirection#TO} or {@link MessageDirection#FROM}.
 * If validation fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class DittoMessageDirectionValueValidator extends AbstractHeaderValueValidator {

    private static final DittoMessageDirectionValueValidator INSTANCE = new DittoMessageDirectionValueValidator();

    private DittoMessageDirectionValueValidator() {
        super(String.class::equals);
    }

    /**
     * Returns an instance of {@code DittoMessageSubjectValueValidator}.
     *
     * @return the instance.
     */
    static DittoMessageDirectionValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            MessageDirection.valueOf(value.toString().toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "message direction")
                    .cause(e)
                    .build();
        }
    }

}
