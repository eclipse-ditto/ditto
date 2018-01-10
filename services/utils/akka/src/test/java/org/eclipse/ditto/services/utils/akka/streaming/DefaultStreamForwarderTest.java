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

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_ACK_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_STARTED;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link DefaultStreamForwarder}.
 */
public class DefaultStreamForwarderTest {

    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(5);

    private static final EntityIdWithRevision KNOWN_TAG_1 = new AbstractEntityIdWithRevision("element1", 1L) {};

    private static final EntityIdWithRevision KNOWN_TAG_2 = new AbstractEntityIdWithRevision("element2", 2L) {};

    private static final BatchedEntityIdWithRevisions<?> KNOWN_BATCH_1 =
            BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Collections.singletonList(KNOWN_TAG_1));

    private static final BatchedEntityIdWithRevisions<?> KNOWN_BATCH_2 =
            BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Collections.singletonList(KNOWN_TAG_2));

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
    public void streamWithTimeout() {
        new TestKit(actorSystem) {
            {
                final ActorRef streamForwarder = createStreamForwarder(Duration.ofMillis(100));
                watch(streamForwarder);

                streamForwarder.tell(STREAM_STARTED, getRef());
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(KNOWN_BATCH_1, getRef());
                recipient.expectMsg(KNOWN_TAG_1);

                // now wait for timeout to apply
                final FiniteDuration moreThanIdleTime =
                        FiniteDuration.create(MAX_IDLE_TIME.plusSeconds(1).toMillis(), TimeUnit.MILLISECONDS);
                expectTerminated(moreThanIdleTime, streamForwarder);

                completionRecipient.expectMsg(FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG);
            }
        };
    }

    /**
     * Verify that even if a failure is returned by the stream, the forwarder still returns success.
     */
    @Test
    public void streamWithFailure() {
        new TestKit(actorSystem) {
            {
                final ActorRef streamForwarder = createStreamForwarder();
                watch(streamForwarder);

                streamForwarder.tell(STREAM_STARTED, getRef());
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(KNOWN_BATCH_1, getRef());
                recipient.expectMsg(KNOWN_TAG_1);
                recipient.reply(failureResponse(KNOWN_TAG_1.toString()));
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(STREAM_COMPLETED, getRef());
                completionRecipient.expectMsg(STREAM_COMPLETED);

                expectTerminated(streamForwarder);
            }
        };
    }

    @Test
    public void successfulStream() {
        new TestKit(actorSystem) {
            {
                final ActorRef streamForwarder = createStreamForwarder();
                watch(streamForwarder);

                streamForwarder.tell(STREAM_STARTED, getRef());
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(KNOWN_BATCH_1, getRef());
                recipient.expectMsg(KNOWN_TAG_1);
                recipient.reply(successResponse(KNOWN_TAG_1.toString()));
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(KNOWN_BATCH_2, getRef());
                recipient.expectMsg(KNOWN_TAG_2);
                recipient.reply(successResponse(KNOWN_TAG_2.toString()));
                expectMsg(STREAM_ACK_MSG);

                streamForwarder.tell(STREAM_COMPLETED, getRef());
                completionRecipient.expectMsg(STREAM_COMPLETED);

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

    private ActorRef createStreamForwarder(final Duration maxIdleTime) {
        // for simplicity, just use String as elementClass
        final Props props = DefaultStreamForwarder.props(recipient.ref(), completionRecipient.ref(), maxIdleTime,
                EntityIdWithRevision.class, Source::single);

        return actorSystem.actorOf(props);
    }

    private ActorRef createStreamForwarder() {
        return createStreamForwarder(MAX_IDLE_TIME);
    }

}
