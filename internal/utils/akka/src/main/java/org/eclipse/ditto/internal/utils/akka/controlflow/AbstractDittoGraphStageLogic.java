/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import java.util.function.Function;

import akka.japi.function.Effect;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.Shape;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.GraphStageLogicWithLogging;

/**
 * Extension of {@code akka.stream.stage.GraphStageLogic} with Ditto-specific functions.
 *
 * @since 1.1.0
 */
public abstract class AbstractDittoGraphStageLogic extends GraphStageLogicWithLogging {

    /**
     * Create a graph stage logic object.
     *
     * @param shape shape of the graph stage to whom this graph stage logic belongs.
     */
    protected AbstractDittoGraphStageLogic(final Shape shape) {
        super(shape);
    }

    /**
     * A copy of {@code GraphStageLogic#passAlong} with element mapping function.
     *
     * @param <I> type of input elements.
     * @param <O> type of output elements.
     * @param from inlet of elements.
     * @param to outlet of elements.
     * @param mappingFunction function mapping an input element to 0 or more output elements.
     */
    protected <I, O> void passAlongMapConcat(
            final Inlet<I> from,
            final Outlet<O> to,
            final Function<I, Iterable<O>> mappingFunction) {

        final PassAlongMapConcatHandler<I, O> ph =
                new PassAlongMapConcatHandler<>(from, to, mappingFunction);

        // Before setting the PassAlongMapConcatHandler:
        // 1. Ensure all pending elements from inlet are grabbed.
        if (isAvailable(from)) {
            emitMultiple(to, mappingFunction.apply(grab(from)).iterator(), ph);
        }

        // 2. Handle pass-along of an empty stream.
        if (isClosed(from)) {
            completeStage();
        }

        setHandler(from, ph);

        // Kick-start inlet handling by the PassAlongMapConcatHandler.
        if (!hasBeenPulled(from)) {
            tryPull(from);
        }
    }

    /**
     * Handler that passes elements from an inlet to an outlet after mapping.
     *
     * @param <I> type of incoming elements.
     * @param <O> type of outgoing elements.
     */
    private final class PassAlongMapConcatHandler<I, O> extends AbstractInHandler implements Effect {

        private final Inlet<I> from;
        private final Outlet<O> to;
        private final Function<I, Iterable<O>> mappingFunction;

        private PassAlongMapConcatHandler(final Inlet<I> from,
                final Outlet<O> to,
                final Function<I, Iterable<O>> mappingFunction) {

            this.from = from;
            this.to = to;
            this.mappingFunction = mappingFunction;
        }

        /**
         * Pull the next element from the inlet. Used as 'andThen' after each emit.
         */
        @Override
        public void apply() {
            tryPull(from);
        }

        @Override
        public void onPush() {
            emitMultiple(to, mappingFunction.apply(grab(from)).iterator(), this);
        }

        @Override
        public void onUpstreamFinish() {
            completeStage();
        }

        @Override
        public void onUpstreamFailure(final Throwable ex) {
            failStage(ex);
        }
    }
}
