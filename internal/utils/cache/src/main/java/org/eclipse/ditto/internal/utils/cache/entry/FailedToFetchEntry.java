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
package org.eclipse.ditto.internal.utils.cache.entry;

import java.util.NoSuchElementException;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

@Immutable
final class FailedToFetchEntry<T> implements Entry<T> {

    private final Throwable throwable;

    /* this object is not supposed to be constructed anywhere else. */
    private FailedToFetchEntry(final Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public long getRevision() {
        return 0;
    }

    @Override
    public T getValueOrThrow() {
        throw new NoSuchElementException();
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isFetchError() {
        return true;
    }

    @Override
    public Optional<Throwable> getFetchErrorCause() {
        return Optional.of(throwable);
    }

    static <T> FailedToFetchEntry<T> of(final Throwable throwable) {
        return new FailedToFetchEntry<>(throwable);
    }

}
