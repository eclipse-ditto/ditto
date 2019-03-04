/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.aggregator;

import java.util.UUID;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
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
public class ThingsAggregatorProxyActorTest {

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    private static final DittoRuntimeException DITTO_RUNTIME_EXCEPTION =
            DittoRuntimeException.newBuilder("test.error", HttpStatusCode.BAD_REQUEST)
                    .dittoHeaders(DITTO_HEADERS)
                    .build();
    private static final GatewayInternalErrorException GATEWAY_INTERNAL_ERROR_EXCEPTION =
            GatewayInternalErrorException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
    private static final RetrieveThings RETRIEVE_THINGS_COMMAND = RetrieveThings.getBuilder("ditto:thing")
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
            targetActor.setAutoPilot(new AutoPilotAnsweringWithException(GATEWAY_INTERNAL_ERROR_EXCEPTION));

            final Props props = ThingsAggregatorProxyActor.props(targetActor.ref());
            final ActorRef proxyActor = actorSystem.actorOf(props);

            proxyActor.tell(RETRIEVE_THINGS_COMMAND, getRef());
            expectMsg(GATEWAY_INTERNAL_ERROR_EXCEPTION);
        }};
    }

    private static class AutoPilotAnsweringWithException extends AutoPilot {

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