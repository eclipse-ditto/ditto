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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.entity.validation.NoControlCharactersNoSlashesValidator;

/**
 * An immutable implementation of {@link Label}.
 */
@Immutable
final class ImmutableLabel implements Label {

    /**
     * Prefix for Policy labels which were imported. As a consequence Policy entries may not start with the prefix.
     */
    static final String BLACKLISTED_IMPORTED_PREFIX = "imported-";

    private final String labelValue;

    private ImmutableLabel(final String theLabelValue) {
        labelValue = theLabelValue;
    }

    /**
     * Returns a new Label based on the provided string.
     *
     * @param labelValue the character sequence forming the Label's value.
     * @return a new Label.
     * @throws NullPointerException if {@code labelValue} is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     * @throws LabelInvalidException of the {@code labelValue} can not be used to to blacklisted prefixes.
     */
    public static Label of(final CharSequence labelValue) {
        argumentNotEmpty(labelValue, "label value");
        if (labelValue.toString().startsWith(BLACKLISTED_IMPORTED_PREFIX)) {
            throw LabelInvalidException.newBuilder(labelValue).build();
        }

        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(labelValue);
        if (!validator.isValid()) {
            throw PolicyEntryInvalidException.newBuilder()
                    .message("The Policy Label " + labelValue + " is invalid")
                    .description(validator.getReason().orElse(null))
                    .build();
        }

        return new ImmutableLabel(labelValue.toString());
    }

    @Override
    public int length() {
        return labelValue.length();
    }

    @Override
    public char charAt(final int index) {
        return labelValue.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return labelValue.subSequence(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLabel that = (ImmutableLabel) o;
        return Objects.equals(labelValue, that.labelValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelValue);
    }

    @Override
    @Nonnull
    public String toString() {
        return labelValue;
    }

}
