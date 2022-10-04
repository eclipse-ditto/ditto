/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This class represents the name of an operation to be used as name for a trace span.
 */
@Immutable
public final class TraceOperationName implements CharSequence, Comparable<TraceOperationName> {

    private final String name;

    private TraceOperationName(final String name) {
        this.name = name;
    }

    /**
     * Returns an instance of {@code TraceOperationName} for the specified CharSequence argument.
     *
     * @param name the name of the operation. <em>Note:</em> all leading and trailing spaces will be deleted in the
     * resulting {@code TraceOperationName}.
     * @return the instance.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty or blank.
     */
    public static TraceOperationName of(final CharSequence name) {
        ConditionChecker.checkNotNull(name, "name");

        // Convert to String to ensure immutability of TraceOperationName.
        final var nameAsString = name.toString().trim();
        return new TraceOperationName(
                ConditionChecker.checkArgument(
                        nameAsString,
                        arg -> !arg.isBlank(),
                        () -> "The name must neither be empty nor blank."
                )
        );
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TraceOperationName) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(final TraceOperationName o) {
        ConditionChecker.checkNotNull(o, "o");
        return CharSequence.compare(name, o.name);
    }

}
