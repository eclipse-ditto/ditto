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

import java.util.NoSuchElementException;

import javax.annotation.concurrent.Immutable;

@Immutable
final class NonexistentEntry<T> implements Entry<T> {

    private static final NonexistentEntry<?> INSTANCE = new NonexistentEntry<>();

    /* this object is not supposed to be constructed anywhere else. */
    private NonexistentEntry() {}

    @Override
    public long getRevision() {
        return Long.MIN_VALUE;
    }

    @Override
    public T getValueOrThrow() {
        throw new NoSuchElementException();
    }

    @Override
    public boolean exists() {
        return false;
    }

    @SuppressWarnings("unchecked")
    static <T> NonexistentEntry<T> getInstance() {
        return (NonexistentEntry<T>) INSTANCE;
    }

}
