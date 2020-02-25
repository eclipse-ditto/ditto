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

import java.util.Arrays;

import org.eclipse.ditto.model.base.exceptions.TooManyRequestsException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

public final class ErrorRespondingBufferTest {

    private static final Gauge GAUGE = DittoMetrics.gauge(ErrorRespondingBufferTest.class.getSimpleName());

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create();
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Test
    public void bufferRespondsWithTooManyRequestsExceptionIfBufferIsFull() {
        new TestKit(actorSystem) {{
            final WithSender<RetrieveThing> msg1 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final WithSender<RetrieveThing> msg2 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final WithSender<RetrieveThing> msg3 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final Source<WithSender<RetrieveThing>, NotUsed> sourceUnderTest =
                    Source.from(Arrays.asList(msg1, msg2, msg3));

            sourceUnderTest
                    .via(ErrorRespondingBuffer.of(1, GAUGE))
                    .runWith(TestSink.probe(actorSystem), ActorMaterializer.create(actorSystem))
                    .request(1)
                    .expectNext(msg1);

            expectMsgClass(TooManyRequestsException.class);
        }};
    }

    @Test
    public void bufferDropsNonWithSenderMessageIfBufferIsFull() {
        new TestKit(actorSystem) {{
            final Source<Integer, NotUsed> sourceUnderTest = Source.from(Arrays.asList(1, 2, 4));

            sourceUnderTest
                    .via(ErrorRespondingBuffer.of(1, GAUGE))
                    .runWith(TestSink.probe(actorSystem), ActorMaterializer.create(actorSystem))
                    .request(1)
                    .expectNext(1);

            expectNoMessage();
        }};
    }

    @Test
    public void bufferWorksOffIncomingMessagesWhenTheyAreAllConsumed() {
        new TestKit(actorSystem) {{
            final WithSender<RetrieveThing> msg1 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final WithSender<RetrieveThing> msg2 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final WithSender<RetrieveThing> msg3 = ControlFlowFactory
                    .messageWithSender(RetrieveThing.of(ThingId.generateRandom(), DittoHeaders.empty()), getRef());
            final Source<WithSender<RetrieveThing>, NotUsed> sourceUnderTest =
                    Source.from(Arrays.asList(msg1, msg2, msg3));

            sourceUnderTest
                    .via(ErrorRespondingBuffer.of(1, GAUGE))
                    .runWith(TestSink.probe(actorSystem), ActorMaterializer.create(actorSystem))
                    .request(3)
                    .expectNext(msg1, msg2, msg3);

            expectNoMessage();
        }};
    }

}