/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersNoSlashesValidator;

/**
 * An immutable implementation of {@link Label} which was created by an imported policy.
 */
@Immutable
final class ImmutableImportedLabel implements Label {

    /**
     * Prefix for Policy labels which were imported. As a consequence "normal" Policy labels may not start with the
     * prefix.
     */
    static final String IMPORTED_PREFIX = "imported";

    private static final String LABEL_MESSAGE_FORMAT_PATTERN = "{0}-{1}-{2}";

    private final String labelValue;

    private ImmutableImportedLabel(final PolicyId importedFromPolicyId, final String theLabelValue) {
        checkNotNull(importedFromPolicyId, "importedFromPolicyId");
        labelValue = MessageFormat.format(LABEL_MESSAGE_FORMAT_PATTERN, IMPORTED_PREFIX, importedFromPolicyId, theLabelValue);
    }

    /**
     * Returns a new Label based on the provided string, the label being treated as imported label.
     *
     * @param importedFromPolicyId the Policy ID from where the label was imported from.
     * @param labelValue the character sequence forming the Label's value.
     * @return a new Label.
     * @throws NullPointerException if {@code labelValue} is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     */
    public static Label of(final PolicyId importedFromPolicyId, final CharSequence labelValue) {
        argumentNotEmpty(labelValue, "label value");

        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(labelValue);
        if (!validator.isValid()) {
            throw PolicyEntryInvalidException.newBuilder()
                    .message("The Policy Label " + labelValue + " is invalid")
                    .description(validator.getReason().orElse(null))
                    .build();
        }

        return new ImmutableImportedLabel(importedFromPolicyId, labelValue.toString());
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
        final ImmutableImportedLabel that = (ImmutableImportedLabel) o;
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
