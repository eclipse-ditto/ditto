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
package org.eclipse.ditto.internal.utils.pekko.controlflow;

import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.apache.pekko.testkit.javadsl.TestKit;

public final class TimeoutFlowTest {

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
    public void expectTimeout() {
        new TestKit(system) {{
            final Duration timeout = dilated(Duration.ofMillis(10));
            final Duration sleepDuration = dilated(Duration.ofSeconds(3));
            final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.<String>create()
                    .flatMapConcat(element -> Source.single(element).initialDelay(sleepDuration));

            final Flow<String, String, NotUsed> withTimeoutFLow =
                    TimeoutFlow.of(flowThatNeedsSomeTime, timeout, input -> "Timeout");

            Source.repeat("Test")
                    .via(withTimeoutFLow)
                    .to(testSink)
                    .run(system);

            sinkProbe.request(1L);
            sinkProbe.expectNext("Timeout");
        }};
    }

    @Test
    public void expectNoTimeout() {

        new TestKit(system) {{
            final Duration timeout = dilated(Duration.ofSeconds(3));
            final Duration sleepDuration = dilated(Duration.ofMillis(10));
            final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.<String>create()
                    .flatMapConcat(element -> Source.single(element).initialDelay(sleepDuration));

            final Flow<String, String, NotUsed> withTimeoutFLow =
                    TimeoutFlow.of(flowThatNeedsSomeTime, timeout, input -> "Timeout");

            Source.repeat("Test")
                    .via(withTimeoutFLow)
                    .to(testSink)
                    .run(system);

            sinkProbe.request(1L);
            sinkProbe.expectNext("Test");
        }};
    }

    @Test
    public void cancelFlow() {

        new TestKit(system) {{
            final Duration timeout = dilated(Duration.ofMillis(10));
            final Duration sleepDuration = dilated(Duration.ofMinutes(3));
            final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.<String>create()
                    .flatMapConcat(element -> Source.single(element).initialDelay(sleepDuration));

            final Flow<String, String, NotUsed> withTimeoutFLow =
                    TimeoutFlow.of(flowThatNeedsSomeTime, timeout, input -> "Timeout");

            final var testSourcePair = TestSource.<String>probe(system).preMaterialize(system);
            final var sourceProbe = testSourcePair.first();

            testSourcePair.second().via(withTimeoutFLow).to(testSink).run(system);

            sinkProbe.request(2L);
            sourceProbe.ensureSubscription();
            sourceProbe.sendNext("Test");
            sinkProbe.expectNext("Timeout");

            // WHEN: downstream cancelled before all demands are fulfilled
            sinkProbe.cancel();

            // THEN: cancellation should propagate upstream
            sourceProbe.sendNext("Test");
            sourceProbe.expectCancellation();
        }};
    }

}
