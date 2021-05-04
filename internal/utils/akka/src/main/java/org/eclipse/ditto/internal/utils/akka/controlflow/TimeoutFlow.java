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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;

import akka.NotUsed;
import akka.japi.function.Function;
import akka.stream.FlowShape;
import akka.stream.SourceShape;
import akka.stream.UniformFanInShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Source;

/**
 * Factory to create flows that emit a timeout element if it took longer than the given timeout to process an input
 * element.
 */
public final class TimeoutFlow {

    private TimeoutFlow() {
        //No-Op because this is a factory.
    }

    /**
     * Builds a flow that emits a timeout element if it took longer than the given timeout to process
     * the input of the flow to the output.
     *
     * @param flow the flow that produces an output element for each input element.
     * @param timeout the maximum processing time of a single stream element in this flow.
     * @param onTimeout the function to generate timeout elements from input elements.
     * @param <I> the type of the Flow input.
     * @param <O> the type of the Flow output.
     * @return a Flow wrapping the given flow that can produce timeout elements.
     */
    public static <I, O> Flow<I, O, NotUsed> of(
            final Flow<I, O, ?> flow,
            final Duration timeout,
            final Function<I, O> onTimeout) {
        checkNotNull(flow, "flow");
        checkNotNull(onTimeout, "onTimeout");

        return Flow.<I>create()
                .flatMapConcat(input -> single(input, flow, timeout, onTimeout));
    }

    /**
     * Builds a source that emits a timeout element if it took longer than the given timeout to process
     * the given input element.
     *
     * @param input the input element.
     * @param flow the flow with which the input element is processed.
     * @param timeoutDuration the maximum processing time of a single stream element in this flow.
     * @param onTimeout the function to generate timeout elements from input elements.
     * @param <I> the type of the Flow input.
     * @param <O> the type of the Flow output.
     * @return a source that emits the output element or a timeout element if the given flow took too long.
     */
    @SuppressWarnings("unchecked") // due to GraphDSL
    public static <I, O> Source<O, NotUsed> single(final I input, final Flow<I, O, ?> flow,
            final Duration timeoutDuration, final Function<I, O> onTimeout) {

        final Source<O, ?> timeoutSource = Source.single(input).initialDelay(timeoutDuration).map(onTimeout);

        final Source<O, ?> outputSource = Source.single(input).via(flow);

        return Source.fromGraph(GraphDSL.create(builder -> {
            final SourceShape<O> timeout = builder.add(timeoutSource);
            final SourceShape<O> output = builder.add(outputSource);
            final UniformFanInShape<O, O> merge = builder.add(Merge.create(2, true));
            final FlowShape<O, O> take1 = builder.add(Flow.<O>create().take(1));

            builder.from(timeout).toFanIn(merge);
            builder.from(output).toFanIn(merge);
            builder.from(merge).via(take1);

            return SourceShape.of(take1.out());
        }));
    }

}
