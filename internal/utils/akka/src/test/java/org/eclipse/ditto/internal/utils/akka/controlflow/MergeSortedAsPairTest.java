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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import static org.assertj.core.api.Java6Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link MergeSortedAsPair}..
 */
public final class MergeSortedAsPairTest {

    private static ActorSystem system;

    // these become available after calling materializeTestProbes()
    private TestPublisher.Probe<Integer> source1Probe;
    private TestPublisher.Probe<Integer> source2Probe;
    private TestSubscriber.Probe<Pair<Integer, Integer>> sinkProbe;

    @BeforeClass
    public static void init() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void cleanup() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void testMergeSortedResult() {
        final Source<Integer, NotUsed> source1 = Source.from(List.of(1, 3, 5, 7, 9));
        final Source<Integer, NotUsed> source2 = Source.from(List.of(2, 3, 4, 5, 6));
        final List<Pair<Integer, Integer>> mergeResult = runWithMergeSortedAsPair(source1, source2);
        assertThat(mergeResult)
                .containsExactlyElementsOf(List.of(
                        Pair.create(1, 2),
                        Pair.create(3, 2),
                        Pair.create(3, 3),
                        Pair.create(5, 4),
                        Pair.create(5, 5),
                        Pair.create(7, 6),
                        Pair.create(7, Integer.MAX_VALUE),
                        Pair.create(9, Integer.MAX_VALUE)
                ));
    }

    @Test
    public void forIdenticalSources() {
        final Source<Integer, NotUsed> source1 = Source.from(List.of(1, 3, 5, 7, 9));
        final List<Pair<Integer, Integer>> mergeResult = runWithMergeSortedAsPair(source1, source1);
        assertThat(mergeResult)
                .containsExactlyElementsOf(List.of(
                        Pair.create(1, 1),
                        Pair.create(3, 3),
                        Pair.create(5, 5),
                        Pair.create(7, 7),
                        Pair.create(9, 9)
                ));
    }

    @Test
    public void testBehaviorSpecificationByMergeSortOnExample() {
        final Source<Integer, NotUsed> source1 = Source.from(List.of(1, 3, 5, 7, 9));
        final Source<Integer, NotUsed> source2 = Source.from(List.of(2, 3, 4, 5, 6));
        final List<Integer> specifiedMergeSortedResult = runSource(source1.mergeSorted(source2, Integer::compare));
        final List<Integer> actualMergeSortedResult = runSource(equivalentOfMergeSortedUnderTest(source1, source2));
        assertThat(actualMergeSortedResult).isEqualTo(specifiedMergeSortedResult);
    }

    @Test
    public void testBehaviorSpecificationByMergeSortOnEarlyExhaustion() {
        final Source<Integer, NotUsed> source1 = Source.from(List.of(1, 3));
        final Source<Integer, NotUsed> source2 = Source.from(List.of(2, 4, 6, 8));
        final List<Integer> specifiedMergeSortedResult = runSource(source1.mergeSorted(source2, Integer::compare));
        final List<Integer> actualMergeSortedResult = runSource(equivalentOfMergeSortedUnderTest(source1, source2));
        assertThat(actualMergeSortedResult).isEqualTo(specifiedMergeSortedResult);
    }

    @Test
    public void testIdenticalStartAndEnd() {
        materializeTestProbes();
        List.of(1, 3, 4).forEach(source1Probe::sendNext);
        List.of(1, 2, 4).forEach(source2Probe::sendNext);
        source1Probe.sendComplete();
        source2Probe.sendComplete();
        sinkProbe.request(9);
        sinkProbe.expectNext(Pair.create(1, 1));
        sinkProbe.expectNext(Pair.create(3, 2));
        sinkProbe.expectNext(Pair.create(3, 4));
        sinkProbe.expectNext(Pair.create(4, 4));
        sinkProbe.expectComplete();
    }

    @Test
    public void mergeEmptySources() {
        // source1 is empty
        final List<Integer> integers = List.of(1, 2, 3, 4, 5);
        assertThat(runWithMergeSortedAsPair(Source.empty(), Source.from(integers)))
                .containsExactlyElementsOf(
                        integers.stream().map(i -> Pair.create(Integer.MAX_VALUE, i)).collect(Collectors.toList()));

        // source2 is empty
        assertThat(runWithMergeSortedAsPair(Source.from(integers), Source.empty()))
                .containsExactlyElementsOf(
                        integers.stream().map(i -> Pair.create(i, Integer.MAX_VALUE)).collect(Collectors.toList()));

        // both sources empty
        assertThat(runWithMergeSortedAsPair(Source.empty(), Source.empty())).isEmpty();
    }

    @Test
    public void testUpstreamFailure() {
        materializeTestProbes();
        source1Probe.sendNext(1).sendNext(3);
        source2Probe.sendNext(2).sendNext(4).sendNext(6);
        sinkProbe.request(3);
        sinkProbe.expectNext(Pair.create(1, 2))
                .expectNext(Pair.create(3, 2))
                .expectNext(Pair.create(3, 4));
        final Throwable error = new IllegalStateException("source1 failure");
        source1Probe.sendError(error);
        source2Probe.expectCancellation();
        sinkProbe.expectError(error);
    }

    @Test
    public void testDownstreamCancellation() {
        materializeTestProbes();
        source1Probe.sendNext(1).sendNext(3);
        source2Probe.sendNext(2).sendNext(4).sendNext(6);
        sinkProbe.request(3);
        sinkProbe.expectNext(Pair.create(1, 2))
                .expectNext(Pair.create(3, 2))
                .expectNext(Pair.create(3, 4));
        sinkProbe.cancel();
        source1Probe.expectCancellation();
        source2Probe.expectCancellation();
    }

    @Test
    public void testSourcesDisjointInTimeWithTheEarlierSourceExhaustedFirst() {
        materializeTestProbes();
        source1Probe.sendNext(1).sendNext(3).sendComplete();
        sinkProbe.request(6);
        sinkProbe.expectNoMsg(FiniteDuration.create(250L, TimeUnit.MILLISECONDS));
        source2Probe.sendNext(2).sendNext(4).sendNext(6).sendNext(8).sendComplete();
        sinkProbe.expectNext(Pair.create(1, 2))
                .expectNext(Pair.create(3, 2))
                .expectNext(Pair.create(3, 4))
                .expectNext(Pair.create(Integer.MAX_VALUE, 4))
                .expectNext(Pair.create(Integer.MAX_VALUE, 6))
                .expectNext(Pair.create(Integer.MAX_VALUE, 8))
                .expectComplete();
    }

    @Test
    public void testSourcesDisjointInTimeWithTheLaterSourceExhaustedFirst() {
        materializeTestProbes();
        source1Probe.sendNext(1).sendNext(3).sendNext(5).sendNext(7).sendComplete();
        sinkProbe.request(6);
        sinkProbe.expectNoMsg(FiniteDuration.create(250L, TimeUnit.MILLISECONDS));
        source2Probe.sendNext(2).sendNext(4).sendComplete();
        sinkProbe.expectNext(Pair.create(1, 2))
                .expectNext(Pair.create(3, 2))
                .expectNext(Pair.create(3, 4))
                .expectNext(Pair.create(5, 4))
                .expectNext(Pair.create(5, Integer.MAX_VALUE))
                .expectNext(Pair.create(7, Integer.MAX_VALUE))
                .expectComplete();
    }

    private void materializeTestProbes() {
        final Pair<Pair<TestPublisher.Probe<Integer>, TestPublisher.Probe<Integer>>,
                TestSubscriber.Probe<Pair<Integer, Integer>>>
                probes =
                mergeSortedAsPairWithMat(TestSource.probe(system), TestSource.probe(system))
                        .toMat(TestSink.probe(system), Keep.both())
                        .run(system);
        source1Probe = probes.first().first();
        source2Probe = probes.first().second();
        sinkProbe = probes.second();
    }

    private static Source<Integer, NotUsed> equivalentOfMergeSortedUnderTest(
            final Source<Integer, ?> source1,
            final Source<Integer, ?> source2) {

        return mergeSortedAsPair(source1, source2)
                .mapConcat(pair -> pair.first() < pair.second()
                        ? List.of(pair.first())
                        : pair.first() > pair.second()
                        ? List.of(pair.second())
                        : List.of(pair.first(), pair.second())
                )
                .mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    private static Source<Pair<Integer, Integer>, NotUsed> mergeSortedAsPair(
            final Source<Integer, ?> source1,
            final Source<Integer, ?> source2) {

        return mergeSortedAsPairWithMat(source1, source2).mapMaterializedValue(ignored -> NotUsed.getInstance());
    }

    @SuppressWarnings("unchecked") // due to GraphDSL usage
    private static <M1, M2> Source<Pair<Integer, Integer>, Pair<M1, M2>> mergeSortedAsPairWithMat(
            final Source<Integer, M1> source1,
            final Source<Integer, M2> source2) {

        final Graph<SourceShape<Pair<Integer, Integer>>, Pair<M1, M2>> combinedSource =
                GraphDSL.create(source1, source2, Pair::create, (builder, s1, s2) -> {
                    final FanInShape2<Integer, Integer, Pair<Integer, Integer>> mergeSorted =
                            builder.add(MergeSortedAsPair.getInstance(Integer.MAX_VALUE, Integer::compare));
                    builder.from(s1).toInlet(mergeSorted.in0());
                    builder.from(s2).toInlet(mergeSorted.in1());
                    return SourceShape.of(mergeSorted.out());
                });

        return Source.fromGraph(combinedSource);
    }

    private static List<Pair<Integer, Integer>> runWithMergeSortedAsPair(final Source<Integer, ?> source1,
            final Source<Integer, ?> source2) {

        return runSource(mergeSortedAsPair(source1, source2));
    }

    private static <T> List<T> runSource(final Source<T, ?> source) {
        return source.runWith(Sink.seq(), system).toCompletableFuture().join();
    }
}
