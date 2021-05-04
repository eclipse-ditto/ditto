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

import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * The result of applying the strategy to the given command.
 */
public interface Result<E extends Event<?>> {

    /**
     * Evaluate the result by a visitor.
     *
     * @param visitor the visitor to evaluate the result, typically the persistent actor itself.
     */
    void accept(final ResultVisitor<E> visitor);

    /**
     * Convert the result with a function.
     *
     * @param mappingFunction the mapping function.
     * @param <F> the new event type of the result.
     * @return the new result.
     * @since 2.0.0
     */
    <F extends Event<?>> Result<F> map(Function<E, F> mappingFunction);

    /**
     * @return the empty result
     */
    static <E extends Event<?>> Result<E> empty() {
        return ResultFactory.emptyResult();
    }
}
