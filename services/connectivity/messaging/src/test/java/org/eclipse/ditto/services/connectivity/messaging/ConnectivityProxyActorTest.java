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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectivityProxyActor}.
 */
public class ConnectivityProxyActorTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("ditto", "myThing");
    private static ActorSystem actorSystem;
    private static ActorMaterializer actorMaterializer;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        actorMaterializer = ActorMaterializer.create(actorSystem);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testRetrieveThings() {
        new TestKit(actorSystem) {{
            final Props props = ConnectivityProxyActor.props(getRef());
            final ActorRef proxy = actorSystem.actorOf(props);

            final List<Thing> thingList = IntStream.range(0, 10)
                    .mapToObj(i -> ThingId.of(KNOWN_THING_ID.getNamespace(), KNOWN_THING_ID.getName() + "-" + i))
                    .map(id -> Thing.newBuilder().setId(id).build())
                    .collect(Collectors.toList());
            final ThingId[] thingIdsArray =
                    thingList.stream()
                            .map(Thing::getEntityId)
                            .map(Optional::get)
                            .collect(Collectors.toList())
                            .toArray(new ThingId[0]);

            final String correlationId = UUID.randomUUID().toString();
            final DittoHeaders headers =
                    DittoHeaders.newBuilder().correlationId(correlationId).contentType("application/json").build();
            final RetrieveThings retrieveThings =
                    RetrieveThings.getBuilder(thingIdsArray).dittoHeaders(headers).build();

            // WHEN: RetrieveThings is sent to proxy actor
            proxy.tell(retrieveThings, getRef());

            // THEN: command is forwarded to concierge
            expectMsg(retrieveThings);

            // WHEN: concierge responds with SourceRef of things
            Source.from(retrieveThings.getThingEntityIds())
                    .map(thingId -> Thing.newBuilder().setId(thingId).build())
                    .map(thing -> RetrieveThingResponse.of(thing.getEntityId().orElseThrow(), thing,
                            DittoHeaders.empty())).runWith(StreamRefs.sourceRef(), actorMaterializer)
                    .thenAccept(sr -> getLastSender().tell(sr, ActorRef.noSender()));

            // THEN: original sender receives
            final RetrieveThingsResponse retrieveThingsResponse = expectMsgClass(RetrieveThingsResponse.class);

            assertThat(retrieveThingsResponse.getThings()).containsExactlyElementsOf(thingList);
        }};
    }

    @Test
    public void testForwardOtherSignal() {
        new TestKit(actorSystem) {{
            final Signal signal = Mockito.mock(Signal.class);
            Mockito.when(signal.getDittoHeaders()).thenReturn(DittoHeaders.empty());

            final Props props = ConnectivityProxyActor.props(getRef());
            final ActorRef proxy = actorSystem.actorOf(props);

            // WHEN: signal is sent to proxy actor
            proxy.tell(signal, getRef());

            // THEN: signal is forwarded to concierge
            expectMsg(signal);

            // WHEN: concierge responds
            final Signal response = Mockito.mock(Signal.class);
            Mockito.when(response.getDittoHeaders()).thenReturn(DittoHeaders.empty());
            getLastSender().tell(response, ActorRef.noSender());

            // THEN: original sender receives response
            expectMsg(response);
        }};
    }
}