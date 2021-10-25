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

    private static final String PLACEHOLDER_GROUP = "placeholder";

    /**
     * Regular expression which determines the value of a valid AcknowledgementLabel.
     *
     * Ack labels starting with unresolved placeholders are also valid, e.g.: {@code {{connection:id}}:my-ack}.
     */
    public static final String ACK_LABEL_REGEX =
            "(?<" + PLACEHOLDER_GROUP + ">\\{\\{\\w*[a-z]+:[a-z]+\\w*}}:)?[a-zA-Z0-9-_:]{3,165}";

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
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException if {@code label} did not match the regex {@value #ACK_LABEL_REGEX}.
     */
    public static AcknowledgementLabel newLabel(final CharSequence label) {
        if (label instanceof AcknowledgementLabel) {
            return (AcknowledgementLabel) label;
        }
        validateLabel(checkNotNull(label, "label"));

        return ImmutableAcknowledgementLabel.of(label);
    }

    /**
     * Determines whether the passed {@code label} was fully resolved (contains no unresolved placeholders).
     *
     * @param label the AcknowledgementLabel to check for being fully resolved.
     * @return {@code true} if no placeholders were present in the passed {@code label}.
     */
    static boolean isFullyResolved(final AcknowledgementLabel label) {
        final Matcher labelRegexMatcher = ACK_LABEL_PATTERN.matcher(label);
        if (labelRegexMatcher.matches()) {
            final String placeholder = labelRegexMatcher.group(PLACEHOLDER_GROUP);
            return null == placeholder || placeholder.isEmpty();
        } else {
            // this can't happen for an already created AcknowledgementLel
            throw new AcknowledgementLabelInvalidException(label);
        }
    }

    private static void validateLabel(final CharSequence label) {
        final Matcher labelRegexMatcher = ACK_LABEL_PATTERN.matcher(label);
        if (!labelRegexMatcher.matches()) {
            throw new AcknowledgementLabelInvalidException(label);
        }
    }

}
