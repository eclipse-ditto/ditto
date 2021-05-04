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
package org.eclipse.ditto.base.model.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Represents the label identifying an Acknowledgement ("ACK").
 * <p>
 * Can be a built-in Ditto ACK label as well as a custom one emitted by external applications.
 * </p>
 *
 * @since 1.1.0
 */
@Immutable
public interface AcknowledgementLabel extends Comparable<AcknowledgementLabel>, CharSequence {

    /**
     * Returns a new AcknowledgementLabel for the given character sequence.
     *
     * @param label the character sequence value of the AcknowledgementLabel to be created.
     * @return a new AcknowledgementLabel with {@code label} as its value.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException if {@code label} did not match the regex
     * {@value org.eclipse.ditto.base.model.acks.AcknowledgementLabels#ACK_LABEL_REGEX}.
     */
    static AcknowledgementLabel of(final CharSequence label) {
        return AcknowledgementLabels.newLabel(label);
    }

    /**
     * Determines whether this AcknowledgementLabel was fully resolved (contains no unresolved placeholders).
     *
     * @return {@code true} if no placeholders were present this acknowledgement label.
     * @since 1.4.0
     */
    default boolean isFullyResolved() {
        return AcknowledgementLabels.isFullyResolved(this);
    }

    @Override
    default int compareTo(final AcknowledgementLabel o) {
        checkNotNull(o, "o");
        final String selfString = toString();
        return selfString.compareTo(o.toString());
    }

    /**
     * Returns the String representation of this AcknowledgementLabel.
     *
     * @return this label's value as plain String.
     */
    @Override
    String toString();

}
