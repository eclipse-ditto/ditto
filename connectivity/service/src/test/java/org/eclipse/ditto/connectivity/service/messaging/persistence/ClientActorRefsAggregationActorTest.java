/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorRefs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.Broadcast;
import akka.testkit.TestKit;
import akka.testkit.TestProbe;
import scala.concurrent.duration.FiniteDuration;

public final class ClientActorRefsAggregationActorTest {

    private ActorSystem actorSystem;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(ClientActorRefsAggregationActorTest.class.getSimpleName());
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
        actorSystem = null;
    }

    @Test
    public void aggregationWorksAsExpected() {
        new TestKit(actorSystem) {{
            final TestProbe receiverProbe = TestProbe.apply(actorSystem);
            final TestProbe client1 = TestProbe.apply(actorSystem);
            final TestProbe client2 = TestProbe.apply(actorSystem);
            final TestProbe client3 = TestProbe.apply(actorSystem);
            final TestProbe clientActorRouterProbe = TestProbe.apply(actorSystem);
            final Duration aggregationInterval = Duration.ofSeconds(2);
            final Props props =
                    ClientActorRefsAggregationActor.props(3, receiverProbe.ref(), clientActorRouterProbe.ref(),
                            aggregationInterval, Duration.ofSeconds(3));
            final ActorRef underTest = actorSystem.actorOf(props);

            // Verify that aggregation actor broadcasts a PING signal via client actor router
            final Broadcast broadcast = clientActorRouterProbe.expectMsgClass(
                    FiniteDuration.apply(aggregationInterval.toSeconds() + 5, TimeUnit.SECONDS),
                    Broadcast.class);

            assertThat(broadcast.message()).isEqualTo(BaseClientActor.HealthSignal.PING);
            final ActorRef senderOfBroadCast = clientActorRouterProbe.lastSender();
            assertThat(senderOfBroadCast).isEqualTo(underTest);

            // When all client actors respond with a PONG signal
            senderOfBroadCast.tell(BaseClientActor.HealthSignal.PONG, client1.ref());
            senderOfBroadCast.tell(BaseClientActor.HealthSignal.PONG, client2.ref());
            senderOfBroadCast.tell(BaseClientActor.HealthSignal.PONG, client3.ref());

            // Then receiver is provided with the aggregated client actor refs
            final ClientActorRefs clientActorRefs = receiverProbe.expectMsgClass(ClientActorRefs.class);

            assertThat(clientActorRefs.getSortedRefs()).containsOnly(client1.ref(), client2.ref(), client3.ref());

            // Verify that process is repeated
            clientActorRouterProbe.expectMsgClass(
                    FiniteDuration.apply(aggregationInterval.toSeconds() + 5, TimeUnit.SECONDS),
                    Broadcast.class);
        }};
    }

    @Test
    public void aggregationRetriedAfterNormalIntervalInCaseOfTimeoutDuringAggregation() {
        new TestKit(actorSystem) {{
            final TestProbe receiverProbe = TestProbe.apply(actorSystem);
            final TestProbe client1 = TestProbe.apply(actorSystem);
            final TestProbe client2 = TestProbe.apply(actorSystem);
            final TestProbe client3 = TestProbe.apply(actorSystem);
            final TestProbe clientActorRouterProbe = TestProbe.apply(actorSystem);
            final Duration aggregationInterval = Duration.ofSeconds(2);
            final Props props =
                    ClientActorRefsAggregationActor.props(3, receiverProbe.ref(), clientActorRouterProbe.ref(),
                            aggregationInterval, Duration.ofSeconds(3));
            final ActorRef underTest = actorSystem.actorOf(props);

            final Broadcast broadcast1 = clientActorRouterProbe.expectMsgClass(
                    FiniteDuration.apply(aggregationInterval.toSeconds() + 5, TimeUnit.SECONDS),
                    Broadcast.class);

            assertThat(broadcast1.message()).isEqualTo(BaseClientActor.HealthSignal.PING);
            final ActorRef senderOfBroadCast1 = clientActorRouterProbe.lastSender();
            assertThat(senderOfBroadCast1).isEqualTo(underTest);

            senderOfBroadCast1.tell(BaseClientActor.HealthSignal.PONG, client1.ref());
            senderOfBroadCast1.tell(BaseClientActor.HealthSignal.PONG, client2.ref());

            // Receiver is not getting any message because aggregation timed out
            receiverProbe.expectNoMessage();

            final Broadcast broadcast2 = clientActorRouterProbe.expectMsgClass(
                    FiniteDuration.apply(aggregationInterval.toSeconds() + 5, TimeUnit.SECONDS),
                    Broadcast.class);

            assertThat(broadcast2.message()).isEqualTo(BaseClientActor.HealthSignal.PING);
            final ActorRef senderOfBroadCast2 = clientActorRouterProbe.lastSender();
            assertThat(senderOfBroadCast2).isEqualTo(underTest);

            senderOfBroadCast2.tell(BaseClientActor.HealthSignal.PONG, client1.ref());
            senderOfBroadCast2.tell(BaseClientActor.HealthSignal.PONG, client2.ref());
            senderOfBroadCast2.tell(BaseClientActor.HealthSignal.PONG, client3.ref());

            final ClientActorRefs clientActorRefs = receiverProbe.expectMsgClass(ClientActorRefs.class);

            assertThat(clientActorRefs.getSortedRefs()).containsOnly(client1.ref(), client2.ref(), client3.ref());
        }};
    }

}
