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
package org.eclipse.ditto.base.model.entity.validation;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.RegexPatterns;

/**
 * Validator capable of validating char sequences via the pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_PATTERN} in
 * order to validate that they do not contain control characters as defined in {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#CONTROL_CHARS}.
 *
 * @since 1.2.0
 */
@Immutable
public final class NoControlCharactersValidator extends AbstractPatternValidator {

    /**
     * @param id the char sequence that is validated
     * @return new instance of {@link org.eclipse.ditto.base.model.entity.validation.NoControlCharactersValidator}
     */
    public static NoControlCharactersValidator getInstance(final CharSequence id) {
        return new NoControlCharactersValidator(id);
    }

    protected NoControlCharactersValidator(final CharSequence id) {
        super(id, RegexPatterns.NO_CONTROL_CHARS_PATTERN, "No control characters are allowed.");
    }

}
