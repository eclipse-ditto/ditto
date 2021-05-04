/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.backoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.connectivity.service.config.BackOffConfig;
import org.eclipse.ditto.connectivity.service.config.TimeoutConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link BackOffActor}.
 */
public final class BackOffActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final Duration BACK_OFF_DURATION = Duration.ofSeconds(2L);
    private static final Duration DOUBLE_BACK_OFF_DURATION = BACK_OFF_DURATION.multipliedBy(2L);
    private static final Duration HALF_BACK_OFF_DURATION = BACK_OFF_DURATION.dividedBy(2L);

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void receiveMessageAfterBackOff() {
        new TestKit(actorSystem) {{

            final ActorRef underTest = childActorOf(BackOffActor.props(createBackOffConfig()));
            final String message = "I expect to receive this after backOff";
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(message), getRef());

            // verify there happens a back off
            expectNoMessage(HALF_BACK_OFF_DURATION);

            // verify we receive the answer afterwards
            expectMsg(BACK_OFF_DURATION, message);
        }};
    }

    @Test
    public void backOffDuringBackOffWillDelayMessage() {
        new TestKit(actorSystem) {{

            final ActorRef underTest = childActorOf(BackOffActor.props(createBackOffConfig()));
            final String initialMessage = "This should not be received as the back off is cancelled.";
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(initialMessage), getRef());

            // verify there happens a back off
            expectNoMessage(HALF_BACK_OFF_DURATION);
            final String expectedMessage = "I expect to receive this after the second backOff";
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(expectedMessage), getRef());

            // verify there happens another back off
            expectNoMessage(BACK_OFF_DURATION);

            // verify we receive the answer afterwards
            expectMsg(DOUBLE_BACK_OFF_DURATION, expectedMessage);
        }};
    }

    @Test
    public void backOffDelayWillBeResetAfterSomeTime() {

        new TestKit(actorSystem) {{

            final ActorRef underTest = childActorOf(BackOffActor.props(createBackOffConfig()));
            final String message = "I expect to receive this after backOff";
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(message), getRef());

            // verify there happens a back off
            expectNoMessage(HALF_BACK_OFF_DURATION);

            // verify we receive the answer afterwards
            expectMsg(BACK_OFF_DURATION, message);

            // wait for ~BACK_OFF_DURATION, the timer should reset itself. Using double so test should always work
            expectNoMessage(DOUBLE_BACK_OFF_DURATION);

            // test again with the same duration to verify the actor doesn't use the double duration
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(message), getRef());

            expectNoMessage(HALF_BACK_OFF_DURATION);
            expectMsg(BACK_OFF_DURATION, message);
        }};
    }

    @Test
    public void providesInformationIfCurrentlyInBackOff() {
        final TestKit probe = new TestKit(actorSystem);
        final ActorRef underTest = probe.childActorOf(BackOffActor.props(createBackOffConfig()));
        final String message = "I expect to receive this after backOff";


        assertIsInBackOffMode(underTest, probe, false);

        underTest.tell(BackOffActor.createBackOffWithAnswerMessage(message), probe.getRef());

        assertIsInBackOffMode(underTest, probe, true);

        // verify we receive the answer afterwards
        probe.expectNoMessage(HALF_BACK_OFF_DURATION);
        probe.expectMsg(BACK_OFF_DURATION, message);


        assertIsInBackOffMode(underTest, probe, false);
    }

    private static void assertIsInBackOffMode(final ActorRef underTest, final TestKit probe,
            final boolean isInBackOff) {
        underTest.tell(BackOffActor.createIsInBackOffMessage(), probe.getRef());
        final BackOffActor.IsInBackOffResponse response = probe.expectMsgClass(BackOffActor.IsInBackOffResponse.class);
        assertThat(response.isInBackOff()).isEqualTo(isInBackOff);
    }

    @Test
    public void maxBackOffTimeIsNotExceeded() {
        final Duration minBackOff = Duration.ofSeconds(1);
        final Duration maxBackOff = Duration.ofSeconds(2);
        final BackOffConfig configWithSmallMaxTimeout = createBackOffConfig(minBackOff, maxBackOff);

        final TestKit probe = new TestKit(actorSystem);
        final ActorRef underTest = probe.childActorOf(BackOffActor.props(configWithSmallMaxTimeout));
        final String message = "I expect to receive this after backOff";

        final int iterations = 10;
        Duration currentBackOff = minBackOff;
        for (int i = 0; i < iterations; ++i) {
            underTest.tell(BackOffActor.createBackOffWithAnswerMessage(message), probe.getRef());
            probe.expectNoMessage(currentBackOff.dividedBy(2L));
            probe.expectMsg(currentBackOff, message);
            currentBackOff = min(currentBackOff.multipliedBy(2L), maxBackOff);
        }
    }

    private static Duration min(final Duration d1, final Duration d2) {
        return d1.minus(d2).isNegative() ? d1 : d2;
    }

    private BackOffConfig createBackOffConfig() {
        return createBackOffConfig(BACK_OFF_DURATION, DOUBLE_BACK_OFF_DURATION);
    }

    private BackOffConfig createBackOffConfig(final Duration minTimeout, final Duration maxTimeout) {
        return () -> new TimeoutConfig() {
            @Override
            public Duration getMinTimeout() {
                return minTimeout;
            }

            @Override
            public Duration getMaxTimeout() {
                return maxTimeout;
            }
        };
    }

}
