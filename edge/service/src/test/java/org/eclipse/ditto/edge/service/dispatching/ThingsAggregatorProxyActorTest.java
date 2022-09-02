/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.UUID;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActor.AutoPilot;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ThingsAggregatorProxyActor}.
 */
public final class ThingsAggregatorProxyActorTest {

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    private static final DittoRuntimeException DITTO_RUNTIME_EXCEPTION =
            ThingIdInvalidException.newBuilder("invalidThingId")
                    .dittoHeaders(DITTO_HEADERS)
                    .build();
    private static final DittoInternalErrorException INTERNAL_ERROR_EXCEPTION =
            DittoInternalErrorException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
    private static final RetrieveThings RETRIEVE_THINGS_COMMAND =
            RetrieveThings.getBuilder(ThingId.of("ditto", "thing"))
                    .dittoHeaders(DITTO_HEADERS)
                    .build();

    private static ActorSystem actorSystem;

    @Test
    public void testHandleDittoRuntimeException() {
        new TestKit(actorSystem) {{
            final TestProbe targetActor = new TestProbe(actorSystem);
            targetActor.setAutoPilot(new AutoPilotAnsweringWithException(DITTO_RUNTIME_EXCEPTION));

            final Props props = ThingsAggregatorProxyActor.props(targetActor.ref());
            final ActorRef proxyActor = actorSystem.actorOf(props);

            proxyActor.tell(RETRIEVE_THINGS_COMMAND, getRef());
            expectMsg(DITTO_RUNTIME_EXCEPTION);
        }};
    }

    @Test
    public void testHandleGenericException() {
        new TestKit(actorSystem) {{
            final TestProbe targetActor = new TestProbe(actorSystem);
            targetActor.setAutoPilot(new AutoPilotAnsweringWithException(INTERNAL_ERROR_EXCEPTION));

            final Props props = ThingsAggregatorProxyActor.props(targetActor.ref());
            final ActorRef proxyActor = actorSystem.actorOf(props);

            proxyActor.tell(RETRIEVE_THINGS_COMMAND, getRef());
            expectMsg(INTERNAL_ERROR_EXCEPTION);
        }};
    }

    private static final class AutoPilotAnsweringWithException extends AutoPilot {

        private final Exception exceptionToRespond;

        private AutoPilotAnsweringWithException(final Exception exception) {
            exceptionToRespond = exception;
        }

        @Override
        public AutoPilot run(final ActorRef sender, final Object msg) {
            sender.tell(exceptionToRespond, ActorRef.noSender());
            return keepRunning();
        }
    }

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }
}
