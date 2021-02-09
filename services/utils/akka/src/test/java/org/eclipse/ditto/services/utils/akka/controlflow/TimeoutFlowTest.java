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
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

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
    public void expectTimeoutMessage() {
        final Duration timeout = Duration.ofSeconds(1);
        final Duration sleepDuration = timeout.plusSeconds(1);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.fromFunction(x -> {
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            return x;
        });

        new TestKit(system) {{

            final Flow<String, String, NotUsed> withTimeoutFLow =
                    TimeoutFlow.of(flowThatNeedsSomeTime,
                            timeout.toSeconds(),
                            "Hello",
                            getRef(),
                            Materializer.apply(system));

            Source.repeat("Test")
                    .via(withTimeoutFLow)
                    .to(testSink)
                    .run(system);

            sinkProbe.request(1L);
            sinkProbe.expectNext("Test");

            expectMsg("Hello");
        }};
    }

    @Test
    public void expectNoTimeoutMessage() {
        final Duration timeout = Duration.ofSeconds(1);
        final Duration sleepDuration = timeout.dividedBy(2);
        final Flow<String, String, NotUsed> flowThatNeedsSomeTime = Flow.fromFunction(x -> {
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
            return x;
        });

        new TestKit(system) {{

            final Flow<String, String, NotUsed> withTimeoutFLow =
                    TimeoutFlow.of(flowThatNeedsSomeTime,
                            timeout.toSeconds(),
                            "Hello",
                            getRef(),
                            Materializer.apply(system));

            Source.repeat("Test")
                    .via(withTimeoutFLow)
                    .to(testSink)
                    .run(system);

            sinkProbe.request(1L);
            sinkProbe.expectNext("Test");

            expectNoMessage();
        }};
    }

}
