/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.japi.Pair;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.Materializer;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Zip;
import scala.concurrent.duration.FiniteDuration;

public final class TimeoutFlow {

    private TimeoutFlow() {
        //No-Op because this is a factory.
    }

    /**
     * Builds a flow that issues a message to the given receiver if it took longer than the given timeout to process
     * the input of the flow to the output.
     *
     * <pre>
     *   +------------------------------------------------------------------------------------+
     *   |                                                                                    |
     *   |                         +--------------+                                           |
     *   |                     +-->+ startTimer   +-------------+   +-----+   +-----------+   |
     * IN|  +---------------+  |   +--------------+             +-->+ zip +-->+ stopTimer |   |
     * +--->+ beforeTimerBC +--+                                |   +-----+   +-----------+   |
     *   |  +---------------+  |   +------+   +--------------+  |                             |
     *   |                     +-->+ flow +-->+ afterTimerBC +--+                             |OUT
     *   |                         +------+   +--------------+  +-------------------------------->
     *   |                                                                                    |
     *   +------------------------------------------------------------------------------------+
     * </pre>
     *
     * @param flow the flow that should be observed.
     * @param timeoutInSeconds the maximum processing time of a single stream element in this flow.
     * @param message the message that should be issued if processing time of a single stream element in this flow
     * @param receiver the actor that should receive the message.
     * @param materializer the materializer that is used to schedule the message for the given timeout.
     * @param <I> the type of the Flow input.
     * @param <O> the type of the Flow output.
     * @param <M> the type of the materialized value.
     * @return a Flow wrapping the given flow to measure the time.
     */
    public static <I, O, M> Flow<I, O, M> of(final Flow<I, O, M> flow, final long timeoutInSeconds,
            final Object message, final ActorRef receiver, final Materializer materializer) {
        checkNotNull(flow, "flow");
        checkNotNull(message, "message");
        checkNotNull(receiver, "receiver");
        checkNotNull(materializer, "materializer");
        if (timeoutInSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than 0 seconds.");
        }

        final Graph<FlowShape<I, O>, M> graph = GraphDSL.create(flow, (builder, flowShape) -> {

            final UniformFanOutShape<I, I> beforeTimerBroadcast = builder.add(Broadcast.create(2));

            final UniformFanOutShape<O, O> afterTimerBroadcast = builder.add(Broadcast.create(2));

            final FanInShape2<Cancellable, O, Pair<Cancellable, O>> zip = builder.add(Zip.create());

            final Sink<Pair<Cancellable, O>, CompletionStage<Done>> stopTimeoutSink =
                    Sink.<Pair<Cancellable, O>>foreach(pair -> pair.first().cancel());

            final Flow<I, Cancellable, NotUsed> startTimeoutFlow = Flow.fromFunction(request -> materializer
                    .scheduleOnce(FiniteDuration.apply(timeoutInSeconds, TimeUnit.SECONDS),
                            () -> receiver.tell(message, ActorRef.noSender())));

            // its important that outlet 0 is connected to the timers, to guarantee that the timer is started first
            builder.from(beforeTimerBroadcast.out(0))
                    .via(builder.add(startTimeoutFlow))
                    .toInlet(zip.in0());

            builder.from(afterTimerBroadcast.out(0))
                    .toInlet(zip.in1());

            builder.from(zip.out())
                    .to(builder.add(stopTimeoutSink));

            builder.from(beforeTimerBroadcast.out(1))
                    .via(flowShape)
                    .viaFanOut(afterTimerBroadcast);

            return FlowShape.of(beforeTimerBroadcast.in(), afterTimerBroadcast.out(1));
        });
        return Flow.fromGraph(graph);
    }

}
