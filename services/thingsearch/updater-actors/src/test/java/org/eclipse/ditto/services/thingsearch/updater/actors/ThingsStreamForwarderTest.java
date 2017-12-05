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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.ditto.services.models.things.ThingTag;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public class ThingsStreamForwarderTest {

    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(5);

    private static final String KNOWN_THING_ID = "namespace:thingid";
    private static final long KNOWN_REVISION_1 = 4L;
    private static final long KNOWN_REVISION_2 = 5L;
    private static final ThingTag KNOWN_TAG_1 = ThingTag.of(KNOWN_THING_ID, KNOWN_REVISION_1);
    private static final ThingTag KNOWN_TAG_2 = ThingTag.of(KNOWN_THING_ID, KNOWN_REVISION_2);

    private ActorSystem actorSystem;
    private TestProbe thingsUpdater;
    private TestProbe successRecipient;


    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        thingsUpdater = TestProbe.apply(actorSystem);
        successRecipient = TestProbe.apply(actorSystem);
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
                final ActorRef thingsStreamForwarder = createThingsStreamForwarder();
                watch(thingsStreamForwarder);

                thingsStreamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());

                thingsUpdater.expectMsg(KNOWN_TAG_1);
                thingsUpdater.reply(new Status.Success(KNOWN_THING_ID));

                // now wait for timeout to apply
                Thread.sleep(MAX_IDLE_TIME.plusSeconds(1).toMillis());

                expectTerminated(thingsStreamForwarder);
                successRecipient.expectNoMessage(FiniteDuration.Zero());
            }
        };
    }

    @Test
    public void streamWithFailure() throws Exception {
        new TestKit(actorSystem) {
            {
                final ActorRef thingsStreamForwarder = createThingsStreamForwarder();
                watch(thingsStreamForwarder);

                thingsStreamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());

                thingsUpdater.expectMsg(KNOWN_TAG_1);
                thingsUpdater.reply(new Status.Success(KNOWN_THING_ID));

                thingsStreamForwarder.tell(KNOWN_TAG_2, ActorRef.noSender());

                thingsUpdater.expectMsg(KNOWN_TAG_2);
                thingsUpdater.reply(new Status.Failure(new IllegalStateException("something bad happened")));

                thingsStreamForwarder.tell(Done.getInstance(), ActorRef.noSender());

                expectTerminated(thingsStreamForwarder);
                successRecipient.expectNoMessage(FiniteDuration.Zero());
            }
        };
    }

    @Test
    public void successfulStream() throws Exception {
        new TestKit(actorSystem) {
            {

                final Instant before = Instant.now().minusSeconds(1);
                final ActorRef thingsStreamForwarder = createThingsStreamForwarder();
                watch(thingsStreamForwarder);

                thingsStreamForwarder.tell(KNOWN_TAG_1, ActorRef.noSender());

                thingsUpdater.expectMsg(KNOWN_TAG_1);
                thingsUpdater.reply(new Status.Success(KNOWN_THING_ID));

                thingsStreamForwarder.tell(KNOWN_TAG_2, ActorRef.noSender());

                thingsUpdater.expectMsg(KNOWN_TAG_2);
                thingsUpdater.reply(new Status.Success(KNOWN_THING_ID));

                thingsStreamForwarder.tell(Done.getInstance(), ActorRef.noSender());

                final Status.Success success = successRecipient.expectMsgClass(Status.Success.class);

                final Instant after = Instant.now().plusSeconds(1);
                assertThat(success.status())
                        .isInstanceOf(Instant.class);
                assertThat((Instant) success.status())
                        .isBefore(after)
                        .isAfter(before);
                expectTerminated(thingsStreamForwarder);
            }
        };

    }

    private ActorRef createThingsStreamForwarder() {
        return createThingsStreamForwarder(thingsUpdater.ref(), successRecipient.ref());
    }

    private ActorRef createThingsStreamForwarder(final ActorRef thingsUpdater, final ActorRef successRecipient) {
        return actorSystem.actorOf(ThingsStreamForwarder.props(thingsUpdater, successRecipient, MAX_IDLE_TIME));
    }
}