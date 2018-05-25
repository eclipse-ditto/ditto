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
package org.eclipse.ditto.services.utils.akka.controlflow;

import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Fan in flows of messages with senders.
 */
public final class FanIn {

    private FanIn() {
        throw new AssertionError();
    }

    /**
     * Create fan-in graph that funnels 2 inlets into an outlet.
     *
     * @param <T> type of output.
     * @param <A> type of first input.
     * @param <B> type of second input.
     * @return the fan-in graph.
     */
    public static <T, A extends T, B extends T>
    GraphStage<FanInShape2<WithSender<A>, WithSender<B>, WithSender<T>>> of2() {
        return new GraphStage<FanInShape2<WithSender<A>, WithSender<B>, WithSender<T>>>() {

            private final FanInShape2<WithSender<A>, WithSender<B>, WithSender<T>> shape =
                    new FanInShape2<>(Inlet.create("in0"), Inlet.create("in1"), Outlet.create("out"));

            @Override
            public FanInShape2<WithSender<A>, WithSender<B>, WithSender<T>> shape() {
                return shape;
            }

            @Override
            public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
                return new AbstractControlFlowLogic(shape) {
                    {
                        initOutlets(shape);
                        when(shape.in0(), a -> emit(shape.out(), a.withMessage(a.getMessage())));
                        when(shape.in1(), b -> emit(shape.out(), b.withMessage(b.getMessage())));
                    }
                };
            }
        };
    }
}
