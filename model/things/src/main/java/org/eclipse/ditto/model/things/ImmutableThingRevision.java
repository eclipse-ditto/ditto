/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.things;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link ImmutableThingRevision}.
 */
@Immutable
final class ImmutableThingRevision implements ThingRevision {

    private final long value;

    private ImmutableThingRevision(final long theValue) {
        value = theValue;
    }

    /**
     * Returns a new instance of {@code ThingRevision} with the given value.
     *
     * @param value the value of the new revision.
     * @return a new Thing revision.
     */
    public static ThingRevision of(final long value) {
        return new ImmutableThingRevision(value);
    }

    private static void checkThingVersion(final ThingRevision thingRevision) {
        requireNonNull(thingRevision, "The other revision to compare this revision with must not be null!");
    }

    @Override
    public boolean isGreaterThan(final ThingRevision other) {
        return 0 < compareTo(other);
    }

    @Override
    public boolean isGreaterThanOrEqualTo(final ThingRevision other) {
        return 0 <= compareTo(other);
    }

    @Override
    public boolean isLowerThan(final ThingRevision other) {
        return 0 > compareTo(other);
    }

    @Override
    public boolean isLowerThanOrEqualTo(final ThingRevision other) {
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
        final ImmutableThingRevision that = (ImmutableThingRevision) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int compareTo(final ThingRevision o) {
        checkThingVersion(o);
        return Long.compare(value, o.toLong());
    }

}
