/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link PolicyRevision}.
 */
@Immutable
final class ImmutablePolicyRevision implements PolicyRevision {

    private final long value;

    private ImmutablePolicyRevision(final long theValue) {
        value = theValue;
    }

    /**
     * Returns a new instance of {@code PolicyRevision} with the given value.
     *
     * @param value the value of the new revision.
     * @return a new Policy revision.
     */
    public static ImmutablePolicyRevision of(final long value) {
        return new ImmutablePolicyRevision(value);
    }

    @Override
    public boolean isGreaterThan(final PolicyRevision other) {
        return 0 < compareTo(other);
    }

    @Override
    public boolean isGreaterThanOrEqualTo(final PolicyRevision other) {
        return 0 <= compareTo(other);
    }

    @Override
    public boolean isLowerThan(final PolicyRevision other) {
        return 0 > compareTo(other);
    }

    @Override
    public boolean isLowerThanOrEqualTo(final PolicyRevision other) {
        return 0 >= compareTo(other);
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
        final ImmutablePolicyRevision that = (ImmutablePolicyRevision) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int compareTo(final PolicyRevision o) {
        checkNotNull(o, "other revision to compare this revision with");
        return Long.compare(value, o.toLong());
    }

}
