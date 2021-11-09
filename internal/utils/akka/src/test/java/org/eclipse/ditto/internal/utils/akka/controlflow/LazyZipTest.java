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

import org.junit.After;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link LazyZip}.
 */
public final class LazyZipTest {


    private final ActorSystem actorSystem;
    private final Graph<FanInShape2<Object, Object, Pair<Object, Object>>, NotUsed> underTest;
    private final TestPublisher.Probe<Object> source1;
    private final TestPublisher.Probe<Object> source2;
    private final TestSubscriber.Probe<Pair<Object, Object>> sink;

    @SuppressWarnings("unchecked")
    public LazyZipTest() {
        actorSystem = ActorSystem.create();
        underTest = LazyZip.of();
        final var testSource1 = TestSource.probe(actorSystem);
        final var testSource2 = TestSource.probe(actorSystem);
        final var zippedSource =
                Source.fromGraph(GraphDSL.create(testSource1, testSource2, Pair::create, (builder, p1, p2) -> {
                    final var zip = builder.add(underTest);
                    builder.from(p1).toInlet(zip.in0());
                    builder.from(p2).toInlet(zip.in1());
                    return SourceShape.of(zip.out());
                }));
        final var testSink = TestSink.<Pair<Object, Object>>probe(actorSystem);
        final var mat = zippedSource.toMat(testSink, Keep.both()).run(actorSystem);
        source1 = mat.first().first();
        source2 = mat.first().second();
        sink = mat.second();
        sink.ensureSubscription();
        source1.ensureSubscription();
        source2.ensureSubscription();
    }

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    // Not possible to test lazy request due to async boundaries with input buffers around test probes.

    @Test
    public void eagerCancel() {
        sink.cancel();
        source1.expectCancellation();
        source2.expectCancellation();
    }

    @Test
    public void eagerComplete1() {
        source1.sendComplete();
        source2.expectCancellation();
        sink.expectComplete();
    }

    @Test
    public void eagerComplete2() {
        source2.sendComplete();
        source1.expectCancellation();
        sink.expectComplete();
    }

    @Test
    public void error1() {
        final var error = new IllegalStateException("Expected");
        source1.sendError(error);
        source2.expectCancellation();
        sink.expectError(error);
    }
}
