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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_ACK_MSG;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_COMPLETED;
import static org.eclipse.ditto.services.utils.akka.streaming.StreamConstants.STREAM_STARTED;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link DefaultPersistenceStreamingActor}.
 */
public final class DefaultPersistenceStreamingActorTest {

    private static ActorSystem actorSystem;

    private static final String ID = "ns:knownId";
    private static final long REVISION = 32L;

    @BeforeClass
    public static void initActorSystem() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
    }

    @AfterClass
    public static void shutdownActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void retrieveEmptyStream() {
        new TestKit(actorSystem) {{
            final Source<PidWithSeqNr, NotUsed> mockedSource = Source.empty();
            final ActorRef underTest = createPersistenceQueriesActor(mockedSource);
            final Command<?> command = createStreamingRequest();

            sendCommand(this, underTest, command);
            expectMsg(STREAM_STARTED);
            reply(STREAM_ACK_MSG);

            expectMsg(STREAM_COMPLETED);
        }};
    }

    @Test
    public void retrieveNonEmptyStream() {
        new TestKit(actorSystem) {{
            final Source<PidWithSeqNr, NotUsed> mockedSource = Source.single(new PidWithSeqNr(ID, REVISION));
            final ActorRef underTest = createPersistenceQueriesActor(mockedSource);
            final Command<?> command = createStreamingRequest();

            sendCommand(this, underTest, command);
            expectMsg(STREAM_STARTED);
            reply(STREAM_ACK_MSG);

            final BatchedEntityIdWithRevisions<?> expectedMessage =
                    BatchedEntityIdWithRevisions.of(SimpleEntityIdWithRevision.class,
                            Collections.singletonList(new SimpleEntityIdWithRevision(ID, REVISION)));
            expectMsg(expectedMessage);
            reply(STREAM_ACK_MSG);

            expectMsg(STREAM_COMPLETED);
        }};
    }

    private Command<?> createStreamingRequest() {
        final Instant endTs = Instant.now().minusSeconds(5);
        final Instant startTs = endTs.minusSeconds(10);

        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        return SudoStreamModifiedEntities.of(startTs, endTs, 1, 10_000L, dittoHeaders);
    }

    private static ActorRef createPersistenceQueriesActor(final Source<PidWithSeqNr, NotUsed> mockedSource) {
        final MongoClientWrapper mockClient = mock(MongoClientWrapper.class);
        final MongoReadJournal mockJournal = mock(MongoReadJournal.class);
        when(mockJournal.getPidWithSeqNrsByInterval(any(), any())).thenReturn(mockedSource);
        final Props props = Props.create(DefaultPersistenceStreamingActor.class, () ->
                new DefaultPersistenceStreamingActor<>(SimpleEntityIdWithRevision.class,
                        100, DefaultPersistenceStreamingActorTest::mapEntity, mockJournal, mockClient));
        return actorSystem.actorOf(props, "persistenceQueriesActor-" + UUID.randomUUID());
    }

    private static void sendCommand(final TestKit testKit, final ActorRef actorRef, final Command<?> command) {
        actorRef.tell(command, testKit.getRef());
    }

    private static SimpleEntityIdWithRevision mapEntity(final PidWithSeqNr pidWithSeqNr) {
        return new SimpleEntityIdWithRevision(pidWithSeqNr.getPersistenceId(), pidWithSeqNr.getSequenceNr());
    }

    private static final class SimpleEntityIdWithRevision extends AbstractEntityIdWithRevision {

        private SimpleEntityIdWithRevision(final String id, final long revision) {
            super(id, revision);
        }
    }
}
