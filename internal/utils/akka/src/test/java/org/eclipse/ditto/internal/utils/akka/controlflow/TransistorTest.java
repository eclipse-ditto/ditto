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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL$;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link Transistor} with all combinations of activation orders
 * of its 2 inlets and 1 outlet, and whether the amount of elements delivered is limited by collector elements, credits, or
 * downstream requests. An inlet activates when it pushes; an outlet activates when it pulls. These tests are supposed
 * to catch graph stage logic interface violations such as double-pulling.
 */
@RunWith(Parameterized.class)
public final class TransistorTest {

    /**
     * Name of the inlets and the outlet.
     */
    public enum Terminal {
        COLLECTOR,
        BASE,
        EMITTER
    }

    /**
     * @return All combinations of inlet/outlet activation orders and the limiting agent of elements delivered.
     */
    @Parameterized.Parameters(name = "{0}")
    public static List<Parameter> parameters() {
        return permutations(Arrays.asList(Terminal.values()))
                .flatMap(order -> Arrays.stream(Terminal.values()).map(limit -> new Parameter(order, limit)))
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter
    public Parameter parameter;

    private ActorSystem system;
    private TestPublisher.Probe<Integer> collector;
    private TestPublisher.Probe<Integer> base;
    private TestSubscriber.Probe<Integer> emitter;

    /**
     * Connect a transistor to 2 test collectors and a test sink.
     */
    @Before
    public void init() {
        system = ActorSystem.create();
        final Source<Integer, TestPublisher.Probe<Integer>> collectorSource = TestSource.probe(system);
        final Source<Integer, TestPublisher.Probe<Integer>> baseSource = TestSource.probe(system);
        final Sink<Integer, TestSubscriber.Probe<Integer>> emitterSink = TestSink.probe(system);
        final Transistor<Integer> underTest = Transistor.of();

        final Graph<SourceShape<Integer>, Pair<TestPublisher.Probe<Integer>, TestPublisher.Probe<Integer>>>
                collectorGateTransistor =
                GraphDSL$.MODULE$.create3(
                        collectorSource, baseSource, underTest,
                        (collector, base, notUsed) -> Pair.create(collector, base),
                        (builder, collectorShape, baseShape, transistorShape) -> {
                            builder.from(collectorShape.out()).toInlet(transistorShape.in0());
                            builder.from(baseShape.out()).toInlet(transistorShape.in1());
                            return SourceShape.of(transistorShape.out());
                        });

        final Pair<Pair<TestPublisher.Probe<Integer>, TestPublisher.Probe<Integer>>, TestSubscriber.Probe<Integer>> m =
                Source.fromGraph(collectorGateTransistor)
                        .toMat(emitterSink, Keep.both())
                        .run(system);

        collector = m.first().first();
        base = m.first().second();
        emitter = m.second();
    }

    /**
     * Stop the actor system, terminating all running streams.
     */
    @After
    public void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void test() {
        // Push/pull from inlets/outlet according to their activation order.
        // The limiting terminal restricts the number of elements passing through to 4.
        for (final Terminal terminal : parameter.activationOrder) {
            switch (terminal) {
                case COLLECTOR:
                    collector.sendNext(1).sendNext(2).sendNext(3).sendNext(4);
                    if (isNotLimiting(Terminal.COLLECTOR)) {
                        collector.sendNext(5).sendNext(6).sendNext(7).sendNext(8);
                    }
                    break;
                case BASE:
                    base.sendNext(4);
                    if (isNotLimiting(Terminal.BASE)) {
                        base.sendNext(5).sendNext(7).sendNext(9);
                    }
                    break;
                case EMITTER:
                    emitter.request(2).request(2);
                    if (isNotLimiting(Terminal.EMITTER)) {
                        emitter.request(3).request(2);
                    }
                    break;
            }
        }
        // Exactly 4 elements should pass through the stream.
        emitter.expectNext(1, 2, 3, 4);

        if (isNotLimiting(Terminal.EMITTER)) {
            collector.sendComplete();
            base.sendComplete();
            emitter.expectComplete();
        } else {
            emitter.cancel();
            base.expectCancellation();
            collector.expectCancellation();
        }
    }

    private boolean isNotLimiting(final Terminal terminal) {
        return parameter.limitingTerminal != terminal;
    }

    /**
     * Generates all permutations of a list of elements.
     *
     * @param elements what to permute.
     * @param <T> type of elements.
     * @return stream of all permutations of the elements.
     */
    private static <T> Stream<List<T>> permutations(final List<T> elements) {
        return permutations(elements.size())
                .map(indices -> indices.stream().map(elements::get).collect(Collectors.toList()));
    }

    /**
     * Generate all permutations of 0, 1, ..., n-1.
     *
     * @param n a positive integer.
     * @return permutations of all non-negative integers smaller than n.
     */
    private static Stream<List<Integer>> permutations(final int n) {
        if (n <= 0) {
            return Stream.of(Collections.emptyList());
        } else {
            return IntStream.range(0, n)
                    .boxed()
                    .flatMap(i -> permutations(n - 1).map(list -> {
                        final List<Integer> augmentedList = new ArrayList<>(n);
                        augmentedList.addAll(list);
                        augmentedList.add(i, n - 1);
                        return augmentedList;
                    }));
        }
    }

    /**
     * Parameter of the test: A permutation of activation orders and one of the terminals that should limit the stream.
     */
    public static final class Parameter {

        private final List<Terminal> activationOrder;
        private final Terminal limitingTerminal;

        private Parameter(final List<Terminal> activationOrder, final Terminal limitingTerminal) {
            this.activationOrder = activationOrder;
            this.limitingTerminal = limitingTerminal;
        }

        @Override
        public String toString() {
            return String.format("activation in order %s limited by %s", activationOrder.toString(),
                    limitingTerminal.name());
        }
    }
}
