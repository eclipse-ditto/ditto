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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Represents a parsed {@code Signal}'s type.
 * Right now it only provides access to the signal domain.
 * Further parts like or the signal type category ({@code "commands"}, {@code "responses"} etc.) or signal name could
 * be added as required to this class.
 */
@Immutable
final class SemanticSignalType {

    private static final char SIGNAL_DOMAIN_DELIMITER = '.';

    private final String signalDomain;

    private SemanticSignalType(final String signalDomain) {
        this.signalDomain = signalDomain;
    }

    /**
     * Parses the specified {@code CharSequence} argument as {@code SemanticSignalType}.
     *
     * @param signalType the signal type to be parsed.
     * @return the parsed {@code SemanticSignalType}.
     * @throws NullPointerException if {@code signalType} is {@code null}.
     * @throws IllegalArgumentException if {@code signalType} is empty.
     */
    static SemanticSignalType parseSemanticSignalType(final CharSequence signalType) {
        ConditionChecker.argumentNotEmpty(signalType, "signalType");

        final var signalTypeAsString = signalType.toString();
        final var indexDomainDelimiter = signalTypeAsString.lastIndexOf(SIGNAL_DOMAIN_DELIMITER);

        return new SemanticSignalType(signalTypeAsString.substring(0, indexDomainDelimiter));
    }

    /**
     * Returns the domain of the signal like for example {@code "things"}, {@code "messages"} etc.
     *
     * @return the signal domain.
     */
    String getSignalDomain() {
        return signalDomain;
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
        return Objects.equals(signalDomain, that.signalDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalDomain);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "signalDomain=" + signalDomain +
                "]";
    }

}
