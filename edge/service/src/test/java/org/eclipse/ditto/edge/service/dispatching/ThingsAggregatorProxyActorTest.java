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

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.testkit.TestActor.AutoPilot;

/**
 * Tests {@link ThingsAggregatorProxyActor}.
 */
public final class ThingsAggregatorProxyActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Test
    public void testHandleDittoRuntimeException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var thingIdInvalidException = ThingIdInvalidException.newBuilder("invalidThingId")
                .dittoHeaders(dittoHeaders)
                .build();
        final var senderActor = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var targetActor = ACTOR_SYSTEM_RESOURCE.newTestKit();
        targetActor.setAutoPilot(new AutoPilotAnsweringWithException(thingIdInvalidException));
        final var proxyActor = ACTOR_SYSTEM_RESOURCE.newActor(ThingsAggregatorProxyActor.props(targetActor.getRef()));

        proxyActor.tell(
                RetrieveThings.getBuilder(ThingId.of("ditto", "thing")).dittoHeaders(dittoHeaders).build(),
                senderActor.getRef()
        );

        senderActor.expectMsg(thingIdInvalidException);
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void testHandleGenericException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var dittoInternalErrorException = DittoInternalErrorException.newBuilder()
                .dittoHeaders(dittoHeaders)
                .build();
        final var senderActor = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var targetActor = ACTOR_SYSTEM_RESOURCE.newTestKit();
        targetActor.setAutoPilot(new AutoPilotAnsweringWithException(dittoInternalErrorException));
        final var proxyActor = ACTOR_SYSTEM_RESOURCE.newActor(ThingsAggregatorProxyActor.props(targetActor.getRef()));

        proxyActor.tell(
                RetrieveThings.getBuilder(ThingId.of("ditto", "thing")).dittoHeaders(dittoHeaders).build(),
                senderActor.getRef()
        );

        senderActor.expectMsg(dittoInternalErrorException);
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

}
