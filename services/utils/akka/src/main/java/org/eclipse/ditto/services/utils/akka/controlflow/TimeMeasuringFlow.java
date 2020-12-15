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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Zip;


public final class TimeMeasuringFlow {

    private TimeMeasuringFlow() {
        //No-Op because this is a factory.
    }

    /**
     * Builds a flow that measures the time it took the given flow to process the input to the output using the given timer.
     *
     * @param flow the flow that should be measured.
     * @param timer the timer that should be used to measure the time.
     * @param <I> the type of the Flow input.
     * @param <O> the type of the Flow output.
     * @return a Flow wrapping the given flow to measure the time.
     */
    @SuppressWarnings("unchecked")
    public static <I, O> Flow<I, O, NotUsed> measureTimeOf(final Flow<I, O, ?> flow, final PreparedTimer timer) {
        final Graph<FlowShape<I, O>, NotUsed>
                flowShapeNotUsedGraph = GraphDSL.create(builder -> {

            final FlowShape<I, I> timeMeasuringEntry = builder.add(Flow.fromFunction(input -> input));

            final UniformFanOutShape<I, I> beforeTimerBroadcast = builder.add(Broadcast.create(2));

            final UniformFanOutShape<O, O> afterTimerBroadcast = builder.add(Broadcast.create(2));

            final FanInShape2<StartedTimer, O, Pair<StartedTimer, O>> zip = builder.add(Zip.create());

            final Sink<Pair<StartedTimer, O>, CompletionStage<Done>> stopTimerSink =
                    Sink.foreach(pair -> pair.first().stop());

            final Flow<I, StartedTimer, NotUsed> startTimerFlow = Flow.fromFunction(request -> timer.start());

            builder.from(beforeTimerBroadcast)
                    .via(builder.add(startTimerFlow))
                    .toInlet(zip.in0());

            builder.from(afterTimerBroadcast.out(0))
                    .toInlet(zip.in1());

            builder.from(zip.out())
                    .to(builder.add(stopTimerSink));

            builder.from(timeMeasuringEntry.out())
                    .viaFanOut(beforeTimerBroadcast)
                    .via(builder.add(flow))
                    .viaFanOut(afterTimerBroadcast);

            return FlowShape.of(timeMeasuringEntry.in(), afterTimerBroadcast.out(1));
        });

        return Flow.fromGraph(flowShapeNotUsedGraph);
    }

}
