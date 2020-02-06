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
package org.eclipse.ditto.signals.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link AcknowledgementLabel}.
 */
@Immutable
final class ImmutableAcknowledgementLabel implements AcknowledgementLabel {

    private static final Pattern ACK_LABEL_PATTERN = Pattern.compile("[a-z][A-Z][0-9][-_]{3,64}");

    private final String label;

    private ImmutableAcknowledgementLabel(final String label) {this.label = label;}

    /**
     * Returns a new AcknowledgementLabel based on the provided string.
     *
     * @param label the character sequence forming the label's value.
     * @return a new AcknowledgementLabel.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    static AcknowledgementLabel of(final CharSequence label) {
        checkNotNull(label, "label");


        if (AcknowledgementLabel.class.isAssignableFrom(label.getClass())) {
            return ((AcknowledgementLabel) label);
        } else {
            checkNotEmpty(label, "label");
        }

        final boolean isValid = ACK_LABEL_PATTERN.matcher(label).matches();
        if (!isValid) {
            // TODO TJ throw new defined Exception
        }

        return new ImmutableAcknowledgementLabel(label.toString());
    }

    @Override
    public int length() {
        return label.length();
    }

    @Override
    public char charAt(final int index) {
        return label.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return label.substring(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAcknowledgementLabel that = (ImmutableAcknowledgementLabel) o;
        return Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    @Nonnull
    public String toString() {
        return label;
    }
}
