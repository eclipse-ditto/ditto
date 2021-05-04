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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.internal.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.models.streaming.SudoStreamPids;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link DefaultPersistenceStreamingActor}.
 */
public final class DefaultPersistenceStreamingActorTest {

    private static ActorSystem actorSystem;

    private static final ThingId ID = ThingId.of("ns:knownId");

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
            final Source<String, NotUsed> mockedSource = Source.empty();
            final ActorRef underTest = createPersistenceQueriesActor(mockedSource);
            final Command<?> command = createStreamingRequest();

            sendCommand(this, underTest, command);

            final SourceRef<?> sourceRef = expectMsgClass(SourceRef.class);

            sourceRef.getSource()
                    .runWith(TestSink.probe(actorSystem), actorSystem)
                    .request(1000L)
                    .expectComplete();
        }};
    }

    @Test
    @SuppressWarnings("unchecked")
    public void retrieveNonEmptyStream() {
        new TestKit(actorSystem) {{
            final Source<String, NotUsed> mockedSource = Source.single(ID.toString());
            final ActorRef underTest = createPersistenceQueriesActor(mockedSource);
            final Command<?> command = createStreamingRequest();

            sendCommand(this, underTest, command);

            final SourceRef<Object> sourceRef = expectMsgClass(SourceRef.class);

            final Object expectedMessage =
                    BatchedEntityIdWithRevisions.of(SimpleEntityIdWithRevision.class,
                            Collections.singletonList(new SimpleEntityIdWithRevision(ID, 0L)));

            sourceRef.getSource()
                    .runWith(TestSink.probe(actorSystem), actorSystem)
                    .request(1000L)
                    .expectNext(expectedMessage)
                    .expectComplete();
        }};
    }

    private static Command<?> createStreamingRequest() {
        return SudoStreamPids.of(1, 10_000L, DittoHeaders.empty(), ThingConstants.ENTITY_TYPE);
    }

    private static ActorRef createPersistenceQueriesActor(final Source<String, NotUsed> mockedSource) {
        final MongoReadJournal mockJournal = mock(MongoReadJournal.class);
        when(mockJournal.getJournalPids(anyInt(), any(), any())).thenReturn(mockedSource);
        final Props props = DefaultPersistenceStreamingActor.propsForTests(SimpleEntityIdWithRevision.class,
                DefaultPersistenceStreamingActorTest::mapEntity,
                DefaultPersistenceStreamingActorTest::unmapEntity,
                mockJournal);
        return actorSystem.actorOf(props, "persistenceQueriesActor-" + UUID.randomUUID());
    }

    private static void sendCommand(final TestKit testKit, final ActorRef actorRef, final Command<?> command) {
        actorRef.tell(command, testKit.getRef());
    }

    private static SimpleEntityIdWithRevision mapEntity(final PidWithSeqNr pidWithSeqNr) {
        return new SimpleEntityIdWithRevision(ThingId.of(pidWithSeqNr.getPersistenceId()),
                pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr unmapEntity(final EntityIdWithRevision<?> entityIdWithRevision) {
        return new PidWithSeqNr(entityIdWithRevision.getEntityId().toString(), entityIdWithRevision.getRevision());
    }

    private static final class SimpleEntityIdWithRevision extends AbstractEntityIdWithRevision<EntityId> {

        private SimpleEntityIdWithRevision(final EntityId id, final long revision) {
            super(id, revision);
        }
    }

}
