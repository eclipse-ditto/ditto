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
package org.eclipse.ditto.internal.utils.persistentactors.results;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * Result without content.
 *
 * @param <E> type of events.
 */
public final class EmptyResult<E extends Event<?>> implements Result<E> {

    private static final Object INSTANCE = new EmptyResult<>();

    /**
     * Returns the singleton instance of an empty {@link Result}.
     *
     * @param <E> the type of the event contained in the result.
     * @return the empty result instance.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Event<?>> EmptyResult<E> getInstance() {
        return (EmptyResult<E>) INSTANCE;
    }

    @Override
    public void accept(final ResultVisitor<E> visitor) {
        visitor.onEmpty();
    }

    @Override
    public <F extends Event<?>> Result<F> map(final Function<E, F> mappingFunction) {
        return getInstance();
    }

    @Override
    public <F extends Event<?>> Result<F> mapStages(final Function<CompletionStage<E>, CompletionStage<F>> mappingFunction) {
        return getInstance();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " []";
    }
}
