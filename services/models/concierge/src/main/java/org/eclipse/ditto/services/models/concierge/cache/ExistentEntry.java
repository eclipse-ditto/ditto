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
package org.eclipse.ditto.services.models.concierge.cache;

import java.util.Objects;

import jdk.nashorn.internal.ir.annotations.Immutable;

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
    public T getValue() {
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
        return revision == that.revision &&
                Objects.equals(value, that.value);
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
