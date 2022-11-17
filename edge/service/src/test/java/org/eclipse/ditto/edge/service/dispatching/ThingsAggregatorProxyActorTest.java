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

import java.util.List;
import java.util.UUID;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.TestActor.AutoPilot;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

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

    private static final String NAMESPACE = "ditto";
    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "thing");

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    private static final DittoRuntimeException DITTO_RUNTIME_EXCEPTION =
            ThingIdInvalidException.newBuilder("invalidThingId")
                    .dittoHeaders(DITTO_HEADERS)
                    .build();
    private static final DittoInternalErrorException INTERNAL_ERROR_EXCEPTION =
            DittoInternalErrorException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
    private static final RetrieveThings RETRIEVE_THINGS_COMMAND =
            RetrieveThings.getBuilder(THING_ID).namespace(NAMESPACE)
                    .dittoHeaders(DITTO_HEADERS)
                    .build();
    private static final RetrieveThingResponse RETRIEVE_THING_RESPONSE =
            RetrieveThingResponse.of(THING_ID, Thing.newBuilder().setId(THING_ID).build().toJsonString(),
                    DITTO_HEADERS);

    private static final RetrieveThingsResponse RETRIEVE_THINGS_RESPONSE =
            RetrieveThingsResponse.of(List.of(Thing.newBuilder().setId(THING_ID).build().toJsonString()),
                    NAMESPACE,
                    DITTO_HEADERS);

    private static SourceRef<Jsonifiable<?>> getSourceRef(final Iterable<Jsonifiable<?>> jsonValues) {
        final var source = Source.from(jsonValues);
        return source.runWith(StreamRefs.sourceRef(), Materializer.apply(ACTOR_SYSTEM_RESOURCE.getActorSystem()));
    }

    @Test
    public void testHandleDittoRuntimeException() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
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
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            final TestProbe targetActor = new TestProbe(actorSystem);
            targetActor.setAutoPilot(new AutoPilotAnsweringWithException(INTERNAL_ERROR_EXCEPTION));

            final Props props = ThingsAggregatorProxyActor.props(targetActor.ref());
            final ActorRef proxyActor = actorSystem.actorOf(props);

            proxyActor.tell(RETRIEVE_THINGS_COMMAND, getRef());
            expectMsg(INTERNAL_ERROR_EXCEPTION);
        }};
    }

    @Test
    public void shutdownWithoutTask() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            final TestProbe pubSubMediator = new TestProbe(actorSystem);
            final Props props = ThingsAggregatorProxyActor.props(pubSubMediator.ref());
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(ThingsAggregatorProxyActor.Control.SERVICE_REQUESTS_DONE, getRef());
            expectMsg(Done.getInstance());
        }};
    }

    @Test
    public void shutdownWithTask() {
        final ActorSystem actorSystem = ACTOR_SYSTEM_RESOURCE.getActorSystem();
        new TestKit(actorSystem) {{
            final TestProbe pubSubMediator = new TestProbe(actorSystem);
            final Props props = ThingsAggregatorProxyActor.props(pubSubMediator.ref());
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(RETRIEVE_THINGS_COMMAND, getRef());
            pubSubMediator.expectMsgClass(DistributedPubSubMediator.Publish.class);

            underTest.tell(ThingsAggregatorProxyActor.Control.SERVICE_REQUESTS_DONE, getRef());
            expectNoMsg();

            pubSubMediator.reply(getSourceRef(List.of(RETRIEVE_THING_RESPONSE)));
            expectMsg(RETRIEVE_THINGS_RESPONSE);
            expectMsg(Done.getInstance());
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

}
