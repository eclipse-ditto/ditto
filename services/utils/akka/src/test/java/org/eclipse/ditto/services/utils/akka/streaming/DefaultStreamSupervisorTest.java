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
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_STARTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationWithTimeout;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link DefaultStreamSupervisor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultStreamSupervisorTest {

    private static final Duration START_OFFSET = Duration.ofMinutes(2);

    /* make sure that known last sync is longer in the past than start-offset -> streaming will be triggered "now",
       allows fast testing */
    private static final Instant KNOWN_LAST_SYNC = Instant.now().minus(START_OFFSET).minusSeconds(1);

    private static final Duration INITIAL_START_OFFSET = Duration.ofDays(1);
    private static final Duration STREAM_INTERVAL = Duration.ofMillis(50);
    private static final int ELEMENTS_STREAMED_PER_BATCH = 1;

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
    private static final VerificationWithTimeout SHORT_MOCKITO_TIMEOUT = timeout(SHORT_TIMEOUT.toMillis());

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe forwardTo;
    private TestProbe provider;
    @Mock
    private StreamMetadataPersistence searchSyncPersistence;

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        forwardTo = TestProbe.apply(actorSystem);
        provider = TestProbe.apply(actorSystem);

        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd())
                .thenAnswer(unused -> Optional.of(KNOWN_LAST_SYNC));
        when(searchSyncPersistence.updateLastSuccessfulStreamEnd(any(Instant.class)))
                .thenReturn(Source.single(NotUsed.getInstance()));
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
            verify(searchSyncPersistence).retrieveLastSuccessfulStreamEnd();

            getForwarderActor(streamSupervisor).tell(STREAM_STARTED, ActorRef.noSender());
            sendMessageToForwarderAndExpectTerminated(this, streamSupervisor, STREAM_COMPLETED);

            // verify the db has been updated with the queryEnd of the completed stream
            verify(searchSyncPersistence, SHORT_MOCKITO_TIMEOUT).updateLastSuccessfulStreamEnd(eq(expectedQueryEnd));
        }};
    }

    /**
     * This test verifies the Stream Supervisor isn't shutdown if an error occurs on saving the end timestamp.
     */
    @Test
    public void errorWhenUpdatingLastSuccessfulStreamEnd() throws Exception {
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);
            watch(streamSupervisor);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd);

            // verify that last query end has been retrieved from persistence
            verify(searchSyncPersistence).retrieveLastSuccessfulStreamEnd();

            when(searchSyncPersistence.updateLastSuccessfulStreamEnd(any(Instant.class)))
                    .thenReturn(Source.failed(new IllegalStateException("mocked stream-metadata-persistence error")));

            getForwarderActor(streamSupervisor).tell(STREAM_STARTED, ActorRef.noSender());
            sendMessageToForwarderAndExpectTerminated(this, streamSupervisor, STREAM_COMPLETED);

            // verify the db has been updated with the queryEnd of the completed stream
            verify(searchSyncPersistence, SHORT_MOCKITO_TIMEOUT).updateLastSuccessfulStreamEnd(eq(expectedQueryEnd));
            // verify the actor is not terminated
            expectNotTerminated(this, streamSupervisor, Duration.ofSeconds(1));
        }};
    }

    @Test
    public void streamIsRetriggeredOnTimeout() throws Exception {
        new TestKit(actorSystem) {{
            final Duration smallMaxIdleTime = Duration.ofMillis(10);
            final ActorRef streamSupervisor = createStreamSupervisor(smallMaxIdleTime);
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd, smallMaxIdleTime);

            // signal timeout to the supervisor
            getForwarderActor(streamSupervisor).tell(STREAM_STARTED, ActorRef.noSender());
            expectForwarderTerminated(this, streamSupervisor, smallMaxIdleTime.plus(SHORT_TIMEOUT));

            // wait for the actor to re-start streaming
            expectStreamTriggerMsg(expectedQueryEnd, smallMaxIdleTime);

            // verify the db has NOT been updated with the queryEnd, cause we never got a success-message
            verify(searchSyncPersistence, never()).updateLastSuccessfulStreamEnd(eq(expectedQueryEnd));
        }};
    }

    @Test
    public void supervisorRestartsIfStreamItDoesNotStartOrStopStreamForTooLong() {
        actorSystem.log().info("Logging disabled for this test because many stack traces are expected.");
        actorSystem.log().info("Re-enable logging should the test fail.");
        actorSystem.eventStream().setLogLevel(Logging.levelFor("off").get().asInt());

        new TestKit(actorSystem) {{

            // GIVEN: A stream supervisor props with extremely short outdated warn offset and stream interval
            //        that sends messages on restart

            final Duration oneMs = Duration.ofMillis(1L);
            final Duration oneDay = Duration.ofDays(1L);
            final StreamConsumerSettings settings =
                    StreamConsumerSettings.of(START_OFFSET, oneMs, INITIAL_START_OFFSET, oneDay, oneDay,
                            ELEMENTS_STREAMED_PER_BATCH, oneMs);

            final String onRestartMessage = "creating DefaultStreamSupervisor";

            final Props propsWithCreationHook = Props.create(DefaultStreamSupervisor.class, () -> {

                // send message to testkit on creation
                getRef().tell(onRestartMessage, ActorRef.noSender());

                return new DefaultStreamSupervisor<>(forwardTo.ref(), provider.ref(), String.class, Source::single,
                        Function.identity(), searchSyncPersistence, materializer, settings);
            });

            // WHEN: The stream supervisor is created
            actorSystem.actorOf(propsWithCreationHook);

            // THEN: The stream supervisor keeps restarting.
            for (int i = 0; i < 10; ++i) {
                expectMsg(onRestartMessage);
            }
        }};
    }

    private void expectStreamTriggerMsg(final Instant expectedQueryEnd) {
        expectStreamTriggerMsg(expectedQueryEnd, getDefaultMaxIdleTime());
    }

    private void expectStreamTriggerMsg(final Instant expectedQueryEnd, final Duration maxIdleTime) {
        final SudoStreamModifiedEntities msg = provider.expectMsgClass(FiniteDuration.apply(SHORT_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS), SudoStreamModifiedEntities.class);
        final Duration streamingActorTimeout = getStreamConsumerSettings(maxIdleTime).getStreamingActorTimeout();
        final SudoStreamModifiedEntities expectedStreamTriggerMsg =
                SudoStreamModifiedEntities.of(KNOWN_LAST_SYNC, expectedQueryEnd, ELEMENTS_STREAMED_PER_BATCH,
                        streamingActorTimeout.toMillis(), DittoHeaders.empty());
        assertThat(msg).isEqualTo(expectedStreamTriggerMsg);
    }

    private ActorRef createStreamSupervisor() {
        return createStreamSupervisor(getDefaultMaxIdleTime());
    }

    private ActorRef createStreamSupervisor(final Duration maxIdleTime) {
        final StreamConsumerSettings streamConsumerSettings = getStreamConsumerSettings(maxIdleTime);
        return actorSystem.actorOf(DefaultStreamSupervisor.props(forwardTo.ref(), provider.ref(),
                String.class, Source::single,
                Function.identity(), searchSyncPersistence, materializer, streamConsumerSettings));
    }

    private static Duration getDefaultMaxIdleTime() {
        return Duration.ofSeconds(10);
    }

    private static StreamConsumerSettings getStreamConsumerSettings(final Duration maxIdleTime) {
        return StreamConsumerSettings.of(START_OFFSET, STREAM_INTERVAL, INITIAL_START_OFFSET, maxIdleTime,
                Duration.ofDays(1), ELEMENTS_STREAMED_PER_BATCH, Duration.ofDays(10));
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

        expectTerminated(testKit, forwarderActor, timeout);
    }

    private ActorRef getForwarderActor(final ActorRef superVisorActorRef) throws Exception {
        final String forwarderPath =
                superVisorActorRef.path() + "/" + DefaultStreamSupervisor.STREAM_FORWARDER_ACTOR_NAME;
        final ActorSelection forwarderActorSelection = actorSystem.actorSelection(forwarderPath);
        final Future<ActorRef> forwarderActorFuture =
                forwarderActorSelection.resolveOne(scala.concurrent.duration.Duration.create(5, TimeUnit.SECONDS));
        Await.result(forwarderActorFuture, scala.concurrent.duration.Duration.create(6, TimeUnit.SECONDS));
        return forwarderActorFuture.value().get().get();
    }

    private void expectNotTerminated(final TestKit testKit, final ActorRef actor, final Duration timeout) {
        try {
            expectTerminated(testKit, actor, timeout);
            Assert.fail("the actor should not be terminated");
        } catch (final AssertionError assertionError) {
            // everything fine since the actor was not terminated
        }
    }

    private void expectTerminated(final TestKit testKit, final ActorRef actor, final Duration timeout)
            throws AssertionError {
        testKit.watch(actor);
        testKit.expectTerminated(FiniteDuration.apply(timeout.toNanos(), TimeUnit.NANOSECONDS), actor);
    }
}
