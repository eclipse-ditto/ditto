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
package org.eclipse.ditto.model.base.acks;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Defines built-in {@link AcknowledgementLabel}s which are emitted by Ditto itself.
 *
 * @since 1.1.0
 */
public enum DittoAcknowledgementLabel implements AcknowledgementLabel {

    /**
     * Label for Acknowledgements indicating that a change to an entity (e. g. a thing) has successfully been persisted
     * in Ditto.
     */
    PERSISTED("ditto-persisted");

    private final AcknowledgementLabel delegate;

    private DittoAcknowledgementLabel(final CharSequence labelValue) {
        delegate = AcknowledgementLabel.of(labelValue);
    }

    /**
     * Indicates whether the given acknowledgement label is a Ditto acknowledgement label.
     *
     * @param acknowledgementLabel the acknowledgement label to be checked.
     * @return {@code true} if the given acknowledgement label is a constant of DittoAcknowledgementLabel.
     */
    public static boolean contains(@Nullable final AcknowledgementLabel acknowledgementLabel) {
        for (final DittoAcknowledgementLabel dittoAcknowledgementLabel : values()) {
            if (areEqual(dittoAcknowledgementLabel, acknowledgementLabel)) {
                return true;
            }
        }
        return false;
    }

    private static boolean areEqual(final DittoAcknowledgementLabel dittoAcknowledgementLabel,
            @Nullable final AcknowledgementLabel other) {

        return dittoAcknowledgementLabel.equals(other) || Objects.equals(dittoAcknowledgementLabel.delegate, other);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public char charAt(final int index) {
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return delegate.subSequence(start, end);
    }

}
