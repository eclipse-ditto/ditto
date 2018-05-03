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
package org.eclipse.ditto.services.utils.akka.controlflow.components;

import java.util.function.BiConsumer;

import org.eclipse.ditto.services.utils.akka.controlflow.AbstractControlFlowLogic;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * A message pipe with an access counter and a side channel for ticks. If 2 ticks pass by without any message passing
 * through the pipe, execute a user-supplied routine.
 *
 * @param <A> type of messages through the pipe.
 * @param <T> type of ticks.
 */
public final class PipeWithIdleRoutine<A, T> extends GraphStage<FanInShape2<A, T, A>> {

    private final FanInShape2<A, T, A> shape =
            new FanInShape2<>(Inlet.create("input"), Inlet.create("tick"), Outlet.create("output"));

    private final BiConsumer<T, LoggingAdapter> idleRoutine;

    private PipeWithIdleRoutine(final BiConsumer<T, LoggingAdapter> idleRoutine) {
        this.idleRoutine = idleRoutine;
    }

    /**
     * Create a pipe with idle routine.
     *
     * @param idleRoutine the idle routine.
     * @param <A> type of messages through the pipe.
     * @param <T> type of ticks.
     * @return pipe with idle routine.
     */
    public static <A, T> Graph<FanInShape2<A, T, A>, NotUsed> of(final BiConsumer<T, LoggingAdapter> idleRoutine) {
        return new PipeWithIdleRoutine<>(idleRoutine);
    }

    @Override
    public FanInShape2<A, T, A> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new AbstractControlFlowLogic(shape) {
            private int accessCounter = 0;
            private int accessCounterByLastTick = 0;

            {
                initOutlets(shape);

                when(shape.in0(), message -> {
                    ++accessCounter;
                    emit(shape.out(), message);
                });

                when(shape.in1(), tick -> {
                    if (accessCounter == accessCounterByLastTick) {
                        idleRoutine.accept(tick, log());
                    } else {
                        accessCounterByLastTick = accessCounter;
                    }
                });
            }
        };
    }
}
