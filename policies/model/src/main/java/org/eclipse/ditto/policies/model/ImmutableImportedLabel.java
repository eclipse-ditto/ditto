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

    /**
     * Prefix for Policy labels which were merged in transparently from a configured namespace root policy.
     * "Normal" Policy labels may not start with this prefix either - that protects operator-configured
     * namespace root entries from being shadowed by tenants reusing their labels and lets multiple
     * namespace roots compose without overwriting each other on label collision.
     *
     * @since 3.9.0
     */
    static final String NSIMPORTED_PREFIX = "nsimported";

    private static final String LABEL_MESSAGE_FORMAT_PATTERN = "{0}-{1}-{2}";

    private final String labelValue;

    private ImmutableImportedLabel(final String prefix, final PolicyId importedFromPolicyId,
            final String theLabelValue) {
        checkNotNull(importedFromPolicyId, "importedFromPolicyId");
        labelValue = MessageFormat.format(LABEL_MESSAGE_FORMAT_PATTERN, prefix, importedFromPolicyId, theLabelValue);
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
        return ofWithPrefix(IMPORTED_PREFIX, importedFromPolicyId, labelValue);
    }

    /**
     * Returns a new Label for an entry merged from a namespace root policy, prefixed with
     * {@value #NSIMPORTED_PREFIX} and the source policy ID. The resulting label cannot collide with a
     * user-submitted local label (the {@value #NSIMPORTED_PREFIX} prefix is rejected by
     * {@link ImmutableLabel#of(CharSequence, boolean)}).
     *
     * @param sourcePolicyId the namespace root Policy ID from which the entry originated.
     * @param labelValue the original label of the entry in the source policy.
     * @return a new Label of the form {@code nsimported-<sourcePolicyId>-<labelValue>}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     * @since 3.9.0
     */
    public static Label ofNsImported(final PolicyId sourcePolicyId, final CharSequence labelValue) {
        return ofWithPrefix(NSIMPORTED_PREFIX, sourcePolicyId, labelValue);
    }

    private static Label ofWithPrefix(final String prefix, final PolicyId sourcePolicyId,
            final CharSequence labelValue) {
        argumentNotEmpty(labelValue, "label value");

        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(labelValue);
        if (!validator.isValid()) {
            throw PolicyEntryInvalidException.newBuilder()
                    .message("The Policy Label " + labelValue + " is invalid")
                    .description(validator.getReason().orElse(null))
                    .build();
        }

        return new ImmutableImportedLabel(prefix, sourcePolicyId, labelValue.toString());
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
