/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signal.type;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * <p>
 * Represents a parsed {@code Signal}'s type.
 * Right now it only provides access to the signal domain and name.
 * Further parts like the signal type category ({@code "commands"}, {@code "responses"} etc.) could be added as
 * required to this class.
 * </p>
 * <p>
 * <em>Note:</em> This class cannot be used for {@code Acknowledgement} signals.
 * </p>
 *
 * TODO change @since 2.x.x
 */
@Immutable
public final class SemanticSignalType {

    static final char SIGNAL_DOMAIN_DELIMITER = '.';
    static final char SIGNAL_NAME_DELIMITER = ':';

    private final String signalDomain;
    private final String signalName;

    private SemanticSignalType(final String signalDomain, final String signalName) {
        this.signalDomain = signalDomain;
        this.signalName = signalName;
    }

    /**
     * Parses the specified {@code CharSequence} argument as {@code SemanticSignalType}.
     *
     * @param signalType the signal type to be parsed.
     * @return the parsed {@code SemanticSignalType}.
     * @throws SignalTypeFormatException if {@code signalType} does not represent a parsable signal type.
     */
    public static SemanticSignalType parseSemanticSignalType(final CharSequence signalType)
            throws SignalTypeFormatException {

        if (null == signalType) {
            throw new SignalTypeFormatException("<null> is not a valid signal type.");
        }
        if (0 == signalType.length()) {
            throw new SignalTypeFormatException("<\"\"> is not a valid signal type.");
        }

        final var signalTypeAsString = signalType.toString().trim();

        return new SemanticSignalType(parseSignalDomainOrThrow(signalTypeAsString),
                parseSignalNameOrThrow(signalTypeAsString));
    }

    private static String parseSignalDomainOrThrow(final String s) throws SignalTypeFormatException {
        return s.substring(0, getIndexOfDomainDelimiterOrThrow(s));
    }

    private static int getIndexOfDomainDelimiterOrThrow(final String s) throws SignalTypeFormatException {
        final var result = s.lastIndexOf(SIGNAL_DOMAIN_DELIMITER);
        if (1 > result) {
            final var pattern = "Signal type <{0}> has wrong index of domain delimiter <{1}>: {2,number}";
            throw new SignalTypeFormatException(MessageFormat.format(pattern, s, SIGNAL_DOMAIN_DELIMITER, result));
        }

        return result;
    }

    private static String parseSignalNameOrThrow(final String s) throws SignalTypeFormatException {
        return s.substring(getIndexOfNameDelimiterOrThrow(s) + 1);
    }

    private static int getIndexOfNameDelimiterOrThrow(final String s) throws SignalTypeFormatException {
        final var result = s.lastIndexOf(SIGNAL_NAME_DELIMITER);
        if (s.length() - 1 == result) {
            final var pattern = "Signal type <{0}> has wrong index of name delimiter <{1}>: {2,number}";
            throw new SignalTypeFormatException(MessageFormat.format(pattern, s, SIGNAL_NAME_DELIMITER, result));
        }

        return result;
    }

    /**
     * Returns the domain of the signal like for example {@code "things"}, {@code "messages"} etc.
     *
     * @return the signal domain.
     */
    public String getSignalDomain() {
        return signalDomain;
    }

    /**
     * Returns the name of the signal as derived from the signal's type.
     *
     * @return the signal name.
     */
    public String getSignalName() {
        return signalName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SemanticSignalType) o;
        return Objects.equals(signalDomain, that.signalDomain) && Objects.equals(signalName, that.signalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalDomain, signalName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "signalDomain=" + signalDomain +
                ", signalName=" + signalName +
                "]";
    }

}
