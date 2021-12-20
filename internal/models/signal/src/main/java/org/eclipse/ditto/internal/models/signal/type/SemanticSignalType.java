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
 * Represents a parsed {@code Signal}'s type and allows random access to its parts.
 * </p>
 * <p>
 * <em>Note:</em> This class cannot be used for {@code Acknowledgement} signals.
 * </p>
 *
 * @since 2.3.0
 */
@Immutable
public final class SemanticSignalType {

    static final char SIGNAL_DOMAIN_DELIMITER = '.';
    static final char SIGNAL_NAME_DELIMITER = ':';

    private final String signalDomain;
    private final SignalTypeCategory signalTypeCategory;
    private final String signalName;

    private SemanticSignalType(final String signalDomain,
            final SignalTypeCategory signalTypeCategory,
            final String signalName) {

        this.signalDomain = signalDomain;
        this.signalTypeCategory = signalTypeCategory;
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
        final var signalTypeAsString = signalType.toString().trim();
        if (signalTypeAsString.isBlank()) {
            throw new SignalTypeFormatException("Signal type must not be blank.");
        }

        final var semanticSignalTypeParser = new SemanticSignalTypeParser(signalTypeAsString);
        return semanticSignalTypeParser.parse();
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
     * Returns the category of the signal type like for example {@code "commands"}.
     *
     * @return the signal type category.
     */
    public SignalTypeCategory getSignalTypeCategory() {
        return signalTypeCategory;
    }

    /**
     * Returns the name of the signal as derived from the signal's type.
     * E.g. the signal name of {@code "things.commands:modifyAttributes"} would be {@code "modifyAttributes"}.
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
        return Objects.equals(signalDomain, that.signalDomain) &&
                Objects.equals(signalTypeCategory, that.signalTypeCategory) &&
                Objects.equals(signalName, that.signalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalDomain, signalTypeCategory, signalName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "signalDomain=" + signalDomain +
                ", signalTypeCategory=" + signalTypeCategory.name() +
                ", signalName=" + signalName +
                "]";
    }

    private static final class SemanticSignalTypeParser {

        private final String signalTypeString;

        private SemanticSignalTypeParser(final String signalTypeString) {
            this.signalTypeString = signalTypeString;
        }

        private SemanticSignalType parse() throws SignalTypeFormatException {
            final var indexOfDomainDelimiter = getIndexOfDomainDelimiterOrThrow();
            final var indexOfNameDelimiter = getIndexOfNameDelimiterOrThrow();

            return new SemanticSignalType(parseSignalDomain(indexOfDomainDelimiter),
                    parseSignalTypeCategoryOrThrow(indexOfDomainDelimiter, indexOfNameDelimiter),
                    parseSignalName(indexOfNameDelimiter));
        }

        private int getIndexOfDomainDelimiterOrThrow() throws SignalTypeFormatException {
            final var result = signalTypeString.lastIndexOf(SIGNAL_DOMAIN_DELIMITER);
            if (1 > result) {
                final var pattern = "Signal type <{0}> has wrong index of domain delimiter <{1}>: {2,number}";
                throw new SignalTypeFormatException(MessageFormat.format(pattern,
                        signalTypeString,
                        SIGNAL_DOMAIN_DELIMITER,
                        result));
            }
            return result;
        }

        private String parseSignalDomain(final int indexOfDomainDelimiter) {
            return signalTypeString.substring(0, indexOfDomainDelimiter);
        }

        private String parseSignalName(final int indexOfNameDelimiter) {
            return signalTypeString.substring(indexOfNameDelimiter + 1);
        }

        private int getIndexOfNameDelimiterOrThrow() throws SignalTypeFormatException {
            final var result = signalTypeString.lastIndexOf(SIGNAL_NAME_DELIMITER);
            if (-1 == result || signalTypeString.length() - 1 == result) {
                final var pattern = "Signal type <{0}> has wrong index of name delimiter <{1}>: {2,number}";
                throw new SignalTypeFormatException(MessageFormat.format(pattern,
                        signalTypeString,
                        SIGNAL_NAME_DELIMITER,
                        result));
            }
            return result;
        }

        private SignalTypeCategory parseSignalTypeCategoryOrThrow(final int indexOfDomainDelimiter,
                final int indexOfNameDelimiter) throws SignalTypeFormatException {

            final var categoryString = signalTypeString.substring(indexOfDomainDelimiter + 1, indexOfNameDelimiter);
            return SignalTypeCategory.getForString(categoryString)
                    .orElseThrow(() -> new SignalTypeFormatException(
                            MessageFormat.format("Signal type <{0}> has unknown category <{1}>.",
                                    signalTypeString,
                                    categoryString))
                    );
        }

    }

}
