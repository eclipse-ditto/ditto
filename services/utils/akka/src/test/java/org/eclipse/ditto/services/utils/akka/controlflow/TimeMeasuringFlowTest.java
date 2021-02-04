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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Offset;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

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
    public void timeMeasuringInaccuracyIsLessThanTwoMS() {
        final Duration sleepDuration = Duration.ofMillis(5);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.fromFunction(x -> {
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            return x;
        });

        final PreparedTimer timer = DittoMetrics.timer("test-time-measuring-flow");
        final PreparedTimer timerSpy = spy(timer);
        final List<Duration> durations = new ArrayList<>();
        final Sink<Duration, CompletionStage<Done>> rememberDurations = Sink.<Duration>foreach(durations::add);
        new TestKit(system) {{
            Source.repeat("Test")
                    .via(TimeMeasuringFlow.measureTimeOf(flowThatNeedsSomeTime, timerSpy,rememberDurations))
                    .via(flowThatNeedsSomeTime) // This should not influence the time measuring above
                    .to(testSink)
                    .run(system);

            for (int i = 0; i < 10; i++) {
                sinkProbe.request(1L);
                sinkProbe.expectNext("Test");
            }

            final double averageDurationInNanos = durations.stream()
                    .mapToLong(Duration::toNanos)
                    .average()
                    .orElseThrow();
            final Offset<Double> twoMsOffset = Offset.offset((double) Duration.ofMillis(2).toNanos());
            assertThat(averageDurationInNanos).isCloseTo(sleepDuration.toNanos(), twoMsOffset);
            verify(timerSpy, times(10)).start();
        }};
    }

}
