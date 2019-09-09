/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.persistentactors.results;

/**
 * Result without content.
 *
 * @param <E> type of events.
 */
public final class EmptyResult<E> implements Result<E> {

    private static final Object INSTANCE = new EmptyResult();

    @SuppressWarnings("unchecked")
    public static <E> EmptyResult<E> getInstance() {
        return (EmptyResult<E>) INSTANCE;
    }

    @Override
    public void accept(final ResultVisitor visitor) {
        visitor.onEmpty();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " []";
    }
}
