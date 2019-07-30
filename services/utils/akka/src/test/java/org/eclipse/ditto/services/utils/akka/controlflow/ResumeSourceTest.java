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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.akka.controlflow.ResumeSource}.
 */
public final class ResumeSourceTest {

    private ActorSystem system;
    private ActorMaterializer mat;
    private TestPublisher.Probe<Integer> sourceProbe;
    private TestSubscriber.Probe<Integer> sinkProbe;
    private Source<Integer, NotUsed> testSource;
    private Sink<Integer, NotUsed> testSink;

    @Before
    public void init() {
        system = ActorSystem.create();
        mat = ActorMaterializer.create(system);

        rematerializeSource();

        // materialize sink once - it never fails.
        final Sink<Integer, TestSubscriber.Probe<Integer>> sink = TestSink.probe(system);
        final Pair<TestSubscriber.Probe<Integer>, Sink<Integer, NotUsed>> sinkPair = sink.preMaterialize(mat);
        sinkProbe = sinkPair.first();
        testSink = sinkPair.second();
    }

    @After
    public void cleanup() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    @Ignore("TODO: cancellation isn't working.")
    public void testCancellation() {
        new TestKit(system) {{
            final Source<Integer, NotUsed> underTest = createResumeSource(getRef(), -1);

            underTest.runWith(testSink, mat);

            // start stream with demand
            sinkProbe.request(100L);
            expectMsg(0);
            reply(testSource);

            // send some elements followed by cancellation
            sourceProbe.sendNext(1).sendNext(2);
            sinkProbe.expectNext(1, 2);
            sinkProbe.cancel();
            sourceProbe.expectCancellation();
        }};
    }

    @Test
    public void testCompletion() {
        new TestKit(system) {{
            final Source<Integer, NotUsed> underTest = createResumeSource(getRef(), -1);

            underTest.runWith(testSink, mat);

            // start stream with demand
            sinkProbe.request(100L);
            expectMsg(0);
            reply(testSource);

            // send some elements followed by completion
            sourceProbe.sendNext(1).sendNext(2);
            sourceProbe.sendComplete();
            sinkProbe.expectNext(1, 2);
            sinkProbe.expectComplete();
        }};
    }

    @Test
    public void testResumption() {
        new TestKit(system) {{
            final Source<Integer, NotUsed> underTest = createResumeSource(getRef(), -1);

            underTest.runWith(testSink, mat);

            // start stream with demand
            sinkProbe.request(100L);
            expectMsg(0);
            reply(testSource);

            // send some elements followed by failure
            sourceProbe.sendNext(1).sendNext(2);
            sinkProbe.expectNext(1, 2);
            sourceProbe.sendError(new IllegalStateException("I FAILED"));

            // expect new seed equal to final element sent
            expectMsg(2);
            rematerializeSource();
            reply(testSource);

            // resume stream until completion
            sourceProbe.sendNext(3).sendNext(4).sendNext(5);
            sourceProbe.sendComplete();
            sinkProbe.expectNext(3, 4, 5);
            sinkProbe.expectComplete();
        }};
    }

    @Test
    public void testFailureAfterMaxRestarts() {
        // disable logging to suppress expected stacktrace
        system.eventStream().setLogLevel(Attributes.logLevelOff());
        new TestKit(system) {{
            final Source<Integer, NotUsed> underTest = createResumeSource(getRef(), 0);

            underTest.runWith(testSink, mat);

            // start stream with demand
            sinkProbe.request(100L);
            expectMsg(0);
            reply(testSource);

            // send some elements followed by failure
            sourceProbe.sendNext(1).sendNext(2);
            sinkProbe.expectNext(1, 2);
            final Throwable error = new IllegalStateException("Expected error");
            sourceProbe.sendError(error);

            // expect stream failed
            final Throwable actualError = sinkProbe.expectError();
            assertThat(actualError).isInstanceOf(ExecutionException.class);
            assertThat(actualError.getCause()).isEqualTo(error);
        }};
    }

    private void rematerializeSource() {
        final Source<Integer, TestPublisher.Probe<Integer>> source = TestSource.probe(system);
        final Pair<TestPublisher.Probe<Integer>, Source<Integer, NotUsed>> sourcePair = source.preMaterialize(mat);
        sourceProbe = sourcePair.first();
        testSource = sourcePair.second();
    }

    /**
     * Create a resume-source for test.
     *
     * @param testProbe the test probe to ask for new sources on resumption.
     * @param maxRestarts maximum number of restarts until the source fails.
     * @return the resume-source for test.
     */
    @SuppressWarnings("unchecked")
    private static Source<Integer, NotUsed> createResumeSource(final ActorRef testProbe, final int maxRestarts) {
        return ResumeSource.onFailureWithBackoff(Duration.ZERO, Duration.ZERO, maxRestarts, Duration.ZERO, 0,
                seed -> (Source<Integer, NotUsed>) Patterns.ask(testProbe, seed, Duration.ofSeconds(10L))
                        .toCompletableFuture()
                        .join(),
                1,
                xs -> xs.isEmpty() ? 0 : xs.get(xs.size() - 1));
    }
}
