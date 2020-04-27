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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.IdValidator;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * This validator checks if a CharSequence is a valid ID that matches {@value MessageHeaderDefinition#SUBJECT_REGEX}.
 * If validation fails, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class DittoMessageSubjectValueValidator extends AbstractHeaderValueValidator {

    private DittoMessageSubjectValueValidator() {
        super(String.class::equals);
    }

    /**
     * Returns an instance of {@code DittoMessageSubjectValueValidator}.
     *
     * @return the instance.
     */
    static DittoMessageSubjectValueValidator getInstance() {
        return new DittoMessageSubjectValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final Validator subjectValidator = IdValidator.newInstance(value, MessageHeaderDefinition.SUBJECT_REGEX);
        if (!subjectValidator.isValid()) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "message subject").build();
        }
    }

}
