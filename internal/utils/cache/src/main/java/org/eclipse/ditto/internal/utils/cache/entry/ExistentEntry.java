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
package org.eclipse.ditto.internal.utils.cache.entry;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * A pair consisting of a value and a revision.
 */
@Immutable
final class ExistentEntry<T> implements Entry<T> {

    private final long revision;
    private final T value;

    ExistentEntry(final long revision, final T value) {
        this.revision = revision;
        this.value = value;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public T getValueOrThrow() {
        return value;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExistentEntry)) {
            return false;
        }
        final ExistentEntry<?> that = (ExistentEntry<?>) o;
        return revision == that.revision && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "revision=" + revision +
                ", value=" + value +
                "]";
    }

}
