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
package org.eclipse.ditto.internal.utils.tracing.span;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This class represents the identifier of a trace span.
 */
@Immutable
public final class SpanId implements CharSequence, Comparable<SpanId> {

    private final String idString;

    private SpanId(final String idString) {
        this.idString = idString;
    }

    /**
     * Returns an instance of {@code SpanId} for the specified CharSequence argument.
     *
     * @param identifier the identifier of the tracing span.
     * @return the instance.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws IllegalArgumentException if {@code identifier} is empty or blank.
     */
    public static SpanId of(final CharSequence identifier) {
        ConditionChecker.checkNotNull(identifier, "identifier");
        return new SpanId(
                ConditionChecker.checkArgument(identifier.toString(),
                        arg -> !arg.isBlank(),
                        () -> "The identifier must neither be empty nor blank.")
        );
    }

    /**
     * Returns an empty instance of {@code SpanId}.
     *
     * @return the empty instance.
     */
    public static SpanId empty() {
        return new SpanId("");
    }

    @Override
    public int length() {
        return idString.length();
    }

    @Override
    public char charAt(final int index) {
        return idString.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return idString.subSequence(start, end);
    }

    @Override
    public String toString() {
        return idString;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var spanId = (SpanId) o;
        return Objects.equals(idString, spanId.idString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idString);
    }

    @Override
    public int compareTo(final SpanId o) {
        ConditionChecker.checkNotNull(o, "o");
        return CharSequence.compare(idString, o.idString);
    }

}
