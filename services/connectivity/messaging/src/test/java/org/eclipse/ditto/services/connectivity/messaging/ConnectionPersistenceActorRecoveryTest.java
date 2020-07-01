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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.INSTANT;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionLifecycle;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionMongoSnapshotAdapter;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.Creator;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorRecoveryTest extends WithMockServers {

    private static final String PERSISTENCE_ID_PREFIX = "connection:";
    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;
    private static ActorRef proxyActor;
    private ConnectionId connectionId;

    private ConnectionCreated connectionCreated;
    private ConnectionDeleted connectionDeleted;

    private static final ConnectionMongoSnapshotAdapter SNAPSHOT_ADAPTER = new ConnectionMongoSnapshotAdapter();
    private static ConfigValue blockedHosts;

    @BeforeClass
    public static void setUp() {
        blockedHosts = Mockito.spy(ConfigValueFactory.fromAnyRef(""));
        final Config config =
                TestConstants.CONFIG.withValue("ditto.connectivity.connection.blocked-hostnames", blockedHosts);
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        proxyActor = actorSystem.actorOf(TestConstants.ProxyActorMock.props());
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Before
    public void init() {
        when(blockedHosts.unwrapped()).thenCallRealMethod();
        connectionId = TestConstants.createRandomConnectionId();
        final Connection connection = TestConstants.createConnection(connectionId);
        connectionCreated = ConnectionCreated.of(connection, INSTANT, DittoHeaders.empty());
        connectionDeleted = ConnectionDeleted.of(connectionId, INSTANT, DittoHeaders.empty());
    }

    /**
     * This tests the behavior for connections that were deleted without saving a snapshot i.e the last stored event
     * is a ConnectionDeleted event.
     */
    @Test
    public void testRecoveryOfDeletedConnectionsWithoutSnapshot() {
        new TestKit(actorSystem) {{

            final Queue<ConnectivityEvent> existingEvents
                    = new LinkedList<>(Arrays.asList(connectionCreated, connectionDeleted));
            final Props fakeProps = FakePersistenceActor.props(connectionId, getRef(), existingEvents);

            actorSystem.actorOf(fakeProps);
            expectMsgEquals("persisted");

            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, proxyActor);
            watch(underTest);

            // expect termination because it was deleted (last event was ConnectionDeleted)
            expectTerminated(underTest);

            // verify snapshot was saved with DELETED lifecycle
            final Connection deletedConnection = setLifecycleDeleted(connectionCreated.getConnection());
            final SnapshotOffer snapshot = getSnapshotOffer(deletedConnection, 2); // created + deleted = 2
            final Queue<Object> expected = new LinkedList<>(Arrays.asList(snapshot, RecoveryCompleted.getInstance()));
            actorSystem.actorOf(RecoverActor.props(connectionId, getRef(), expected));
            expectMsgEquals("recovered");
        }};
    }

    @Test
    public void testRecoveryOfConnectionWithBlockedHost() {

        // enable blocklist for this test
        when(blockedHosts.unwrapped()).thenReturn("127.0.0.1");

        new TestKit(actorSystem) {{

            final Queue<ConnectivityEvent> existingEvents = new LinkedList<>(List.of(connectionCreated));
            final Props fakeProps = FakePersistenceActor.props(connectionId, getRef(), existingEvents);

            actorSystem.actorOf(fakeProps);
            expectMsgEquals("persisted");

            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, proxyActor);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());

            final ConnectionConfigurationInvalidException exception =
                    expectMsgClass(ConnectionConfigurationInvalidException.class);
            assertThat(exception)
                    .hasMessageContaining("The configured host '127.0.0.1' may not be used for the connection");
        }};
    }

    private Connection setLifecycleDeleted(final Connection connection) {
        return connection
                .toBuilder()
                .lifecycle(ConnectionLifecycle.DELETED)
                .build();
    }

    private SnapshotOffer getSnapshotOffer(final Connection deletedConnection, final int sequenceNr) {
        final SnapshotMetadata metadata = new SnapshotMetadata(PERSISTENCE_ID_PREFIX + connectionId, sequenceNr, 0);
        return new SnapshotOffer(metadata, deletedConnection);
    }

    /**
     * This actor verifies recovery of a persistence actor by checking if the events received during recovery matches
     * the expected events.
     */
    static class RecoverActor extends AbstractPersistentActor {

        private final ConnectionId connectionId;
        private final ActorRef ref;
        private final Queue<Object> expected;

        private RecoverActor(final ConnectionId connectionId, final ActorRef ref,
                final Queue<Object> expected) {
            this.connectionId = connectionId;
            this.ref = ref;
            this.expected = expected;
        }

        static Props props(final ConnectionId connectionId, final ActorRef probe,
                final Queue<Object> expected) {
            return Props.create(RecoverActor.class, new Creator<>() {
                private static final long serialVersionUID = 1L;

                @Override
                public RecoverActor create() {
                    return new RecoverActor(connectionId, probe, expected);
                }
            });
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder()
                    .match(SnapshotOffer.class, this::checkSnapshotOffer)
                    .matchAny(this::check)
                    .build();
        }

        private void check(final Object recovered) {
            final Object next = expected.poll();
            if (recovered.equals(next)) {
                if (expected.isEmpty()) {
                    ref.tell("recovered", getSelf());
                }
            } else {
                fail("expected: " + next + " but got: " + recovered);
            }
        }

        private void checkSnapshotOffer(final SnapshotOffer snapshotOffer) {
            final Object next = expected.poll();
            if (next instanceof SnapshotOffer) {
                final SnapshotOffer expected = (SnapshotOffer) next;
                if (expected.metadata().sequenceNr() != snapshotOffer.metadata().sequenceNr()) {
                    fail("expected sequence nr: " + expected.metadata().sequenceNr() + " but got: " +
                            snapshotOffer.metadata().sequenceNr());
                }
                if (!expected.snapshot().equals(SNAPSHOT_ADAPTER.fromSnapshotStore(snapshotOffer))) {
                    fail("expected: " + expected.snapshot() + " but got: " + snapshotOffer.snapshot());
                }
            } else {
                fail("expected snapshot offer");
            }
        }

        private void fail(final String reason) {
            ref.tell(reason, getSelf());
            getContext().stop(getSelf());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(m -> ref.forward(m, context())).build();
        }

        @Override
        public String persistenceId() {
            return PERSISTENCE_ID_PREFIX + connectionId;
        }

        @Override
        public String journalPluginId() {
            return JOURNAL_PLUGIN_ID;
        }

        @Override
        public String snapshotPluginId() {
            return SNAPSHOT_PLUGIN_ID;
        }
    }

    /**
     * This actor prepares the (in-memory) persistence with the given events.
     */
    static class FakePersistenceActor extends AbstractPersistentActor {

        private final ConnectionId connectionId;
        private final ActorRef probe;
        private final Queue<ConnectivityEvent> events;

        private FakePersistenceActor(final ConnectionId connectionId, final ActorRef probe,
                final Queue<ConnectivityEvent> events) {
            this.connectionId = connectionId;
            this.probe = probe;
            this.events = events;
        }

        static Props props(final ConnectionId connectionId, final ActorRef probe,
                final Queue<ConnectivityEvent> events) {
            return Props.create(FakePersistenceActor.class,
                    (Creator<FakePersistenceActor>) () -> new FakePersistenceActor(connectionId, probe, events));
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder().build();
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(ConnectivityEvent.class, e -> persist(e, persisted -> persistNextEvent()))
                    .build();
        }

        private void persistNextEvent() {
            final ConnectivityEvent next = events.poll();
            if (next != null) {
                getSelf().tell(next, getSelf());
            } else {
                probe.tell("persisted", getSelf());
                getContext().stop(getSelf());
            }
        }

        @Override
        public void preStart() {
            persistNextEvent();
        }

        @Override
        public String persistenceId() {
            return PERSISTENCE_ID_PREFIX + connectionId;
        }

        @Override
        public String journalPluginId() {
            return JOURNAL_PLUGIN_ID;
        }

        @Override
        public String snapshotPluginId() {
            return SNAPSHOT_PLUGIN_ID;
        }
    }
}
