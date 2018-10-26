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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
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
        final String id1 = "random-connection-ID-jbxlkeimx";
        final String id2 = "differentConnectionId";
        final Optional<String> outputId1 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id1));
        final Optional<String> outputId2 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id2));
        assertThat(outputId1).contains(id1);
        assertThat(outputId2).contains(id2);
        assertThat(outputId1).isNotEqualTo(outputId2);
    }

    @Test
    public void testRecoverConnections() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            final Props props = ReconnectActor.props(probe.ref(),
                    () -> Source.from(Arrays.asList(
                            ConnectionActor.PERSISTENCE_ID_PREFIX + "connection-1",
                            "invalid:connection-2",
                            ConnectionActor.PERSISTENCE_ID_PREFIX + "connection-3")));

            actorSystem.actorOf(props);

            final RetrieveConnectionStatus msg1 = probe.expectMsgClass(RetrieveConnectionStatus.class);
            assertThat(msg1.getConnectionId()).isEqualTo("connection-1");
            final RetrieveConnectionStatus msg2 = probe.expectMsgClass(RetrieveConnectionStatus.class);
            assertThat(msg2.getConnectionId()).isEqualTo("connection-3");
        }};
    }

    @Test
    public void testRecoverConnectionsIsNotStartedTwice() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            final Props props = ReconnectActor.props(probe.ref(),
                    () -> Source.from(Arrays.asList(
                            ConnectionActor.PERSISTENCE_ID_PREFIX + "connection-1",
                            ConnectionActor.PERSISTENCE_ID_PREFIX + "connection-2",
                            ConnectionActor.PERSISTENCE_ID_PREFIX + "connection-3")));

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(ReconnectActor.ReconnectConnections.INSTANCE, getRef());

            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectMsgClass(RetrieveConnectionStatus.class);
            probe.expectNoMsg();
        }};
    }
}