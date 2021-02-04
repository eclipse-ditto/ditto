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


import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;

import akka.actor.ActorRef;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

public final class DittoFlowEnhancement<I, O, M> {

    private final Flow<I, O, M> flow;

    private DittoFlowEnhancement(final Flow<I, O, M> flow) {
        this.flow = flow;
    }

    /**
     * Builds a Factory to enhance a given flow with some add-on behaviour.
     *
     * @param flowToEnhance the flow that should get enhanced.
     * @param <I> the input type.
     * @param <O> the output type.
     * @param <M> the type of the materialized value.
     * @return the enhancement factory.
     */
    public static <I, O, M> DittoFlowEnhancement<I, O, M> enhanceFlow(final Flow<I, O, M> flowToEnhance) {
        return new DittoFlowEnhancement<>(flowToEnhance);
    }

    /**
     * Wraps the flow with a timing behaviour which uses the given timer to measure the time and provides each measured
     * time to the given sink.
     *
     * @param timer the timer to use.
     * @param durationSink the sink where all measured times should be sent to.
     * @return The enhancement containing a flow that measures the time of each processing.
     */
    public DittoFlowEnhancement<I, O, M> measureTime(final PreparedTimer timer, final Sink<Duration, ?> durationSink) {
        return enhanceFlow(TimeMeasuringFlow.measureTimeOf(flow, timer, durationSink));
    }

    /**
     * Wraps the Flow with a behaviour that sends a message to an actor if the processing of one element in the
     * wrapped flow exceeds the given timeout.
     * Please note that this is not intended to be used for very fast operations. The intended granularity here are
     * seconds. For example an HTTP request that should not take more time than 60 seconds.
     * @param timeoutInSeconds the timeout in seconds.
     * @return The first builder step to describe the timeout behaviour
     */
    public TimeoutFlowBuilderStepMessage<I, O, M> onTimeoutOfSeconds(final long timeoutInSeconds) {
        return new TimeoutFlowBuilder<>(flow, timeoutInSeconds, null, null, null);
    }

    /**
     * @return Returns the enhanced flow.
     */
    public Flow<I, O, M> getEnhancedFlow() {
        return flow;
    }

    public interface TimeoutFlowBuilderStepMessage<I, O, M> {

        /**
         * Specifies the message that should be sent if the timeout is exceeded.
         *
         * @param message the message.
         * @return The next builder step.
         */
        TimeoutFlowBuilderStepToRef<I, O, M> sendMessage(Object message);
    }

    public interface TimeoutFlowBuilderStepToRef<I, O, M> {

        /**
         * Specifies the ActorRef that should receive the message that is sent if the timeout is exceeded.
         *
         * @param receiver the receiver.
         * @return the next builder step.
         */
        TimeoutFlowBuilderStepUsingMaterializer<I, O, M> toRef(ActorRef receiver);
    }

    public interface TimeoutFlowBuilderStepUsingMaterializer<I, O, M> {

        /**
         * Specifies the materializer that should be used to materialize the scheduling of the message.
         *
         * @param materializer the materializer.
         * @return the enhancement containing the flow that is wrapped with a timeout behaviour now.
         */
        DittoFlowEnhancement<I, O, M> usingMaterializer(Materializer materializer);
    }


    private static class TimeoutFlowBuilder<I, O, M>
            implements TimeoutFlowBuilderStepMessage<I, O, M>, TimeoutFlowBuilderStepToRef<I, O, M>,
            TimeoutFlowBuilderStepUsingMaterializer<I, O, M> {

        private final Flow<I, O, M> flow;
        private final long timeoutInSeconds;
        @Nullable private final Object message;
        @Nullable private final ActorRef receiver;
        @Nullable private final Materializer materializer;

        private TimeoutFlowBuilder(final Flow<I, O, M> flow, final long timeoutInSeconds,
                @Nullable final Object message, @Nullable final ActorRef receiver,
                @Nullable final Materializer materializer) {
            this.flow = flow;
            this.message = message;
            this.receiver = receiver;
            this.materializer = materializer;
            this.timeoutInSeconds = timeoutInSeconds;
        }

        @Override
        public TimeoutFlowBuilderStepToRef<I, O, M> sendMessage(final Object message) {
            return new TimeoutFlowBuilder<>(flow, timeoutInSeconds, message, receiver, materializer);
        }

        @Override
        public TimeoutFlowBuilderStepUsingMaterializer<I, O, M> toRef(final ActorRef receiver) {
            return new TimeoutFlowBuilder<>(flow, timeoutInSeconds, message, receiver, materializer);
        }

        @Override
        public DittoFlowEnhancement<I, O, M> usingMaterializer(final Materializer materializer) {
            return enhanceFlow(TimeoutFlow.of(flow, timeoutInSeconds, message, receiver, materializer));
        }

    }

}
