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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamSupervisor;
import org.eclipse.ditto.services.utils.akka.streaming.StreamConstants;
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
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

@RunWith(MockitoJUnitRunner.class)
public class ThingsStreamSupervisorTest {

    private static final Duration START_OFFSET = Duration.ofMinutes(2);

    /* make sure that known last sync is longer in the past than start-offset -> streaming will be triggered "now",
       allows fast testing */
    private static final Instant KNOWN_LAST_SYNC = Instant.now().minus(START_OFFSET).minusSeconds(1);

    private static final Duration INITIAL_START_OFFSET = Duration.ofDays(1);
    private static final Duration STREAM_INTERVAL = Duration.ofSeconds(5);
    private static final int ELEMENTS_STREAMED_PER_SECOND = 5;

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    private static final VerificationWithTimeout SHORT_MOCKITO_TIMEOUT = timeout(SHORT_TIMEOUT.toMillis());

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe thingsUpdater;
    private TestProbe pubSubMediator;
    @Mock
    private StreamMetadataPersistence searchSyncPersistence;

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        thingsUpdater = TestProbe.apply(actorSystem);
        pubSubMediator = TestProbe.apply(actorSystem);

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
    public void successfulSync() throws Exception {
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd);

            // verify that last query end has been retrieved from persistence
            verify(searchSyncPersistence).retrieveLastSuccessfulStreamEnd(any(Instant.class));

            sendMessageToForwarderAndExpectTerminated(this, streamSupervisor, StreamConstants.STREAM_FINISHED_MSG);

            // verify the db has been updated with the queryEnd of the completed stream
            verify(searchSyncPersistence, SHORT_MOCKITO_TIMEOUT).updateLastSuccessfulStreamEnd(eq(expectedQueryEnd));
        }};
    }

    @Test
    public void streamIsRetriggeredOnTimeout() throws Exception {
        new TestKit(actorSystem) {{
            final Duration smallMaxIdleTime = Duration.ofSeconds(1);
            final ActorRef streamSupervisor = createStreamSupervisor(smallMaxIdleTime);
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd);

            // signal timeout to the supervisor
            expectForwarderTerminated(this, streamSupervisor, smallMaxIdleTime.plus(SHORT_TIMEOUT));

            // wait for the actor to re-start streaming
            expectStreamTriggerMsg(expectedQueryEnd);

            // verify the db has NOT been updated with the queryEnd, cause we never got a success-message
            verify(searchSyncPersistence, never()).updateLastSuccessfulStreamEnd(eq(expectedQueryEnd));
        }};
    }

    private void expectStreamTriggerMsg(final Instant expectedQueryEnd) {
        final SudoStreamModifiedEntities msg = expectPubSubMessage(SudoStreamModifiedEntities.class);
        final SudoStreamModifiedEntities expectedStreamTriggerMsg =
                SudoStreamModifiedEntities.of(KNOWN_LAST_SYNC, expectedQueryEnd, ELEMENTS_STREAMED_PER_SECOND,
                        DittoHeaders.empty());
        assertThat(msg).isEqualTo(expectedStreamTriggerMsg);
    }

    private <T> T expectPubSubMessage(final Class<T> clazz) {
        final DistributedPubSubMediator.Send pubSubMsg =
                pubSubMediator.expectMsgClass(FiniteDuration.apply(15, TimeUnit.SECONDS), DistributedPubSubMediator
                        .Send.class);
        final Object wrappedMsg = pubSubMsg.msg();
        return clazz.cast(wrappedMsg);
    }

    private ActorRef createStreamSupervisor() {
        return createStreamSupervisor(Duration.ofSeconds(10));
    }

    private ActorRef createStreamSupervisor(final Duration maxIdleTime) {
        return actorSystem.actorOf(
                ThingsStreamSupervisor.props(thingsUpdater.ref(), searchSyncPersistence, materializer,
                        START_OFFSET,
                        STREAM_INTERVAL, INITIAL_START_OFFSET, Duration.ofDays(10),
                        maxIdleTime, ELEMENTS_STREAMED_PER_SECOND, pubSubMediator.ref()));
    }

    private void sendMessageToForwarderAndExpectTerminated(final TestKit testKit, final ActorRef superVisorActorRef,
            final Object terminationCausingMsg) throws Exception {
        final ActorRef forwarderActor = getForwarderActor(superVisorActorRef);

        testKit.watch(forwarderActor);
        forwarderActor.tell(terminationCausingMsg, testKit.getRef());
        testKit.expectTerminated(forwarderActor);
    }

    private void expectForwarderTerminated(final TestKit testKit, final ActorRef superVisorActorRef,
            final Duration timeout) throws Exception {
        final ActorRef forwarderActor = getForwarderActor(superVisorActorRef);

        testKit.watch(forwarderActor);
        testKit.expectTerminated(FiniteDuration.apply(timeout.toNanos(), TimeUnit.NANOSECONDS), forwarderActor);
    }

    private ActorRef getForwarderActor(final ActorRef superVisorActorRef) throws Exception {
        final String forwarderPath =
                superVisorActorRef.path() + "/" + AbstractStreamSupervisor.STREAM_FORWARDER_ACTOR_NAME;
        final ActorSelection forwarderActorSelection = actorSystem.actorSelection(forwarderPath);
        final Future<ActorRef> forwarderActorFuture =
                forwarderActorSelection.resolveOne(scala.concurrent.duration.Duration.create(5, TimeUnit.SECONDS));
        Await.result(forwarderActorFuture, scala.concurrent.duration.Duration.create(6, TimeUnit.SECONDS));
        return forwarderActorFuture.value().get().get();
    }

}