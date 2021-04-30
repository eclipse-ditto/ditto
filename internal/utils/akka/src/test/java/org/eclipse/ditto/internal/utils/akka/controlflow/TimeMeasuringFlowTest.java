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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.data.Offset;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalAnswers;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Unit tests for {@link TimeMeasuringFlow}.
 */
public final class TimeMeasuringFlowTest {

    private ActorSystem system;
    private TestSubscriber.Probe<String> sinkProbe;
    private Sink<String, NotUsed> testSink;

    @Before
    public void setup() {
        system = ActorSystem.create();
        final Sink<String, TestSubscriber.Probe<String>> sink = TestSink.probe(system);
        final Pair<TestSubscriber.Probe<String>, Sink<String, NotUsed>> sinkPair = sink.preMaterialize(system);
        sinkProbe = sinkPair.first();
        testSink = sinkPair.second();
    }

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void timeMeasuresOnlyTimeOfWrappedFlow() {
        final Duration sleepDuration = Duration.ofMillis(100);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.fromFunction(x -> {
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            return x;
        });

        final PreparedTimer timer = DittoMetrics.timer("test-time-measuring-flow");
        final PreparedTimer timerMock = mock(PreparedTimer.class);
        when(timerMock.start()).thenAnswer(AdditionalAnswers.delegatesTo(timer));
        final Sink<Duration, TestSubscriber.Probe<Duration>> sink = TestSink.<Duration>probe(system);
        final Pair<TestSubscriber.Probe<Duration>, Sink<Duration, NotUsed>> sinkPair = sink.preMaterialize(system);
        final TestSubscriber.Probe<Duration> durationProbe = sinkPair.first();
        new TestKit(system) {{
            Source.repeat("Test")
                    .via(TimeMeasuringFlow.measureTimeOf(flowThatNeedsSomeTime, timerMock, sinkPair.second()))
                    .via(flowThatNeedsSomeTime) // This should not influence the time measuring above
                    .to(testSink)
                    .run(system);

            final int numberOfRepetitions = 10;
            final ArrayList<String> expectedResults = new ArrayList<>(numberOfRepetitions);
            for (int i = 0; i < numberOfRepetitions; i++) {
                expectedResults.add("Test");
            }
            sinkProbe.request(numberOfRepetitions);
            durationProbe.request(numberOfRepetitions);
            sinkProbe.expectNextN(CollectionConverters.asScala(expectedResults).toSeq());
            final List<Duration> durations =
                    CollectionConverters.asJava(durationProbe.expectNextN(numberOfRepetitions));

            final double averageDurationInNanos = durations.stream()
                    .mapToLong(Duration::toNanos)
                    .average()
                    .orElseThrow();
            /*
             * Needs to be lesser than sleep duration to verify that potential inaccuracies are not mixed up with
             * what this test you ensure.
             */
            final Offset<Double> offset = Offset.offset((double) Duration.ofMillis(20).toNanos());
            assertThat(averageDurationInNanos).isCloseTo(sleepDuration.toNanos(), offset);
            verify(timerMock, times(numberOfRepetitions)).start();
        }};
    }

    @Test
    public void keepsParallelism() {
        final Duration sleepDuration = Duration.ofMillis(100);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTimeButUsesParallelism =
                Flow.<String>create().flatMapMerge(10, input -> Source.single(input).via
                        (Flow.<String, String>fromFunction(x -> {
                            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
                            return x;
                        }).async()));

        final PreparedTimer timer = DittoMetrics.timer("test-time-measuring-flow");
        final PreparedTimer timerMock = mock(PreparedTimer.class);
        final int numberOfRepetitions = 10;
        when(timerMock.start()).thenAnswer(AdditionalAnswers.delegatesTo(timer));
        final List<String> expectedResults = IntStream.range(0, numberOfRepetitions)
                .mapToObj(i -> "Test")
                .collect(Collectors.toList());
        new TestKit(system) {{
            Source.from(expectedResults)
                    .via(TimeMeasuringFlow.measureTimeOf(flowThatNeedsSomeTimeButUsesParallelism, timerMock))
                    .to(testSink)
                    .run(system);
            final Instant start = Instant.now();
            sinkProbe.request(numberOfRepetitions);
            sinkProbe.expectNextN(CollectionConverters.asScala(expectedResults).toSeq());
            final Instant end = Instant.now();
            final Duration duration = Duration.ofMillis(end.toEpochMilli() - start.toEpochMilli());
            assertThat(duration).isLessThan(sleepDuration.multipliedBy(numberOfRepetitions));
            verify(timerMock, times(numberOfRepetitions)).start();
        }};
    }

}
