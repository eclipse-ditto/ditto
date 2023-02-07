/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link ConnectionRevision}.
 */
@Immutable
final class ImmutableConnectionRevision implements ConnectionRevision {

    private final long value;

    private ImmutableConnectionRevision(final long theValue) {
        value = theValue;
    }

    /**
     * Returns a new instance of {@code ConnectionRevision} with the given value.
     *
     * @param value the value of the new revision.
     * @return a new Connection revision.
     */
    public static ImmutableConnectionRevision of(final long value) {
        return new ImmutableConnectionRevision(value);
    }

    @Override
    public boolean isGreaterThan(final ConnectionRevision other) {
        return 0 < compareTo(other);
    }

    @Override
    public boolean isGreaterThanOrEqualTo(final ConnectionRevision other) {
        return 0 <= compareTo(other);
    }

    @Override
    public boolean isLowerThan(final ConnectionRevision other) {
        return 0 > compareTo(other);
    }

    @Override
    public boolean isLowerThanOrEqualTo(final ConnectionRevision other) {
        return 0 >= compareTo(other);
    }

    @Override
    public ConnectionRevision increment() {
        return of(value + 1);
    }

    @Override
    public long toLong() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableConnectionRevision that = (ImmutableConnectionRevision) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int compareTo(final ConnectionRevision o) {
        checkNotNull(o, "other revision to compare this revision with");
        return Long.compare(value, o.toLong());
    }

}
