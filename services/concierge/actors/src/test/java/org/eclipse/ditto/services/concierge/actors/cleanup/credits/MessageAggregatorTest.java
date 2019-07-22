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
package org.eclipse.ditto.services.concierge.actors.cleanup.credits;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.concierge.actors.cleanup.credits.MessageAggregator}.
 */
public final class MessageAggregatorTest {

    private static final Duration ONE_DAY = Duration.ofDays(1L);

    private ActorSystem actorSystem;

    @Before
    public void start() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void stop() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void stopSelfIfExpectNoMessage() {
        new TestKit(actorSystem) {{
            final TestProbe initialReceiver = TestProbe.apply(actorSystem);

            final ActorRef underTest =
                    actorSystem.actorOf(MessageAggregator.props(initialReceiver.ref(), Integer.class, 0, ONE_DAY));

            watch(underTest);

            underTest.tell(true, getRef());
            initialReceiver.expectMsg(true);
            expectMsg(Collections.emptyList());
            expectTerminated(underTest);
        }};
    }

    @Test
    public void aggregateMessagesAndStopSelf() {
        new TestKit(actorSystem) {{
            final TestProbe initialReceiver = TestProbe.apply(actorSystem);

            final ActorRef underTest =
                    actorSystem.actorOf(MessageAggregator.props(initialReceiver.ref(), Integer.class, 3, ONE_DAY));

            watch(underTest);

            underTest.tell(true, getRef());
            underTest.tell(0, getRef());
            underTest.tell(1, getRef());
            underTest.tell(2, getRef());
            initialReceiver.expectMsg(true);
            expectMsg(Arrays.asList(0, 1, 2));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void ignoreIrrelevantMessages() {
        new TestKit(actorSystem) {{
            final TestProbe initialReceiver = TestProbe.apply(actorSystem);

            final ActorRef underTest =
                    actorSystem.actorOf(MessageAggregator.props(initialReceiver.ref(), Integer.class, 3, ONE_DAY));

            watch(underTest);

            underTest.tell(true, getRef());
            underTest.tell("hello", getRef());
            underTest.tell(0, getRef());
            underTest.tell(new Object(), getRef());
            underTest.tell(1, getRef());
            underTest.tell(false, getRef());
            underTest.tell(2, getRef());
            initialReceiver.expectMsg(true);
            expectMsg(Arrays.asList(0, 1, 2));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void reportPartialMessagesOnTimeout() {
        new TestKit(actorSystem) {{
            final TestProbe initialReceiver = TestProbe.apply(actorSystem);

            final ActorRef underTest =
                    actorSystem.actorOf(MessageAggregator.props(initialReceiver.ref(), Integer.class, 3, ONE_DAY));

            watch(underTest);

            underTest.tell(true, getRef());
            underTest.tell(0, getRef());
            underTest.tell(1, getRef());
            underTest.tell(MessageAggregator.TIMEOUT, getRef());
            initialReceiver.expectMsg(true);
            expectMsg(Arrays.asList(0, 1));
            expectTerminated(underTest);
        }};

    }
}
