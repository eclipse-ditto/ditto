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
package org.eclipse.ditto.internal.utils.akka.controlflow;

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
