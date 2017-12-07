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

import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchSyncPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
@Ignore("CR-4951")
public class ThingsStreamSupervisorTest {

    private static final Instant KNOWN_LAST_SUCCESSFUL_SYNC = Instant.now().minusSeconds(37);

    private static final Duration START_OFFSET = Duration.ofMinutes(2);
    private static final Duration INITIAL_START_OFFSET = Duration.ofDays(1);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(10);
    private static final int ELEMENTS_STREAMED_PER_SECOND = 5;

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe thingsUpdater;
    @Mock
    private ThingsSearchSyncPersistence searchSyncPersistence;

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        thingsUpdater = TestProbe.apply(actorSystem);

        when(searchSyncPersistence.retrieveLastSuccessfulSyncTimestamp(any(Instant.class)))
                .thenAnswer(unused -> Source.single(KNOWN_LAST_SUCCESSFUL_SYNC));
        when(searchSyncPersistence.updateLastSuccessfulSyncTimestamp(any(Instant.class)))
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
     * send itself a CheckForActivity message after POLL_INTERVAL which triggers synchronization. Afterwards it will
     * persist a successful sync timestamp if it receives a Status.Success message.
     */
    @Test
    public void successfulSync() throws InterruptedException {
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();
            // wait for the actor to start streaming the first time
            Thread.sleep(POLL_INTERVAL.plusSeconds(1).toMillis());

            verify(searchSyncPersistence).retrieveLastSuccessfulSyncTimestamp(any(Instant.class));

            streamSupervisor.tell(new Status.Success(1), ActorRef.noSender());

            // verify the db is called with the last successful sync timestamp plus the modified offset
            final Instant expectedPersistedTimestamp = KNOWN_LAST_SUCCESSFUL_SYNC.plus(POLL_INTERVAL).plus(START_OFFSET);
            verify(searchSyncPersistence, timeout(1000L)).updateLastSuccessfulSyncTimestamp(
                    eq(expectedPersistedTimestamp));
        }};
    }

    private ActorRef createStreamSupervisor() {
        return actorSystem.actorOf(
                ThingsStreamSupervisor.props(thingsUpdater.ref(), searchSyncPersistence, materializer,
                        START_OFFSET,
                        INITIAL_START_OFFSET,
                        POLL_INTERVAL,
                        MAX_IDLE_TIME, ELEMENTS_STREAMED_PER_SECOND));
    }
}