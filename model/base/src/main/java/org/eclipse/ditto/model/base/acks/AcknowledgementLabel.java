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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
     * @throws AcknowledgementLabelInvalidException if {@code label} did not match the regex
     * {@value AcknowledgementLabels#ACK_LABEL_REGEX}.
     */
    static AcknowledgementLabel of(final CharSequence label) {
        return AcknowledgementLabels.newLabel(label);
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
