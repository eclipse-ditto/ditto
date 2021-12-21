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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * An enumeration of known {@code Signal} type categories.
 *
 * @since 2.3.0
 */
public enum SignalTypeCategory {

    ANNOUNCEMENT,

    COMMAND,

    EVENT,

    RESPONSE;

    /**
     * Returns the {@code SignalTypeCategory} that has the same string representation like the specified string
     * argument.
     *
     * @param signalTypeCategoryString the string representation of the {@code SignalTypeCategory} to get.
     * @return an {@code Optional} that either contains the found {@code SignalTypeCategory} or is empty if
     * {@code signalTypeCategoryString} is unknown.
     * @throws NullPointerException if {@code signalTypeCategoryString} is {@code null}.
     * @see #toString()
     */
    public static Optional<SignalTypeCategory> getForString(final String signalTypeCategoryString) {
        ConditionChecker.checkNotNull(signalTypeCategoryString, "signalTypeCategoryString");
        return Stream.of(values())
                .filter(value -> Objects.equals(signalTypeCategoryString, value.toString()))
                .findAny();
    }

    /**
     * Returns the string representation of this {@code SignalTypeCategory}.
     * The returned string is the lower case {@link #name()} with {@code "s"} appended.
     * For example, for {@link #ANNOUNCEMENT} the returned string is {@code "announcements"}.
     *
     * @return the string representation.
     * @see #getForString(String)
     */
    @Override
    public String toString() {
        final var name = name();
        final var nameLowerCase = name.toLowerCase(Locale.ENGLISH);
        return nameLowerCase.concat("s");
    }

}
