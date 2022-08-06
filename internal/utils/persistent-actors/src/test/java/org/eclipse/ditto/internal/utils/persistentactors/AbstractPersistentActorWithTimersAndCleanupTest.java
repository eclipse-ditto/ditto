/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.internal.utils.persistentactors.MockSnapshotStorePlugin.FAIL_DELETE_SNAPSHOT;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;

import org.eclipse.ditto.base.api.persistence.cleanup.CleanupCommandResponse;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import akka.persistence.SaveSnapshotSuccess;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link AbstractPersistentActorWithTimersAndCleanup}.
 */
@SuppressWarnings("NullableProblems")
public class AbstractPersistentActorWithTimersAndCleanupTest {

    private static final int SNAPSHOT_THRESHOLD = 5;
    private static final String NAMESPACE = "com.example";

    @Rule
    public TestName name = new TestName();

    private ActorSystem actorSystem;

    @Before
    public void init() {
        resetMocks();
        actorSystem = ActorSystem.create(name.getMethodName(), ConfigFactory.load("test.conf"));
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
        actorSystem = null;
    }

    @Test
    public void testDeleteSucceeds() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10, dilated(Duration.ofSeconds(10)));

            // WHEN: cleanup is sent
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());

            // THEN: command is successful and plugin is called
            final CleanupCommandResponse<?> cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse.getHttpStatus()).isEqualTo(HttpStatus.OK);
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 9);
        }};
    }

    @Test
    public void testZeroStaleEventsKept() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId(), 0L));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10, dilated(Duration.ofSeconds(10)));

            // WHEN: cleanup is sent
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());

            // THEN: command is successful and plugin is called
            final CleanupCommandResponse<?> cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse.getHttpStatus()).isEqualTo(HttpStatus.OK);
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 9, 10);
        }};
    }

    @Test
    public void testDeleteMessagesFails() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(
                    MockJournalPlugin.FAIL_DELETE_MESSAGE));

            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 8, dilated(Duration.ofSeconds(10)));

            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(MockJournalPlugin.FAIL_DELETE_MESSAGE),
                            DittoHeaders.empty()),
                    getRef());
            final CleanupCommandResponse<?> cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);

            assertThat(cleanupCommandResponse.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            verifyPersistencePluginCalledWithCorrectArguments(MockJournalPlugin.FAIL_DELETE_MESSAGE, 4, 5);
        }};
    }

    @Test
    public void testDeleteSnapshotFails() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(FAIL_DELETE_SNAPSHOT));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 5, dilated(Duration.ofSeconds(10)));

            // WHEN: cleanup is sent
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(FAIL_DELETE_SNAPSHOT), DittoHeaders.empty()),
                    getRef());

            // THEN: expect success response with correct status and persistence plugin is called
            final CleanupCommandResponse<?> cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            verifyPersistencePluginCalledWithCorrectArguments(FAIL_DELETE_SNAPSHOT, 4);
        }};
    }

    @Test
    public void testDeleteNotCalledWhenRevisionDidNotChange() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 20, dilated(Duration.ofSeconds(10)));

            // WHEN: cleanup is sent
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());
            final CleanupCommandResponse<?> response1 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(response1.getHttpStatus()).isEqualTo(HttpStatus.OK);

            // and entity is not changed in the meantime
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());
            final CleanupCommandResponse<?> response2 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(response2.getHttpStatus()).isEqualTo(HttpStatus.OK);

            // THEN: verify that persistence plugin is only called once
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 19);
        }};
    }

    @Test
    public void testConcurrentCleanupCommandFailsWhenAnotherCleanupIsRunning() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(MockJournalPlugin.SLOW_DELETE));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10, dilated(Duration.ofSeconds(15)));

            // WHEN: concurrent cleanup is sent
            persistenceActor.tell(CleanupPersistence.of(extractEntityIdFromPersistenceId(MockJournalPlugin.SLOW_DELETE),
                    DittoHeaders.empty()),
                    getRef());
            persistenceActor.tell(CleanupPersistence.of(extractEntityIdFromPersistenceId(MockJournalPlugin.SLOW_DELETE),
                    DittoHeaders.empty()),
                    getRef());
            final CleanupCommandResponse<?> cleanupFailed =
                    expectMsgClass(dilated(Duration.ofSeconds(15)), CleanupCommandResponse.class);

            // THEN: second command is rejected and only first command ist executed by plugin
            assertThat(cleanupFailed.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            verifyPersistencePluginCalledWithCorrectArguments(MockJournalPlugin.SLOW_DELETE, 9);
        }};
    }

    @Test
    public void testDeleteIsCalledWhenRevisionChanged() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10, dilated(Duration.ofSeconds(10)));

            // WHEN: cleanup command is sent
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());

            // THEN: command is successful
            final CleanupCommandResponse<?> cleanupCommandResponse1 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse1.getHttpStatus()).isEqualTo(HttpStatus.OK);

            // WHEN: more updates occur and another cleanup command is sent
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10,
                    dilated(Duration.ofSeconds(10)));
            persistenceActor.tell(
                    CleanupPersistence.of(extractEntityIdFromPersistenceId(persistenceId()), DittoHeaders.empty()),
                    getRef());

            // THEN: command is successful and deletes are executed with correct seq number
            final CleanupCommandResponse<?> cleanupCommandResponse2 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse2.getHttpStatus()).isEqualTo(HttpStatus.OK);

            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 9);
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 19);
        }};
    }

    private static void modifyDummyAndWaitForSnapshotSuccess(final TestKit testKit,
            final ActorRef persistenceActor,
            final int times,
            final Duration waitTimeout) {

        IntStream.range(0, times).forEach(i -> persistenceActor.tell("SAVE", ActorRef.noSender()));
        IntStream.range(0, times / SNAPSHOT_THRESHOLD).forEach(i -> testKit.expectMsgClass(waitTimeout,
                SaveSnapshotSuccess.class));
    }

    private static void verifyPersistencePluginCalledWithCorrectArguments(final String persistenceId, final int toSn) {
        verifyPersistencePluginCalledWithCorrectArguments(persistenceId, toSn, toSn);
    }

    private static void verifyPersistencePluginCalledWithCorrectArguments(final String persistenceId,
            final int snapsSn,
            final int journalSn) {

        MockSnapshotStorePlugin.verify(persistenceId, snapsSn);
        MockJournalPlugin.verify(persistenceId, journalSn);
    }

    private static void resetMocks() {
        MockSnapshotStorePlugin.reset();
        MockJournalPlugin.reset();
    }

    private String persistenceId() {
        return EntityType.of("thing") + ":" + NAMESPACE + ":" + name.getMethodName();
    }

    private static EntityId extractEntityIdFromPersistenceId(final String persistenceId) {
        final int indexOfSeparator = persistenceId.indexOf(':');
        if (indexOfSeparator < 0) {
            final String message =
                    String.format("Persistence ID <%s> wasn't prefixed with an entity type.", persistenceId);
            throw new IllegalArgumentException(message);
        }
        final String id = persistenceId.substring(indexOfSeparator + 1);
        final EntityType type = EntityType.of(persistenceId.substring(0, indexOfSeparator));
        return EntityId.of(type, id);
    }

    static final class DummyPersistentActor extends AbstractPersistentActorWithTimersAndCleanup {

        private final String persistenceId;
        private final long staleEventsKept;
        private long lastSnapshotSeqNo = 0;

        private DummyPersistentActor(final String persistenceId, final long staleEventsKept) {
            this.persistenceId = persistenceId;
            this.staleEventsKept = staleEventsKept;
        }

        static Props props(final String persistenceId) {
            return props(persistenceId, 1L);
        }

        static Props props(final String persistenceId, final long staleEventsKept) {
            return Props.create(DummyPersistentActor.class,
                    (Creator<DummyPersistentActor>) () -> new DummyPersistentActor(persistenceId, staleEventsKept));
        }

        @Override
        protected long staleEventsKeptAfterCleanup() {
            return staleEventsKept;
        }

        @Override
        protected long getLatestSnapshotSequenceNumber() {
            return lastSnapshotSeqNo;
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder()
                    .matchAny(m -> log.debug("Received: {}", m))
                    .build();
        }

        @Override
        public Receive createReceive() {
            return super.createReceive()
                    .orElse(receiveBuilder()
                            .match(SaveSnapshotSuccess.class, sss -> {
                                log.debug("Snapshot success: {}", sss);
                                lastSnapshotSeqNo = sss.metadata().sequenceNr();
                                log.debug("Last snapshot sequence number is now {}", lastSnapshotSeqNo);
                                // forward to testkit, it waits for it before sending the cleanup command
                                getContext().getParent().tell(sss, ActorRef.noSender());
                            })
                            // simulates update on the entity
                            .matchEquals("SAVE", s -> persist(UUID.randomUUID().toString(), r -> {
                                log.debug("Persisted {}", r);
                                final long lastSequenceNo = lastSequenceNr();
                                log.debug("Sequence number is now {}", lastSequenceNo);
                                if (lastSequenceNo % SNAPSHOT_THRESHOLD == 0) {
                                    saveSnapshot(r);
                                }
                            }))
                            .build());
        }

        @Override
        public String persistenceId() {
            return persistenceId;
        }

    }

}
