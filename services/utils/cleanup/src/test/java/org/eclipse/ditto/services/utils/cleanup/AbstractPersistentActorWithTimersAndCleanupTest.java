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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.cleanup.Cleanup;
import org.eclipse.ditto.signals.commands.cleanup.CleanupCommandResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotSelectionCriteria;
import akka.persistence.journal.japi.AsyncWriteJournal;
import akka.persistence.snapshot.japi.SnapshotStore;
import akka.testkit.javadsl.TestKit;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

@SuppressWarnings("NullableProblems")
public class AbstractPersistentActorWithTimersAndCleanupTest {

    private static final String FAIL_DELETE_SNAPSHOT = "failDeleteSnapshot";
    private static final String FAIL_DELETE_MESSAGE = "failDeleteMessage";
    private static final String SLOW_DELETE = "slowDelete";
    private static final int SNAPSHOT_THRESHOLD = 5;

    private static ActorSystem actorSystem;
    private AsyncWriteJournal journalMock;
    private SnapshotStore snapshotStoreMock;

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

    @Before
    public void setUp() {
        journalMock = Mockito.mock(AsyncWriteJournal.class);
        snapshotStoreMock = Mockito.mock(SnapshotStore.class);

        MockJournalPlugin.setMock(journalMock);
        MockSnapshotStorePlugin.setMock(snapshotStoreMock);

        // success case - all mocks complete with success
        configureForSuccess(name.getMethodName());

        // snapshot delete throws exception
        final CompletableFuture<Void> failDeleteSnapshot = new CompletableFuture<>();
        failDeleteSnapshot.completeExceptionally(new IllegalStateException(FAIL_DELETE_SNAPSHOT));
        when(journalMock.doAsyncDeleteMessagesTo(same(FAIL_DELETE_SNAPSHOT), anyLong())).thenReturn(Future.successful(null));
        when(snapshotStoreMock.doDeleteAsync(argThat(matchesId(FAIL_DELETE_SNAPSHOT)))).thenReturn(
                FutureConverters.toScala(failDeleteSnapshot));
        when(snapshotStoreMock.doDeleteAsync(same(FAIL_DELETE_SNAPSHOT),
                any(SnapshotSelectionCriteria.class))).thenReturn(FutureConverters.toScala(failDeleteSnapshot));

        // message delete throws exception
        final CompletableFuture<Void> failDeleteMessage = new CompletableFuture<>();
        failDeleteMessage.completeExceptionally(new IllegalStateException(FAIL_DELETE_MESSAGE));
        when(journalMock.doAsyncDeleteMessagesTo(same(FAIL_DELETE_MESSAGE), anyLong())).thenReturn(
                FutureConverters.toScala(failDeleteMessage));
        when(snapshotStoreMock.doDeleteAsync(argThat(matchesId(FAIL_DELETE_MESSAGE)))).thenReturn(
                Future.successful(null));
        when(snapshotStoreMock.doDeleteAsync(same(FAIL_DELETE_MESSAGE),
                any(SnapshotSelectionCriteria.class))).thenReturn(Future.successful(null));

        // do not complete future to simulate long running delete
        final CompletableFuture<Void> futureConcurrent = new CompletableFuture<>();
        when(journalMock.doAsyncDeleteMessagesTo(same(SLOW_DELETE), anyLong()))
                .thenReturn(FutureConverters.toScala(futureConcurrent));
    }

    private void configureForSuccess(final String persistenceId) {
        when(journalMock.doAsyncDeleteMessagesTo(same(persistenceId), anyLong())).thenReturn(Future.successful(null));
        when(snapshotStoreMock.doDeleteAsync(argThat(matchesId(persistenceId)))).thenReturn(Future.successful(null));
        when(snapshotStoreMock.doDeleteAsync(same(persistenceId), any(SnapshotSelectionCriteria.class))).thenReturn(Future.successful(null));
    }


    @Test
    public void testDeleteSucceeds() throws InterruptedException {
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

            Thread.sleep(1000);
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
            final CleanupCommandResponse cleanupFailed = expectMsgClass(CleanupCommandResponse.class);

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
        IntStream.range(0, times / SNAPSHOT_THRESHOLD).forEach(i -> testKit.expectMsgClass(SaveSnapshotSuccess.class));
    }

    private ArgumentMatcher<SnapshotMetadata> matchesId(final String persistenceId) {
        return arg -> arg != null && persistenceId.equals(arg.persistenceId());
    }

    private void verifyPersistencePluginCalledWithCorrectArguments(final String persistenceId, final int toSequenceNr) {
        verify(journalMock).doAsyncDeleteMessagesTo(persistenceId, toSequenceNr);
        verify(snapshotStoreMock).doDeleteAsync(same(persistenceId), argThat(matchesCriteria(toSequenceNr)));
    }

    private ArgumentMatcher<SnapshotSelectionCriteria> matchesCriteria(final long maxSequenceNumber) {
        return arg -> arg != null && maxSequenceNumber == arg.maxSequenceNr();
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