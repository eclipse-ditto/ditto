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

import java.util.Collection;

import akka.NotUsed;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;

/**
 * Combinators to join Akka stream processors.
 */
public final class Pipe {

    private Pipe() {
        throw new AssertionError();
    }

    /**
     * Join a flow into a sink.
     *
     * @param step1 the flow.
     * @param step2 the sink.
     * @param <A> type of input.
     * @param <B> type of intermediate messages.
     * @return joined sink.
     */
    public static <A, B> Graph<SinkShape<A>, NotUsed> joinSink(
            final Graph<FlowShape<A, B>, NotUsed> step1,
            final Graph<SinkShape<B>, NotUsed> step2) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, B> shape1 = builder.add(step1);
            final SinkShape<B> shape2 = builder.add(step2);
            builder.from(shape1.out()).to(shape2);
            return SinkShape.of(shape1.in());
        });
    }

    /**
     * Attach a sink to the output port of a filter.
     *
     * @param filter the filter.
     * @param sink the sink.
     * @param <A> type of incoming and unhandled messages.
     * @param <B> type of messages that pass through the filter.
     * @return joined flow.
     */
    public static <A, B> Graph<FlowShape<A, A>, NotUsed> joinFilteredSink(
            final Graph<FanOutShape2<A, B, A>, NotUsed> filter,
            final Graph<SinkShape<B>, NotUsed> sink) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<A, B, A> filterShape = builder.add(filter);
            final SinkShape<B> sinkShape = builder.add(sink);
            builder.from(filterShape.out0()).to(sinkShape);
            return FlowShape.of(filterShape.in(), filterShape.out1());
        });
    }

    /**
     * Attach a sink to the unhandled port of a filter.
     *
     * @param filter the filter.
     * @param unhandled the sink.
     * @param <A> type of incoming messages.
     * @param <B> type of messages that pass through the filter.
     * @return joined flow.
     */
    public static <A, B> Graph<FlowShape<A, B>, NotUsed> joinUnhandledSink(
            final Graph<FanOutShape2<A, B, A>, NotUsed> filter,
            final Graph<SinkShape<A>, NotUsed> unhandled) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<A, B, A> filterShape = builder.add(filter);
            final SinkShape<A> sinkShape = builder.add(unhandled);
            builder.from(filterShape.out1()).to(sinkShape);
            return FlowShape.of(filterShape.in(), filterShape.out0());
        });
    }

    /**
     * Attach a flow into the output port of a filter.
     *
     * @param filter the filter.
     * @param flow the flow.
     * @param <A> type of incoming and unhandled messages.
     * @param <B> type of messages that pass through the filter.
     * @param <C> output type of the flow.
     * @return joined filter.
     */
    public static <A, B, C> Graph<FanOutShape2<A, C, A>, NotUsed> joinFilteredFlow(
            final Graph<FanOutShape2<A, B, A>, NotUsed> filter,
            final Graph<FlowShape<B, C>, NotUsed> flow) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<A, B, A> filterShape = builder.add(filter);
            final FlowShape<B, C> flowShape = builder.add(flow);
            builder.from(filterShape.out0()).toInlet(flowShape.in());
            return new FanOutShape2<>(filterShape.in(), flowShape.out(), filterShape.out1());
        });
    }

    /**
     * Chain a collection of flows one after another.
     *
     * @param flows collection of flows.
     * @param <A> type of messages through the flows.
     * @return joined flow.
     */
    public static <A> Graph<FlowShape<A, A>, NotUsed> joinFlows(
            final Collection<Graph<FlowShape<A, A>, NotUsed>> flows) {

        Flow<A, A, NotUsed> overallFlow = Flow.create();

        for (Graph<FlowShape<A, A>, NotUsed> flow : flows) {
            overallFlow = overallFlow.via(flow);
        }

        return overallFlow;
    }
}
