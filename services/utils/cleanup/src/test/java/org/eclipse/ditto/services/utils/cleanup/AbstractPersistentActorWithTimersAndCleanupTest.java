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
package org.eclipse.ditto.services.utils.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.utils.cleanup.MockJournalPlugin.FAIL_DELETE_MESSAGE;
import static org.eclipse.ditto.services.utils.cleanup.MockJournalPlugin.SLOW_DELETE;
import static org.eclipse.ditto.services.utils.cleanup.MockSnapshotStorePlugin.FAIL_DELETE_SNAPSHOT;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.cleanup.Cleanup;
import org.eclipse.ditto.signals.commands.cleanup.CleanupCommandResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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

@SuppressWarnings("NullableProblems")
public class AbstractPersistentActorWithTimersAndCleanupTest {

    private static final int SNAPSHOT_THRESHOLD = 5;
    private static ActorSystem actorSystem;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test.conf"));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testDeleteSucceeds() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10);

            // WHEN: cleanup is sent
            persistenceActor.tell(Cleanup.of(persistenceId(), DittoHeaders.empty()), getRef());

            // THEN: command is successful and plugin is called
            final CleanupCommandResponse cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse.getStatusCode()).isEqualTo(HttpStatusCode.OK);
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 9);
        }};
    }

    @Test
    public void testDeleteMessagesFails() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(FAIL_DELETE_MESSAGE));

            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 8);

            persistenceActor.tell(Cleanup.of(FAIL_DELETE_MESSAGE, DittoHeaders.empty()), getRef());
            final CleanupCommandResponse cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);

            assertThat(cleanupCommandResponse.getStatusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);

            verifyPersistencePluginCalledWithCorrectArguments(FAIL_DELETE_MESSAGE, 4);
        }};
    }

    @Test
    public void testDeleteSnapshotFails() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(FAIL_DELETE_SNAPSHOT));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 5);

            // WHEN: cleanup is sent
            persistenceActor.tell(Cleanup.of(FAIL_DELETE_SNAPSHOT, DittoHeaders.empty()), getRef());

            // THEN: expect success response with correct status and persistence plugin is called
            final CleanupCommandResponse cleanupCommandResponse = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse.getStatusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
            verifyPersistencePluginCalledWithCorrectArguments(FAIL_DELETE_SNAPSHOT, 4);
        }};
    }

    @Test
    public void testDeleteNotCalledWhenRevisionDidNotChange() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 20);

            // WHEN: cleanup is sent
            persistenceActor.tell(Cleanup.of(persistenceId(), DittoHeaders.empty()), getRef());
            final CleanupCommandResponse response1 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatusCode.OK);

            // and entity is not changed in the meantime
            persistenceActor.tell(Cleanup.of(persistenceId(), DittoHeaders.empty()), getRef());
            final CleanupCommandResponse response2 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatusCode.OK);

            // THEN: verify that persistence plugin is only called once
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 19);
        }};
    }

    @Test
    public void testConcurrentCleanupCommandFailsWhenAnotherCleanupIsRunning() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(SLOW_DELETE));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10);

            // WHEN: concurrent cleanup is sent
            persistenceActor.tell(Cleanup.of(SLOW_DELETE, DittoHeaders.empty()), getRef());
            persistenceActor.tell(Cleanup.of(SLOW_DELETE, DittoHeaders.empty()), getRef());
            final CleanupCommandResponse cleanupFailed = expectMsgClass(Duration.ofSeconds(10), CleanupCommandResponse.class);

            // THEN: second command is rejected and only first command ist executed by plugin
            assertThat(cleanupFailed.getStatusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
            verifyPersistencePluginCalledWithCorrectArguments(SLOW_DELETE, 9);
        }};
    }

    @Test
    public void testDeleteIsCalledWhenRevisionChanged() {
        new TestKit(actorSystem) {{
            // GIVEN: persistence actor with some messages and snapshots
            final ActorRef persistenceActor = childActorOf(DummyPersistentActor.props(persistenceId()));
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10);

            // WHEN: cleanup command is sent
            persistenceActor.tell(Cleanup.of(persistenceId(), DittoHeaders.empty()), getRef());

            // THEN: command is successful
            final CleanupCommandResponse cleanupCommandResponse1 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse1.getStatusCode()).isEqualTo(HttpStatusCode.OK);

            // WHEN: more updates occur and another cleanup command is sent
            modifyDummyAndWaitForSnapshotSuccess(this, persistenceActor, 10);
            persistenceActor.tell(Cleanup.of(persistenceId(), DittoHeaders.empty()), getRef());

            // THEN: command is successful and deletes are executed with correct seq number
            final CleanupCommandResponse cleanupCommandResponse2 = expectMsgClass(CleanupCommandResponse.class);
            assertThat(cleanupCommandResponse2.getStatusCode()).isEqualTo(HttpStatusCode.OK);

            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 9);
            verifyPersistencePluginCalledWithCorrectArguments(persistenceId(), 19);
        }};
    }

    private void modifyDummyAndWaitForSnapshotSuccess(final TestKit testKit,
            final ActorRef persistenceActor, final int times) {
        IntStream.range(0, times).forEach(i -> persistenceActor.tell("SAVE", ActorRef.noSender()));
        IntStream.range(0, times / SNAPSHOT_THRESHOLD).forEach(i -> testKit.expectMsgClass(Duration.ofSeconds(10),
                SaveSnapshotSuccess.class));
    }

    private void verifyPersistencePluginCalledWithCorrectArguments(final String persistenceId,
            final int toSequenceNumber) {
        MockJournalPlugin.verify(persistenceId, toSequenceNumber);
        MockSnapshotStorePlugin.verify(persistenceId, toSequenceNumber);
    }

    private String persistenceId() {
        return name.getMethodName();
    }

    static class DummyPersistentActor extends AbstractPersistentActorWithTimersAndCleanup {

        private final String persistenceId;
        private long lastSnapshotSeqNo = 0;

        private DummyPersistentActor(final String persistenceId) {
            this.persistenceId = persistenceId;
        }

        static Props props(final String persistenceId) {
            return Props.create(
                    DummyPersistentActor.class, (Creator<DummyPersistentActor>) () -> new DummyPersistentActor(persistenceId));
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