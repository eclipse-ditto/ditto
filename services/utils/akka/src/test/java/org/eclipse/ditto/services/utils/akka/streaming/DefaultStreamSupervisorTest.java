/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
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
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link DefaultStreamSupervisor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultStreamSupervisorTest {

    private static final EntityIdWithRevision TAG_1 = new AbstractEntityIdWithRevision("element1", 1L) {};
    private static final EntityIdWithRevision TAG_2 = new AbstractEntityIdWithRevision("element2", 2L) {};
    private static final EntityIdWithRevision TAG_3 = new AbstractEntityIdWithRevision("element3", 3L) {};

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
    private TimestampPersistence searchSyncPersistence;

    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        forwardTo = TestProbe.apply(actorSystem);
        provider = TestProbe.apply(actorSystem);

        when(searchSyncPersistence.getTimestampAsync())
                .thenAnswer(unused -> Source.single(Optional.of(KNOWN_LAST_SYNC)));
        when(searchSyncPersistence.setTimestamp(any(Instant.class)))
                .thenReturn(Source.single(NotUsed.getInstance()));
    }

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
     * persist a successful sync timestamp if it receives a SourceRef that completes successfully.
     */
    @Test
    public void successfulSync() {
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd);

            // verify that last query end has been retrieved from persistence
            verify(searchSyncPersistence).getTimestampAsync();

            final BatchedEntityIdWithRevisions<?> batch1 =
                    BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Arrays.asList(TAG_1, TAG_2));
            final BatchedEntityIdWithRevisions<?> batch2 =
                    BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Collections.singletonList(TAG_3));
            final BatchedEntityIdWithRevisions<?> batch3 =
                    BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Collections.emptyList());
            final Source<Object, NotUsed> source = Source.from(Arrays.asList(batch1, batch2, batch3));
            Patterns.pipe(source.runWith(StreamRefs.sourceRef(), materializer), actorSystem.dispatcher())
                    .to(streamSupervisor);

            // verify elements arrived at destination
            forwardTo.expectMsg(TAG_1);
            forwardTo.reply(StreamAck.success(TAG_1.getId()));
            forwardTo.expectMsg(TAG_2);
            forwardTo.reply(StreamAck.success(TAG_2.getId()));
            forwardTo.expectMsg(TAG_3);
            forwardTo.reply(StreamAck.success(TAG_3.getId()));

            // verify the db has been updated with the queryEnd of the completed stream
            verify(searchSyncPersistence, SHORT_MOCKITO_TIMEOUT).setTimestamp(eq(expectedQueryEnd));
        }};
    }

    /**
     * This test verifies the Stream Supervisor isn't shutdown if an error occurs on saving the end timestamp.
     */
    @Test
    public void errorWhenUpdatingLastSuccessfulStreamEnd() {
        disableLogging();
        new TestKit(actorSystem) {{
            final ActorRef streamSupervisor = createStreamSupervisor();
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);
            watch(streamSupervisor);

            // wait for the actor to start streaming the first time by expecting the corresponding send-message
            expectStreamTriggerMsg(expectedQueryEnd);

            // verify that last query end has been retrieved from persistence
            verify(searchSyncPersistence).getTimestampAsync();

            when(searchSyncPersistence.setTimestamp(any(Instant.class)))
                    .thenReturn(Source.failed(new IllegalStateException("mocked stream-metadata-persistence error")));

            Patterns.pipe(Source.empty().runWith(StreamRefs.sourceRef(), materializer), actorSystem.dispatcher())
                    .to(streamSupervisor);

            // verify the db has been updated with the queryEnd of the completed stream
            verify(searchSyncPersistence, SHORT_MOCKITO_TIMEOUT).setTimestamp(eq(expectedQueryEnd));
            // verify the actor is not terminated
            expectNotTerminated(this, streamSupervisor, Duration.ofSeconds(1));
        }};
    }

    @Test
    public void streamIsRetriggeredOnTimeout() {
        disableLogging();
        new TestKit(actorSystem) {{
            final Duration smallMaxIdleTime = Duration.ofMillis(10);
            final ActorRef supervisor = createStreamSupervisor(smallMaxIdleTime);
            final Instant expectedQueryEnd = KNOWN_LAST_SYNC.plus(STREAM_INTERVAL);

            // stream is triggered repeatedly
            final BatchedEntityIdWithRevisions<?> msg =
                    BatchedEntityIdWithRevisions.of(EntityIdWithRevision.class, Collections.singletonList(TAG_1));

            expectStreamTriggerMsg(expectedQueryEnd, smallMaxIdleTime);
            Patterns.pipe(Source.single(msg).runWith(StreamRefs.sourceRef(), materializer), actorSystem.dispatcher())
                    .to(supervisor);

            expectStreamTriggerMsg(expectedQueryEnd, smallMaxIdleTime);
            Patterns.pipe(Source.single(msg).runWith(StreamRefs.sourceRef(), materializer), actorSystem.dispatcher())
                    .to(supervisor);

            expectStreamTriggerMsg(expectedQueryEnd, smallMaxIdleTime);

            // verify the db has NOT been updated with the queryEnd, cause we never got a success-message
            verify(searchSyncPersistence, never()).setTimestamp(eq(expectedQueryEnd));
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
                EntityIdWithRevision.class, Source::single,
                Function.identity(), searchSyncPersistence, materializer, streamConsumerSettings));
    }

    private static Duration getDefaultMaxIdleTime() {
        return Duration.ofSeconds(10);
    }

    private static StreamConsumerSettings getStreamConsumerSettings(final Duration maxIdleTime) {
        return StreamConsumerSettings.of(START_OFFSET, STREAM_INTERVAL, INITIAL_START_OFFSET, maxIdleTime,
                Duration.ofDays(1), ELEMENTS_STREAMED_PER_BATCH, Duration.ofDays(10));
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

    /**
     * Disable logging for 1 test to hide stacktrace or other logs on level ERROR. Comment out to debug the test.
     */
    private void disableLogging() {
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
    }
}
