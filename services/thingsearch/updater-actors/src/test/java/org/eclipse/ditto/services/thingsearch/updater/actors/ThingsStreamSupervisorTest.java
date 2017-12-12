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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationWithTimeout;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class ThingsStreamSupervisorTest {

    private static final Duration START_OFFSET = Duration.ofMinutes(2);

    /* make sure that known last sync is longer in the past than start-offset -> streaming will be triggered "now",
       allows fast testing */
   private static final Instant KNOWN_LAST_SYNC = Instant.now().minus(START_OFFSET).minusSeconds(1);

    private static final Duration INITIAL_START_OFFSET = Duration.ofDays(1);
    private static final Duration STREAM_INTERVAL = Duration.ofSeconds(5);

    private static final VerificationWithTimeout SHORT_TIMEOUT = timeout(1000L);

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe thingsUpdater;
    @Mock
    private StreamMetadataPersistence searchSyncPersistence;

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        thingsUpdater = TestProbe.apply(actorSystem);

        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd(any(Instant.class)))
                .thenAnswer(unused -> KNOWN_LAST_SYNC);
        when(searchSyncPersistence.updateLastSuccessfulStreamEnd(any(Instant.class)))
                .thenReturn(Source.empty());
    }

    /** */
    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    /**
     * This Test verifies the behavior of the first sync after the Actor has been started. The StreamSupervisor will
     * send itself a CheckForActivity message after STREAM_INTERVAL which triggers synchronization. Afterwards it will
     * persist a successful sync timestamp if it receives a Status.Success message.
     */
    @Test
    public void successfulSync() throws InterruptedException {
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();

            // wait for the actor to start streaming the first time
            verify(searchSyncPersistence, SHORT_TIMEOUT).retrieveLastSuccessfulStreamEnd(any(Instant.class));

            streamSupervisor.tell(new Status.Success(1), ActorRef.noSender());

            // verify the db is called with the last successful sync timestamp plus the modified offset
            final Instant expectedPersistedTimestamp =
                    KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);
            verify(searchSyncPersistence, SHORT_TIMEOUT).updateLastSuccessfulStreamEnd(
                    eq(expectedPersistedTimestamp));
        }};
    }

    private ActorRef createStreamSupervisor() {
        return actorSystem.actorOf(
                ThingsStreamSupervisor.props(thingsUpdater.ref(), searchSyncPersistence, materializer,
                        START_OFFSET,
                        STREAM_INTERVAL, INITIAL_START_OFFSET, Duration.ofDays(10),
                        Duration.ofSeconds(10), 5));
    }
}