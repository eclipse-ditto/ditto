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
    public T getValue() {
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
