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
package org.eclipse.ditto.base.model.common;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Instances of this class can be used to validate a specified ID against the provided regular expression.
 */
@Immutable
public final class IdValidator implements Validator {

    private final CharSequence id;
    private final Pattern idPattern;
    private String reason;

    private IdValidator(@Nullable final CharSequence id, final String regex) {
        this.id = id;
        idPattern = Pattern.compile(regex);
        reason = null;
    }

    /**
     * Creates a new {@code IdValidator} instance.
     *
     * @param id the ID to be validated.
     * @param regex the regular expression to be validated against.
     * @return a new {@code IdValidator} object.
     * @throws NullPointerException if {@code regex} is {@code null}.
     */
    public static IdValidator newInstance(@Nullable final CharSequence id, final String regex) {
        requireNonNull(regex, "The regex to be validated against must not be null!");

        return new IdValidator(id, regex);
    }

    /**
     * Validates the ID which was provided to the static factory method of this class. Validation ensures that the ID
     * complies to the provided regular expression.
     *
     * @return {@code true} if the checked Thing ID is valid, {@code false} else.
     */
    @Override
    public boolean isValid() {
        final boolean isValid;

        if (id == null) {
            isValid = false;
            reason = "The ID is not valid because it was 'null'!";
        } else {
            final Matcher matcher = idPattern.matcher(id);
            isValid = matcher.matches();
            if (!isValid) {
                final String msgTemplate = "The ID ''{0}'' is not valid! It did not match the pattern ''{1}''.";
                reason = MessageFormat.format(msgTemplate, id, idPattern);
            }
        }


        return isValid;
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }

}
