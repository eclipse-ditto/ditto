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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Entry point for creating instances concerning end-to-end acknowledgement labels.
 *
 * @since 1.1.0
 */
@Immutable
final class AcknowledgementLabels {

    /**
     * Regular expression which determines the value of a valid AcknowledgementLabel.
     */
    public static final String ACK_LABEL_REGEX = "[a-zA-Z0-9-_]{3,64}";

    private static final Pattern ACK_LABEL_PATTERN = Pattern.compile(ACK_LABEL_REGEX);

    private AcknowledgementLabels() {
        throw new AssertionError();
    }

    /**
     * Returns a new AcknowledgementLabel for the given character sequence.
     *
     * @param label the character sequence value of the acknowledgement label to be created.
     * @return a new AcknowledgementLabel with {@code label} as its value.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws AcknowledgementLabelInvalidException if {@code label} did not match the regex {@value #ACK_LABEL_REGEX}.
     */
    public static AcknowledgementLabel newLabel(final CharSequence label) {
        checkNotNull(label, "label");

        final Class<AcknowledgementLabel> labelTargetClass = AcknowledgementLabel.class;
        if (labelTargetClass.isAssignableFrom(label.getClass())) {
            return labelTargetClass.cast(label);
        }
        validateLabel(label);

        return ImmutableAcknowledgementLabel.of(label);
    }

    private static void validateLabel(final CharSequence label) {
        final Matcher labelRegexMatcher = ACK_LABEL_PATTERN.matcher(label);
        if (!labelRegexMatcher.matches()) {
            throw new AcknowledgementLabelInvalidException(label);
        }
    }

}
