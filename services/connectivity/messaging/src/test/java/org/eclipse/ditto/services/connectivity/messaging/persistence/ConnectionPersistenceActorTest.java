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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.MockClientActor.mockClientActorPropsFactory;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.INSTANT;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionIdInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.MockClientActor;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.WithMockServers;
import org.eclipse.ditto.services.models.connectivity.BaseClientState;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.LoggingExpired;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorTest extends WithMockServers {

    private ActorSystem actorSystem;
    private ActorRef pubSubMediator;
    private ActorRef proxyActor;
    private ConnectionId connectionId;
    private CreateConnection createConnection;
    private CreateConnection createClosedConnectionWith2Clients;
    private CreateConnection createClosedConnection;
    private ModifyConnection modifyConnection;
    private ModifyConnection modifyClosedConnection;
    private DeleteConnection deleteConnection;
    private TestConnection testConnection;
    private TestConnection testConnectionCausingFailure;
    private TestConnection testConnectionCausingException;
    private CreateConnectionResponse createConnectionResponse;
    private CreateConnectionResponse createClosedConnectionResponse;
    private ModifyConnectionResponse modifyConnectionResponse;
    private OpenConnection openConnection;
    private CloseConnection closeConnection;
    private CloseConnectionResponse closeConnectionResponse;
    private DeleteConnectionResponse deleteConnectionResponse;
    private RetrieveConnection retrieveConnection;
    private RetrieveConnectionStatus retrieveConnectionStatus;
    private ResetConnectionMetrics resetConnectionMetrics;
    private RetrieveConnectionResponse retrieveModifiedConnectionResponse;
    private RetrieveConnectionStatusResponse retrieveConnectionStatusOpenResponse;
    private ConnectionNotAccessibleException connectionNotAccessibleException;
    private EnableConnectionLogs enableConnectionLogs;
    private EnableConnectionLogsResponse enableConnectionLogsResponse;

    // second actor system to test multiple client actors
    private ActorSystem actorSystem2;
    private Connection closedConnection;

    @After
    public void tearDown() {
        shutdown(actorSystem);
        shutdown(actorSystem2);
    }

    @Before
    public void init() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        proxyActor = actorSystem.actorOf(TestConstants.ProxyActorMock.props());
        connectionId = TestConstants.createRandomConnectionId();
        final Connection connection = TestConstants.createConnection(connectionId);
        final Connection closedConnectionWith2Clients =
                connection.toBuilder().clientCount(2).connectionStatus(ConnectivityStatus.CLOSED).build();
        closedConnection = TestConstants.createConnection(connectionId, ConnectivityStatus.CLOSED,
                TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        createConnection = CreateConnection.of(connection, DittoHeaders.empty());
        createClosedConnectionWith2Clients = CreateConnection.of(closedConnectionWith2Clients, DittoHeaders.empty());
        createClosedConnection = CreateConnection.of(closedConnection, DittoHeaders.empty());
        final Connection modifiedConnection =
                connection.toBuilder()
                        .failoverEnabled(false)
                        .targets(Collections.singletonList(TestConstants.Targets.MESSAGE_TARGET))
                        .build();
        modifyConnection = ModifyConnection.of(modifiedConnection, DittoHeaders.empty());
        modifyClosedConnection = ModifyConnection.of(closedConnection, DittoHeaders.empty());
        deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
        createConnectionResponse = CreateConnectionResponse.of(connection, DittoHeaders.empty());
        createClosedConnectionResponse = CreateConnectionResponse.of(closedConnection, DittoHeaders.empty());
        modifyConnectionResponse = ModifyConnectionResponse.of(connectionId, DittoHeaders.empty());
        openConnection = OpenConnection.of(connectionId, DittoHeaders.empty());
        closeConnection = CloseConnection.of(connectionId, DittoHeaders.empty());
        testConnection = TestConnection.of(connection, DittoHeaders.empty());
        testConnectionCausingFailure =
                TestConnection.of(connection, DittoHeaders.newBuilder().putHeader("fail", "true").build());
        testConnectionCausingException =
                TestConnection.of(connection, DittoHeaders.newBuilder().putHeader("error", "true").build());
        closeConnectionResponse = CloseConnectionResponse.of(connectionId, DittoHeaders.empty());
        deleteConnectionResponse = DeleteConnectionResponse.of(connectionId, DittoHeaders.empty());
        retrieveConnection = RetrieveConnection.of(connectionId, DittoHeaders.empty());
        retrieveConnectionStatus = RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
        resetConnectionMetrics = ResetConnectionMetrics.of(connectionId, DittoHeaders.empty());
        retrieveModifiedConnectionResponse =
                RetrieveConnectionResponse.of(modifiedConnection.toJson(), DittoHeaders.empty());
        retrieveConnectionStatusOpenResponse =
                RetrieveConnectionStatusResponse.getBuilder(connectionId, DittoHeaders.empty())
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.OPEN)
                        .connectedSince(INSTANT)
                        .clientStatus(
                                List.of(ConnectivityModelFactory.newClientStatus("client1", ConnectivityStatus.OPEN,
                                        "connection is open", INSTANT)))
                        .sourceStatus(
                                List.of(ConnectivityModelFactory.newSourceStatus("client1", ConnectivityStatus.OPEN,
                                        "source1", "consumer started"),
                                        ConnectivityModelFactory.newSourceStatus("client1", ConnectivityStatus.OPEN,
                                                "source2", "consumer started")
                                ))
                        .targetStatus(
                                List.of(ConnectivityModelFactory.newTargetStatus("client1", ConnectivityStatus.OPEN,
                                        "target1", "publisher started")))
                        .build();
        connectionNotAccessibleException = ConnectionNotAccessibleException.newBuilder(connectionId).build();

        enableConnectionLogs = EnableConnectionLogs.of(connectionId, DittoHeaders.empty());
        enableConnectionLogsResponse = EnableConnectionLogsResponse.of(connectionId,
                enableConnectionLogs.getDittoHeaders());

    }

    @Test
    public void testConnection() {
        new TestKit(actorSystem) {{
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);

            underTest.tell(testConnection, getRef());

            final TestConnectionResponse testConnectionResponse =
                    TestConnectionResponse.success(connectionId, "mock", DittoHeaders.empty());
            expectMsg(testConnectionResponse);
        }};
    }

    @Test
    public void testConnectionCausingFailure() {
        new TestKit(actorSystem) {{
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);

            underTest.tell(testConnectionCausingFailure, getRef());

            final DittoRuntimeException exception = ConnectionIdInvalidException.newBuilder("invalid").build();
            expectMsg(exception);
        }};
    }

    @Test
    public void testConnectionCausingException() {
        new TestKit(actorSystem) {{
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);

            underTest.tell(testConnectionCausingException, getRef());

            final DittoRuntimeException exception = ConnectionIdInvalidException.newBuilder("invalid").build();
            expectMsg(exception);
        }};
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        new TestKit(actorSystem) {{
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);

            underTest.tell(deleteConnection, getRef());

            final ConnectionNotAccessibleException expectedMessage =
                    ConnectionNotAccessibleException.newBuilder(connectionId).build();
            expectMsg(expectedMessage);
        }};
    }

    @Test
    public void manageConnection() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(
                    connectionId, actorSystem, proxyActor,
                    (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()),
                    pubSubMediator
            );
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // close connection
            underTest.tell(closeConnection, getRef());
            probe.expectMsg(closeConnection);
            expectMsg(closeConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);
            probe.expectNoMessage();
            expectTerminated(underTest);
        }};
    }

    @Test
    public void deleteConnectionUpdatesSubscriptions() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest = TestConstants.createConnectionSupervisorActor(
                    connectionId, actorSystem, proxyActor,
                    (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()),
                    pubSubMediator
            );
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);
            probe.expectNoMessage();
            expectTerminated(underTest);
        }};

    }

    @Test
    public void manageConnectionWith2Clients() throws Exception {
        startSecondActorSystemAndJoinCluster();
        new TestKit(actorSystem) {{
            final TestProbe gossipProbe = TestProbe.apply("gossip", actorSystem);
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor, (a, b, c) -> MockClientActor.props(probe.ref(), gossipProbe.ref()));
            watch(underTest);

            // create closed connection
            underTest.tell(createClosedConnectionWith2Clients, getRef());
            expectMsgClass(CreateConnectionResponse.class);

            // open connection: only local client actor is asked for a response.
            underTest.tell(openConnection, getRef());
            // perform gossip protocol on client actor startup
            underTest.tell(gossipProbe.expectMsgClass(ActorRef.class), ActorRef.noSender());
            underTest.tell(gossipProbe.expectMsgClass(ActorRef.class), ActorRef.noSender());
            // one client actor receives the command
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            probe.expectMsg(enableConnectionLogs);
            expectMsgClass(OpenConnectionResponse.class);

            // forward signal once
            underTest.tell(CreateSubscription.of(DittoHeaders.empty()), getRef());
            probe.expectMsgClass(CreateSubscription.class); // is not the exact command because prefix is added

            // close connection: at least 1 client actor gets the command; the other may or may not be started.
            underTest.tell(closeConnection, getRef());
            probe.expectMsg(closeConnection);
            expectMsg(closeConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);
            expectTerminated(underTest);
        }};
    }

    @Test
    public void createConnectionAfterDeleted() {
        new TestKit(actorSystem) {{
            final TestProbe clientActorMock = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(clientActorMock.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            clientActorMock.expectMsg(enableConnectionLogs);
            clientActorMock.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);

            // create connection again (while ConnectionActor is in deleted state)
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);
            clientActorMock.expectMsg(enableConnectionLogs);
            clientActorMock.expectMsg(openConnection);
        }};
    }

    @Test
    public void openConnectionAfterDeletedFails() {
        new TestKit(actorSystem) {{
            final TestProbe clientActorMock = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(clientActorMock.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            clientActorMock.expectMsg(enableConnectionLogs);
            clientActorMock.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);

            // open connection should fail
            underTest.tell(openConnection, getRef());
            expectMsg(connectionNotAccessibleException);
        }};
    }

    @Test
    public void createConnectionInClosedState() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createClosedConnection, getRef());
            expectMsg(createClosedConnectionResponse);

            // assert that client actor is not called for closed connection
            probe.expectNoMessage();
        }};
    }

    @Test
    public void createClosedConnectionWithUnknownHost() {
        final CreateConnection createClosedConnectionWithUnknownHost =
                CreateConnection.of(closedConnection.toBuilder().uri("amqp://invalid:1234").build(),
                        DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                entry(createClosedConnectionWithUnknownHost,
                        ConnectionPersistenceActorTest::assertHostInvalid));
    }

    @Test
    public void testConnectionWithUnknownHost() {
        final TestConnection testConnectionWithUnknownHost =
                TestConnection.of(closedConnection.toBuilder().uri("amqp://invalid:1234").build(),
                        DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                entry(testConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostInvalid));
    }

    @Test
    public void modifyClosedConnectionWithUnknownHost() {

        // connection is created with a valid host/ip
        final CreateConnection createClosedConnectionWithValidHost =
                CreateConnection.of(closedConnection.toBuilder().uri("amqp://8.8.8.8:1234").build(),
                        DittoHeaders.empty());

        // later modified with an invalid host
        final ModifyConnection modifyClosedConnectionWithInvalidHost =
                ModifyConnection.of(createClosedConnectionWithValidHost.getConnection().toBuilder()
                        .uri("amqp://invalid:1234").build(), DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                // create is successful
                entry(createClosedConnectionWithValidHost, ConnectionPersistenceActorTest::assertConnectionCreated),
                // modify fails because the new host is invalid
                entry(modifyClosedConnectionWithInvalidHost, ConnectionPersistenceActorTest::assertHostInvalid
                ));
    }

    @Test
    public void createClosedConnectionWithBlockedHost() {

        final CreateConnection createClosedConnectionWithBlockedHost =
                CreateConnection.of(closedConnection.toBuilder().uri("amqp://localhost:1234").build(),
                        DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                entry(createClosedConnectionWithBlockedHost,
                        ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @Test
    public void testConnectionWithBlockedHost() {
        final TestConnection testConnectionWithUnknownHost =
                TestConnection.of(closedConnection.toBuilder().uri("amqp://localhost:1234").build(),
                        DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                entry(testConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @Test
    public void modifyClosedConnectionWithBlockedHost() {

        // connection is created with a valid host/ip
        final CreateConnection createClosedConnectionWithValidHost =
                CreateConnection.of(closedConnection.toBuilder().uri("amqp://8.8.8.8:1234").build(),
                        DittoHeaders.empty());

        // later modified with a blocked host
        final ModifyConnection modifyClosedConnectionWithBlockedHost =
                ModifyConnection.of(createClosedConnectionWithValidHost.getConnection().toBuilder()
                        .uri("amqp://localhost:1234").build(), DittoHeaders.empty());

        sendCommandWithEnabledBlocklist(
                // create is successful
                entry(createClosedConnectionWithValidHost, ConnectionPersistenceActorTest::assertConnectionCreated),
                // modify fails because the new host is invalid
                entry(modifyClosedConnectionWithBlockedHost,
                        ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @SafeVarargs
    private void sendCommandWithEnabledBlocklist(
            final Map.Entry<ConnectivityCommand<?>, Consumer<Object>>... commands) {
        final Config configWithBlocklist =
                TestConstants.CONFIG.withValue("ditto.connectivity.connection.blocked-hostnames",
                        ConfigValueFactory.fromAnyRef("127.0.0.1"));
        final ActorSystem systemWithBlocklist = ActorSystem.create(getClass().getSimpleName() + "WithBlocklist",
                configWithBlocklist);
        final ActorRef pubSubMediator = DistributedPubSub.get(systemWithBlocklist).mediator();

        try {
            new TestKit(systemWithBlocklist) {{
                final TestProbe probe = TestProbe.apply(systemWithBlocklist);
                final ActorRef underTest =
                        TestConstants.createConnectionSupervisorActor(connectionId, systemWithBlocklist,
                                pubSubMediator, proxyActor,
                                (connection, proxy, connectionActor) -> MockClientActor.props(probe.ref()));
                watch(underTest);

                for (final Map.Entry<ConnectivityCommand<?>, Consumer<Object>> command : commands) {
                    underTest.tell(command.getKey(), getRef());
                    command.getValue().accept(expectMsgClass(Duration.ofSeconds(10), Object.class));
                }

                // assert that client actor is not called for closed connection
                probe.expectNoMessage();
            }};
        } finally {
            TestKit.shutdownActorSystem(systemWithBlocklist);
        }
    }

    private static void assertHostInvalid(Object response) {
        assertThat(response).isInstanceOf(ConnectionConfigurationInvalidException.class);
        final ConnectionConfigurationInvalidException exception = (ConnectionConfigurationInvalidException) response;
        assertThat(exception).hasMessageContaining("The configured host 'invalid' is invalid");
    }

    private static void assertHostBlocked(Object response) {
        assertThat(response).isInstanceOf(ConnectionConfigurationInvalidException.class);
        final ConnectionConfigurationInvalidException e = (ConnectionConfigurationInvalidException) response;
        assertThat(e).hasMessageContaining("The configured host 'localhost' may not be used for the connection");
    }

    private static void assertConnectionCreated(Object response) {
        assertThat(response).isInstanceOf(CreateConnectionResponse.class);
    }

    @Test
    public void modifyConnectionInClosedState() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // close connection
            underTest.tell(closeConnection, getRef());
            probe.expectMsg(closeConnection);
            expectMsg(closeConnectionResponse);

            // modify connection
            underTest.tell(modifyClosedConnection, getRef());
            // client actor is not informed about modification as it is not started
            probe.expectNoMessage();
            expectMsg(modifyConnectionResponse);
        }};
    }

    @Test
    public void retrieveMetricsInClosedStateDoesNotStartClientActor() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createClosedConnection, getRef());
            expectMsg(createClosedConnectionResponse);
            probe.expectNoMessage();

            // retrieve metrics
            underTest.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());
            probe.expectNoMessage();

            final RetrieveConnectionMetricsResponse metricsResponse =
                    RetrieveConnectionMetricsResponse.getBuilder(connectionId, DittoHeaders.empty())
                            .connectionMetrics(ConnectivityModelFactory.emptyConnectionMetrics())
                            .sourceMetrics(ConnectivityModelFactory.emptySourceMetrics())
                            .targetMetrics(ConnectivityModelFactory.emptyTargetMetrics())
                            .build();
            expectMsg(metricsResponse);
        }};
    }

    @Test
    public void modifyConnectionClosesAndRestartsClientActor() {
        new TestKit(actorSystem) {{
            final TestProbe mockClientProbe = TestProbe.apply(actorSystem);
            final TestProbe commandSender = TestProbe.apply(actorSystem);
            final AtomicReference<Connection> latestConnection = new AtomicReference<>();
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId,
                            actorSystem,
                            proxyActor, (connection, proxyActor, connectionActor) -> {
                                latestConnection.set(connection);
                                return MockClientActor.props(mockClientProbe.ref());
                            },
                            pubSubMediator);

            // create connection
            underTest.tell(createConnection, commandSender.ref());
            mockClientProbe.expectMsg(enableConnectionLogs);
            mockClientProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), openConnection);
            commandSender.expectMsg(createConnectionResponse);

            final ActorRef clientActor = watch(mockClientProbe.sender());

            // modify connection
            underTest.tell(modifyConnection, commandSender.ref());
            // modify triggers a CloseConnection
            mockClientProbe.expectMsg(CloseConnection.of(connectionId, DittoHeaders.empty()));

            // unsubscribe is called for topics of unmodified connection
            expectTerminated(clientActor);

            // and sends an open connection (if desired state is open). Since logging is enabled from creation
            // enabledConnectionLogs is also expected
            mockClientProbe.expectMsg(enableConnectionLogs);
            mockClientProbe.expectMsg(openConnection);
            // finally the response is sent
            commandSender.expectMsg(modifyConnectionResponse);

            // modified connection contains an additional target for messages
            Awaitility.await().untilAtomic(latestConnection, CoreMatchers.is(modifyConnection.getConnection()));
        }};
    }

    @Test
    public void recoverOpenConnection() {
        new TestKit(actorSystem) {{

            final TestProbe mockClientProbe = TestProbe.apply(actorSystem);
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(mockClientProbe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            // wait for open connection of initial creation
            mockClientProbe.expectMsg(enableConnectionLogs);
            mockClientProbe.expectMsg(openConnection);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            final TestProbe recoveredMockClientProbe = TestProbe.apply(actorSystem);
            underTest = Retry.untilSuccess(
                    () -> TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor, (connection, concierge, connectionActor) -> MockClientActor.props(
                                    recoveredMockClientProbe.ref())));

            // connection is opened after recovery -> recovered client actor receives OpenConnection command
            recoveredMockClientProbe.expectMsg(openConnection);

            // poll connection status until status is OPEN
            final ActorRef recoveredActor = underTest;
            Awaitility.await().untilAsserted(() -> {
                recoveredActor.tell(retrieveConnectionStatus, getRef());
                expectMsg(retrieveConnectionStatusOpenResponse);
            });
        }};
    }

    @Test
    public void recoverModifiedConnection() {
        new TestKit(actorSystem) {{

            ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, proxyActor);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            // modify connection
            underTest.tell(modifyConnection, getRef());
            expectMsg(modifyConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                    proxyActor);

            // retrieve connection status
            underTest.tell(retrieveConnection, getRef());
            expectMsg(retrieveModifiedConnectionResponse);
        }};
    }

    @Test
    public void recoverClosedConnection() {
        new TestKit(actorSystem) {{
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            // close connection
            underTest.tell(closeConnection, getRef());
            expectMsg(closeConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = Retry.untilSuccess(() ->
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor));

            // retrieve connection status
            underTest.tell(retrieveConnectionStatus, getRef());
            final RetrieveConnectionStatusResponse response = expectMsgClass(RetrieveConnectionStatusResponse.class);

            assertThat((Object) response.getConnectionStatus()).isEqualTo(ConnectivityStatus.CLOSED);
            assertThat(response.getSourceStatus()).isEmpty();
            assertThat(response.getTargetStatus()).isEmpty();
            assertThat(response.getClientStatus()).hasSize(1);
            assertThat((CharSequence) response.getClientStatus().get(0).getStatus()).isEqualTo(
                    ConnectivityStatus.CLOSED);
            assertThat(response.getClientStatus().get(0).getStatusDetails())
                    .contains(String.format("[%s] connection is closed", BaseClientState.DISCONNECTED));
        }};
    }

    @Test
    public void recoverDeletedConnection() {
        new TestKit(actorSystem) {{
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);
            expectTerminated(underTest);

            // recover actor
            underTest = Retry.untilSuccess(() ->
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor));

            // retrieve connection status
            underTest.tell(retrieveConnectionStatus, getRef());
            expectMsg(connectionNotAccessibleException);
        }};
    }

    @Test
    public void exceptionDuringClientActorPropsCreation() {
        new TestKit(actorSystem) {{
            final Props connectionActorProps =
                    ConnectionPersistenceActor.props(TestConstants.createRandomConnectionId(),
                            proxyActor,
                            (connection, proxyActor, connectionActor) -> {
                                throw ConnectionConfigurationInvalidException.newBuilder("validation failed...")
                                        .build();
                            }, null, UsageBasedPriorityProvider::getInstance);
            // create another actor because this it is stopped and we want to test if the child is terminated
            final TestKit parent = new TestKit(actorSystem);
            final ActorRef connectionActorRef = watch(parent.childActorOf(connectionActorProps));

            // create connection
            connectionActorRef.tell(createConnection, parent.getRef());

            // expect ConnectionConfigurationInvalidException sent to parent
            final Exception exception = parent.expectMsgClass(ConnectionConfigurationInvalidException.class);
            assertThat(exception).hasMessageContaining("validation failed...");

            // connection actor will stop after activity check.
        }};
    }

    @Test
    public void exceptionDueToCustomValidator() {
        new TestKit(actorSystem) {{
            final Props connectionActorProps =
                    ConnectionPersistenceActor.props(TestConstants.createRandomConnectionId(),
                            proxyActor,
                            mockClientActorPropsFactory,
                            (command, connection) -> {
                                throw ConnectionUnavailableException.newBuilder(connectionId)
                                        .dittoHeaders(command.getDittoHeaders())
                                        .message("not valid")
                                        .build();
                            }, UsageBasedPriorityProvider::getInstance);

            // create another actor because we want to test if the child is terminated
            final TestKit parent = new TestKit(actorSystem);
            final ActorRef connectionActorRef = watch(parent.childActorOf(connectionActorProps));

            // create connection
            connectionActorRef.tell(createConnection, parent.getRef());

            // expect ConnectionUnavailableException sent to parent
            final ConnectionUnavailableException exception =
                    parent.expectMsgClass(ConnectionUnavailableException.class);
            assertThat(exception).hasMessageContaining("not valid");

            // do not expect passivation; it only happens for graceful shutdown.
        }};
    }

    @Test
    public void testResetConnectionMetrics() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // reset metrics
            underTest.tell(resetConnectionMetrics, getRef());
            probe.expectMsg(resetConnectionMetrics);

            final ResetConnectionMetricsResponse resetResponse =
                    ResetConnectionMetricsResponse.of(connectionId, DittoHeaders.empty());
            expectMsg(resetResponse);
        }};
    }

    @Test
    public void testConnectionActorRespondsToCleanupCommand() {
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // send cleanup command
            underTest.tell(CleanupPersistence.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CleanupPersistenceResponse.success(connectionId, DittoHeaders.empty()
            ));
        }};
    }

    @Test
    public void enableConnectionLogs() {
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            //Close logging which are automatically enabled via create connection
            underTest.tell(LoggingExpired.of(connectionId, DittoHeaders.empty()), getRef());

            // enable connection logs
            underTest.tell(enableConnectionLogs, getRef());
            probe.expectMsg(enableConnectionLogs);

            expectMsg(enableConnectionLogsResponse);

        }};
    }

    @Test
    public void retrieveLogsInClosedStateDoesNotStartClientActor() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createClosedConnection, getRef());
            expectMsg(createClosedConnectionResponse);
            probe.expectNoMessage();

            // retrieve logs
            underTest.tell(RetrieveConnectionLogs.of(connectionId, DittoHeaders.empty()), getRef());
            probe.expectNoMessage();

            final RetrieveConnectionLogsResponse logsResponse =
                    RetrieveConnectionLogsResponse.of(connectionId,
                            Collections.emptyList(),
                            null,
                            null,
                            DittoHeaders.empty());
            expectMsg(logsResponse);
        }};
    }

    @Test
    public void retrieveLogsIsAggregated() {
        final Instant now = Instant.now();
        final RetrieveConnectionLogsResponse innerResponse = RetrieveConnectionLogsResponse.of(connectionId,
                TestConstants.Monitoring.LOG_ENTRIES,
                now.minusSeconds(312),
                now.plusSeconds(123),
                DittoHeaders.empty());

        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // retrieve logs
            final RetrieveConnectionLogs retrieveConnectionLogs =
                    RetrieveConnectionLogs.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionLogs, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(retrieveConnectionLogs);

            // send answer to aggregator actor
            final ActorRef aggregatorActor = probe.sender();
            probe.send(aggregatorActor, innerResponse);

            expectMsg(innerResponse);
        }};
    }

    @Test
    public void resetConnectionLogs() {
        final ResetConnectionLogs resetConnectionLogs = ResetConnectionLogs.of(connectionId, DittoHeaders.empty());
        final ResetConnectionLogsResponse expectedResponse =
                ResetConnectionLogsResponse.of(connectionId, resetConnectionLogs.getDittoHeaders());

        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // reset logs
            underTest.tell(resetConnectionLogs, getRef());
            probe.expectMsg(resetConnectionLogs);

            expectMsg(expectedResponse);
        }};
    }

    @Test
    public void enabledConnectionLogsAreEnabledAgainAfterModify() {
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()),
                            pubSubMediator);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // Wait until connection is established
            // enable connection logs
            underTest.tell(enableConnectionLogs, getRef());
            probe.expectMsg(enableConnectionLogs);

            expectMsg(enableConnectionLogsResponse);

            // modify connection
            underTest.tell(modifyConnection, getRef());
            probe.expectMsg(closeConnection);
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(modifyConnectionResponse);

            // expect the message twice, once for each client
            probe.expectMsg(enableConnectionLogs);
        }};
    }

    @Test
    public void disabledConnectionLogsAreNotEnabledAfterModify() {
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(enableConnectionLogs);
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            //Close logging which are automatically enabled via create connection
            underTest.tell(LoggingExpired.of(connectionId, DittoHeaders.empty()), getRef());

            // modify connection
            underTest.tell(modifyConnection, getRef());
            probe.expectMsg(closeConnection);
            probe.expectMsg(openConnection);
            expectMsg(modifyConnectionResponse);

            probe.expectNoMsg();
        }};
    }

    @Test
    public void forwardSearchCommands() {
        new TestKit(actorSystem) {{
            final ConnectionId myConnectionId = ConnectionId.of(UUID.randomUUID().toString());
            final TestProbe gossipProbe = TestProbe.apply("gossip", actorSystem);
            final TestProbe clientActorsProbe = TestProbe.apply("clientActors", actorSystem);
            final TestProbe proxyActorProbe = TestProbe.apply("proxyActor", actorSystem);
            // Mock the client actors so that they forward all signals to clientActorsProbe with their own reference
            final ClientActorPropsFactory propsFactory = (a, b, connectionActor) ->
                    Props.create(AbstractActor.class, () -> new AbstractActor() {
                        @Override
                        public Receive createReceive() {
                            return ReceiveBuilder.create()
                                    .match(WithDittoHeaders.class, message -> clientActorsProbe.ref()
                                            .tell(WithSender.of(message, getSelf()), getSender()))
                                    .match(ActorRef.class, actorRef ->
                                            gossipProbe.ref().forward(actorRef, getContext()))
                                    .build();
                        }

                        @Override
                        public void preStart() {
                            connectionActor.tell(getSelf(), getSelf());
                        }
                    });
            final Props connectionActorProps = Props.create(ConnectionPersistenceActor.class, () ->
                    new ConnectionPersistenceActor(myConnectionId, proxyActorProbe.ref(),
                            propsFactory, null,
                            UsageBasedPriorityProvider::getInstance,
                            Trilean.TRUE
                    ));

            // GIVEN: connection persistence actor created with 2 client actors that are allowed to start on same node
            final ActorRef underTest = actorSystem.actorOf(connectionActorProps, myConnectionId.toString());
            underTest.tell(createClosedConnectionWith2Clients, getRef());
            expectMsgClass(CreateConnectionResponse.class);
            underTest.tell(OpenConnection.of(myConnectionId, DittoHeaders.empty()), getRef());
            assertThat(clientActorsProbe.expectMsgClass(WithSender.class).getMessage())
                    .isInstanceOf(EnableConnectionLogs.class);
            assertThat(clientActorsProbe.expectMsgClass(WithSender.class).getMessage())
                    .isInstanceOf(EnableConnectionLogs.class);
            assertThat(clientActorsProbe.expectMsgClass(WithSender.class).getMessage())
                    .isInstanceOf(OpenConnection.class);
            clientActorsProbe.reply(new Status.Success("connected"));
            expectMsgClass(OpenConnectionResponse.class);

            // wait until gossip protocol completes
            gossipProbe.expectMsgClass(ActorRef.class);
            gossipProbe.expectMsgClass(ActorRef.class);

            // WHEN: 2 CreateSubscription commands are received
            // THEN: The 2 commands land in different client actors
            underTest.tell(CreateSubscription.of(DittoHeaders.empty()), getRef());
            underTest.tell(CreateSubscription.of(DittoHeaders.empty()), getRef());
            final WithSender<?> createSubscription1 = clientActorsProbe.expectMsgClass(WithSender.class);
            final WithSender<?> createSubscription2 = clientActorsProbe.expectMsgClass(WithSender.class);
            assertThat(createSubscription1.getSender()).isNotEqualTo(createSubscription2.getSender());
        }};
    }

    private void startSecondActorSystemAndJoinCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        actorSystem2 = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        final Cluster cluster1 = Cluster.get(actorSystem);
        final Cluster cluster2 = Cluster.get(actorSystem2);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        latch.await();
    }

    private static void shutdown(@Nullable final ActorSystem system) {
        if (system != null) {
            TestKit.shutdownActorSystem(system, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

}
