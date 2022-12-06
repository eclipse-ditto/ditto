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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.config.DefaultFieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionMongoSnapshotAdapter;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

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
 * Unit test for {@link org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorRecoveryTest extends WithMockServers {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final String PERSISTENCE_ID_PREFIX = "connection:";
    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;
    private static ActorRef proxyActor;
    private ConnectionId connectionId;

    private ConnectionCreated connectionCreated;
    private ConnectionDeleted connectionDeleted;
    private static final Config config = ConfigFactory.load("connection-fields-encryption-test");
    private static final ConnectionMongoSnapshotAdapter SNAPSHOT_ADAPTER =
            new ConnectionMongoSnapshotAdapter(DefaultFieldsEncryptionConfig.of(config.getConfig("connection")));

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        proxyActor = actorSystem.actorOf(TestConstants.ProxyActorMock.props());
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Before
    public void init() {
        connectionId = TestConstants.createRandomConnectionId();
        final Connection connection = TestConstants.createConnection(connectionId);
        connectionCreated = ConnectionCreated.of(connection, 1L, TestConstants.INSTANT, DittoHeaders.empty(), null);
        connectionDeleted = ConnectionDeleted.of(connectionId, 2L, TestConstants.INSTANT, DittoHeaders.empty(), null);
    }

    /**
     * This tests the behavior for connections that were deleted without saving a snapshot i.e the last stored event
     * is a ConnectionDeleted event.
     */
    @Test
    public void testRecoveryOfDeletedConnectionsWithoutSnapshot() {
        new TestKit(actorSystem) {{
            final Queue<ConnectivityEvent<?>> existingEvents
                    = new LinkedList<>(Arrays.asList(connectionCreated, connectionDeleted));
            final Props fakeProps = FakePersistenceActor.props(connectionId, getRef(), existingEvents);

            actorSystem.actorOf(fakeProps);
            expectMsgEquals("persisted");

            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    pubSubMediator, proxyActor);
            underTest.tell(PingCommand.of(connectionId,
                    "123",
                    JsonValue.of("always-alive")), getRef());
            watch(underTest);

            // expect termination because it was deleted (last event was ConnectionDeleted)
            expectTerminated(underTest);

            // verify snapshot was saved with DELETED lifecycle
            final SnapshotOffer snapshot = getSnapshotOffer(null, 2); // created + deleted = 2
            final Queue<Object> expected = new LinkedList<>(Arrays.asList(snapshot, RecoveryCompleted.getInstance()));
            actorSystem.actorOf(RecoverActor.props(connectionId, getRef(), expected));
            expectMsgEquals("recovered");
        }};
    }

    @Test
    public void testRecoveryOfConnectionWithBlockedHost() {
        final ActorSystem akkaTestSystem =
                ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG.withValue("ditto.connectivity.connection" +
                        ".blocked-hostnames", ConfigValueFactory.fromAnyRef("127.0.0.1")));
        final ActorRef mediator = DistributedPubSub.get(akkaTestSystem).mediator();
        final ActorRef proxyActor = actorSystem.actorOf(TestConstants.ProxyActorMock.props());

        try {
            new TestKit(akkaTestSystem) {{
                final Queue<ConnectivityEvent<?>> existingEvents = new LinkedList<>(List.of(connectionCreated));
                final Props fakeProps = FakePersistenceActor.props(connectionId, getRef(), existingEvents);

                akkaTestSystem.actorOf(fakeProps);
                expectMsgEquals("persisted");

                final ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, akkaTestSystem,
                        mediator, proxyActor);

                underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());

                final ConnectionConfigurationInvalidException exception =
                        expectMsgClass(ConnectionConfigurationInvalidException.class);
                assertThat(exception)
                        .hasMessageContaining("The configured host '127.0.0.1' may not be used for the connection");
            }};
        } finally {
            TestKit.shutdownActorSystem(akkaTestSystem);
        }
    }

    private Connection setLifecycleDeleted(final Connection connection) {
        return connection
                .toBuilder()
                .lifecycle(ConnectionLifecycle.DELETED)
                .build();
    }

    private SnapshotOffer getSnapshotOffer(@Nullable final Object deletedConnection, final int sequenceNr) {
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
                if (!Objects.equals(expected.snapshot(), SNAPSHOT_ADAPTER.fromSnapshotStore(snapshotOffer))) {
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
        private final Queue<ConnectivityEvent<?>> events;

        private FakePersistenceActor(final ConnectionId connectionId, final ActorRef probe,
                final Queue<ConnectivityEvent<?>> events) {
            this.connectionId = connectionId;
            this.probe = probe;
            this.events = events;
        }

        static Props props(final ConnectionId connectionId, final ActorRef probe,
                final Queue<ConnectivityEvent<?>> events) {
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
            final ConnectivityEvent<?> next = events.poll();
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
