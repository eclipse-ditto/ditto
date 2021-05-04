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

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Validator;

/**
 * This abstract implementation of {@code PatternValidator} validates that a given {@code CharSequence} is valid.
 */
@Immutable
public abstract class AbstractPatternValidator implements Validator {

    private static final int MAX_LENGTH = 256;

    private final CharSequence id;
    private final Pattern pattern;
    private final String patternErrorMessage;
    @Nullable private String reason = null;

    /**
     * @param id the char sequence that is validated
     * @param pattern the pattern against which the id is validated
     * @param patternErrorMessage the error message used to describe a pattern mismatch
     */
    protected AbstractPatternValidator(final CharSequence id, final Pattern pattern, final String patternErrorMessage) {
        this.id = id;
        this.pattern = requireNonNull(pattern, "The pattern to be validated against must not be null!");
        this.patternErrorMessage =
                requireNonNull(patternErrorMessage, "The message describing a mismatch must not be null!");
    }

    @Override
    public boolean isValid() {
        if (id.length() > MAX_LENGTH) {
            reason = String.format("Not allowed to exceed length of %d.", MAX_LENGTH);
            return false;
        }
        if (!pattern.matcher(id).matches()) {
            reason = patternErrorMessage;
            return false;
        }
        return true;
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }
}
