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
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * This abstract implementation of {@code PatternValidator} validates that a given {@code CharSequence} is valid.
 * If no {@code Pattern} is given through an overwrite of {@code getPattern()} it throws a {@code NullPointerException}.
 */
@Immutable
public abstract class AbstractPatternValidator implements PatternValidator {

    public final static int MAX_LENGTH = 256;
    private String reason;


    @Override
    public boolean isValid(final CharSequence toBeValidated) {
        requireNonNull(getPattern(), "The pattern to be validated against must not be null!");
        if (toBeValidated.length() > MAX_LENGTH) {
            reason = "Not allowed to exceed length of 256.";
            return false;
        }
        if (!getPattern().matcher(toBeValidated).matches()) {
            reason = "Neither slashes nor any control characters are allowed at any place in your JSON Pointer. " +
                    "Please check!";
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public Pattern getPattern() {
        return null;
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }
}
