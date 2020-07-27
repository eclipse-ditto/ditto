/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.model.base.entity.validation;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.entity.id.RegexPatterns;

/**
 * This abstract implementation of {@code PatternValidator} validates that a given {@code CharSequence} is valid.
 */
@Immutable
abstract class AbstractPatternValidator implements Validator {

    public final static int MAX_LENGTH = 256;
    protected final CharSequence id;
    private final RegexPatterns.PatternWithMessage pattern;
    @Nullable private String reason = null;

    protected AbstractPatternValidator(final CharSequence id, final RegexPatterns.PatternWithMessage pattern) {
        this.id = id;
        this.pattern = requireNonNull(pattern, "The pattern to be validated against must not be null!");
    }

    @Override
    public boolean isValid() {
        if (id.length() > MAX_LENGTH) {
            reason = String.format("Not allowed to exceed length of %d.", MAX_LENGTH);
            return false;
        }
        if (!pattern.getPattern().matcher(id).matches()) {
            reason = pattern.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }
}
