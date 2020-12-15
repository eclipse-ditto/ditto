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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Percentage;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.OnStopHandler;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StoppedTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

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
    public void timeMeasuringInaccuracyIsLessThanOnePercent() {
        final Duration sleepDuration = Duration.ofMillis(500);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.fromFunction(x -> {
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            return x;
        });

        final PreparedTimer timer = DittoMetrics.timer("test-time-measuring-flow");
        final PreparedTimer timerSpy = spy(timer);
        final TimerCaptor timerCaptor = new TimerCaptor();
        doAnswer(timerCaptor).when(timerSpy).start();

        new TestKit(system) {{
            Source.repeat("Test")
                    .via(TimeMeasuringFlow.measureTimeOf(flowThatNeedsSomeTime, timerSpy))
                    .via(flowThatNeedsSomeTime) // This should not influence the time measuring above
                    .to(testSink)
                    .run(system);

            // Ignore first run because of overhead in the first run
            sinkProbe.request(1L);
            sinkProbe.expectNext("Test");

            final List<Duration> durations = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                sinkProbe.request(1L);
                sinkProbe.expectNext("Test");
                durations.add(timerCaptor.getDuration());
            }

            final double averageDurationInNanos = durations.stream()
                    .mapToLong(Duration::toNanos)
                    .average()
                    .orElseThrow();

            assertThat(averageDurationInNanos).isCloseTo(sleepDuration.toNanos(), Percentage.withPercentage(1));
            verify(timerSpy, times(11)).start();
        }};
    }

    static class TimerCaptor implements Answer<StartedTimer> {

        private StoppedTimer stoppedTimer;

        Duration getDuration() {
            return stoppedTimer.getDuration();
        }

        @Override
        public StartedTimer answer(final InvocationOnMock invocation) throws Throwable {
            final StartedTimer startedTimer = (StartedTimer) invocation.callRealMethod();
            startedTimer.onStop(new OnStopHandler(stoppedTimer -> this.stoppedTimer = stoppedTimer));
            return startedTimer;
        }

    }

}
