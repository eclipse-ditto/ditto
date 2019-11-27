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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.ReconnectActor}.
 */
public final class ReconnectActorTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void conversionBetweenCorrelationIdAndConnectionIdIsOneToOne() {
        final ConnectionId id1 = ConnectionId.of("random-connection-ID-jbxlkeimx");
        final ConnectionId id2 = ConnectionId.of("differentConnectionId");
        final Optional<ConnectionId> outputId1 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id1));
        final Optional<ConnectionId> outputId2 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id2));
        assertThat(outputId1).contains(id1);
        assertThat(outputId2).contains(id2);
        assertThat(outputId1).isNotEqualTo(outputId2);
    }

    @Test
    public void testRecoverConnections() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            final ConnectionId connectionId1 = ConnectionId.of("connection-1");
            final ConnectionId connectionId2 = ConnectionId.of("connection-2");
            final ConnectionId connectionId3 = ConnectionId.of("connection-3");
            final Props props = ReconnectActor.props(probe.ref(),
                    () -> Source.from(Arrays.asList(
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionId1,
                            "invalid:" + connectionId2,
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionId3)));

            actorSystem.actorOf(props);

            final RetrieveConnectionStatus msg1 = probe.expectMsgClass(RetrieveConnectionStatus.class);
            assertThat((CharSequence) msg1.getConnectionEntityId()).isEqualTo(connectionId1);
            final RetrieveConnectionStatus msg2 = probe.expectMsgClass(RetrieveConnectionStatus.class);
            assertThat((CharSequence) msg2.getConnectionEntityId()).isEqualTo(connectionId3);
        }};
    }

    @Test
    public void testRecoverConnectionsIsNotStartedTwice() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            final Props props = ReconnectActor.props(probe.ref(),
                    () -> Source.from(Arrays.asList(
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + "connection-1",
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + "connection-2",
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + "connection-3")));

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(ReconnectActor.ReconnectMessages.START_RECONNECT, getRef());

            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectNoMessage();
        }};
    }

}
