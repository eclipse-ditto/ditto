/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_FINISHED_MSG;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link DefaultStreamForwarder}.
 */
public class DefaultStreamForwarderTest {

    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(5);

    private static final String KNOWN_TAG_1 = "element1";
    private static final String KNOWN_TAG_2 = "element2";

    private ActorSystem actorSystem;
    private TestProbe recipient;
    private TestProbe completionRecipient;

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        recipient = TestProbe.apply(actorSystem);
        completionRecipient = TestProbe.apply(actorSystem);
    }

    /** */
    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void streamWithTimeout() throws Exception {
        new TestKit(actorSystem) {
            {
                final ActorRef streamForwarder = createStreamForwarder();
                watch(streamForwarder);

                streamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());

                recipient.expectMsg(KNOWN_TAG_1);

                // now wait for timeout to apply
                final Duration moreThanIdleTime = MAX_IDLE_TIME.plusSeconds(1);
                expectTerminated(FiniteDuration.apply(moreThanIdleTime.toNanos(), TimeUnit.NANOSECONDS),
                        streamForwarder);

                completionRecipient.expectNoMessage(FiniteDuration.Zero());
            }
        };
    }

    /**
     * Verify that even if a failure is returned by the stream, the forwarder still returns success.
     */
    @Test
    public void streamWithFailure() throws Exception {
        new TestKit(actorSystem) {
            {
                final Instant before = Instant.now().minusSeconds(1);
                final ActorRef streamForwarder = createStreamForwarder();
                watch(streamForwarder);

                streamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());
                recipient.expectMsg(KNOWN_TAG_1);

                recipient.reply(failureResponse(KNOWN_TAG_1));

                streamForwarder.tell(STREAM_FINISHED_MSG, ActorRef.noSender());

                final Status.Success success = completionRecipient.expectMsgClass(Status.Success.class);

                final Instant after = Instant.now().plusSeconds(1);
                assertThat(success.status())
                        .isInstanceOf(Instant.class);
                assertThat((Instant) success.status())
                        .isBefore(after)
                        .isAfter(before);
                expectTerminated(streamForwarder);
            }
        };
    }

    @Test
    public void successfulStream() throws Exception {
        new TestKit(actorSystem) {
            {

                final Instant before = Instant.now().minusSeconds(1);
                final ActorRef streamForwarder = createStreamForwarder();
                watch(streamForwarder);

                streamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());

                recipient.expectMsg(KNOWN_TAG_1);
                recipient.reply(successResponse(KNOWN_TAG_1));

                streamForwarder.tell(KNOWN_TAG_2, ActorRef.noSender());

                recipient.expectMsg(KNOWN_TAG_2);
                recipient.reply(successResponse(KNOWN_TAG_2));

                streamForwarder.tell(STREAM_FINISHED_MSG, ActorRef.noSender());

                final Status.Success success = completionRecipient.expectMsgClass(Status.Success.class);

                final Instant after = Instant.now().plusSeconds(1);
                assertThat(success.status())
                        .isInstanceOf(Instant.class);
                assertThat((Instant) success.status())
                        .isBefore(after)
                        .isAfter(before);
                expectTerminated(streamForwarder);
            }
        };

    }

    private StreamAck successResponse(final String tag) {
        return StreamAck.success(tag);
    }

    private StreamAck failureResponse(final String tag) {
        return StreamAck.failure(tag);
    }

    private ActorRef createStreamForwarder() {
        // for simplicity, just use String as elementClass
        final Props props = DefaultStreamForwarder.props(recipient.ref(), completionRecipient.ref(), MAX_IDLE_TIME,
                String.class, String::toString);

        return actorSystem.actorOf(props);
    }

}