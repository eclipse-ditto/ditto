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
package org.eclipse.ditto.services.utils.akka.controlflow;

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
import akka.stream.ActorMaterializer;
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
 * Tests {@link org.eclipse.ditto.services.utils.akka.controlflow.Transistor}.
 * TODO: document what this tests.
 */
@RunWith(Parameterized.class)
public final class TransistorTest {

    public enum Terminal {
        SOURCE,
        GATE,
        DRAIN
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Parameter> parameters() {
        return permutations(Arrays.asList(Terminal.values()))
                .flatMap(order -> Arrays.stream(Terminal.values()).map(limit -> new Parameter(order, limit)))
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter
    public Parameter parameter;

    private ActorSystem system;
    private TestPublisher.Probe<Integer> source;
    private TestPublisher.Probe<Integer> gate;
    private TestSubscriber.Probe<Integer> drain;

    @Before
    public void init() {
        system = ActorSystem.create();
        final Source<Integer, TestPublisher.Probe<Integer>> sourceSource = TestSource.probe(system);
        final Source<Integer, TestPublisher.Probe<Integer>> gateSource = TestSource.probe(system);
        final Sink<Integer, TestSubscriber.Probe<Integer>> drainSink = TestSink.probe(system);
        final Transistor<Integer> underTest = Transistor.of();

        final Graph<SourceShape<Integer>, Pair<TestPublisher.Probe<Integer>, TestPublisher.Probe<Integer>>>
                sourceGateTransistor =
                GraphDSL$.MODULE$.create3(
                        sourceSource, gateSource, underTest,
                        (source, gate, notUsed) -> Pair.create(source, gate),
                        (builder, sourceShape, gateShape, transistorShape) -> {
                            builder.from(sourceShape.out()).toInlet(transistorShape.in0());
                            builder.from(gateShape.out()).toInlet(transistorShape.in1());
                            return SourceShape.of(transistorShape.out());
                        });

        final Pair<Pair<TestPublisher.Probe<Integer>, TestPublisher.Probe<Integer>>, TestSubscriber.Probe<Integer>> m =
                Source.fromGraph(sourceGateTransistor)
                        .toMat(drainSink, Keep.both())
                        .run(ActorMaterializer.create(system));

        source = m.first().first();
        gate = m.first().second();
        drain = m.second();
    }

    @After
    public void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void test() {
        for (final Terminal terminal : parameter.activationOrder) {
            switch (terminal) {
                case SOURCE:
                    source.sendNext(1).sendNext(2).sendNext(3).sendNext(4);
                    if (isNotLimiting(Terminal.SOURCE)) {
                        source.sendNext(5).sendNext(6).sendNext(7).sendNext(8);
                    }
                    break;
                case GATE:
                    gate.sendNext(1).sendNext(3);
                    if (isNotLimiting(Terminal.GATE)) {
                        gate.sendNext(5).sendNext(7).sendNext(9);
                    }
                    break;
                case DRAIN:
                    drain.request(4);
                    if (isNotLimiting(Terminal.DRAIN)) {
                        drain.request(3).request(2);
                    }
                    break;
            }
        }
        drain.expectNext(1, 2, 3, 4);
        source.sendComplete();
        gate.sendComplete();
        drain.expectComplete();
    }

    private boolean isNotLimiting(final Terminal terminal) {
        return parameter.limitingTerminal != terminal;
    }

    private static <T> Stream<List<T>> permutations(final List<T> elements) {
        return permutations(elements.size())
                .map(indices -> indices.stream().map(elements::get).collect(Collectors.toList()));
    }

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
