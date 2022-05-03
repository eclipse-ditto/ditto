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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.eclipse.ditto.connectivity.service.messaging.MockClientActor.mockClientActorPropsFactory;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.INSTANT;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.LoggingExpired;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.connectivity.service.messaging.MockClientActor;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.WithMockServers;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorTest extends WithMockServers {

    @Rule
    public final ActorSystemResource actorSystemResource1 = ActorSystemResource.newInstance(TestConstants.CONFIG);

    @Rule
    public final ActorSystemResource actorSystemResource2 = ActorSystemResource.newInstance(TestConstants.CONFIG);

    @Rule
    public final ActorSystemResource actorSystemResourceWithBlocklist = ActorSystemResource.newInstance(
            TestConstants.CONFIG.withValue(
                    "ditto.connectivity.connection.blocked-hostnames",
                    ConfigValueFactory.fromAnyRef("127.0.0.1")
            )
    );

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private ActorRef pubSubMediator;
    private ActorRef proxyActor;
    private DittoHeaders dittoHeadersWithCorrelationId;
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
    private Connection closedConnection;

    @Before
    public void init() {
        pubSubMediator = DistributedPubSub.get(actorSystemResource1.getActorSystem()).mediator();
        proxyActor = actorSystemResource1.newActor(TestConstants.ProxyActorMock.props());
        connectionId = TestConstants.createRandomConnectionId();
        final var connection = TestConstants.createConnection(connectionId);
        final var closedConnectionWith2Clients =
                connection.toBuilder().clientCount(2).connectionStatus(ConnectivityStatus.CLOSED).build();
        closedConnection = TestConstants.createConnection(connectionId,
                ConnectivityStatus.CLOSED,
                TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);

        dittoHeadersWithCorrelationId =
                DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();

        createConnection = CreateConnection.of(connection, dittoHeadersWithCorrelationId);
        createClosedConnectionWith2Clients =
                CreateConnection.of(closedConnectionWith2Clients, dittoHeadersWithCorrelationId);
        createClosedConnection = CreateConnection.of(closedConnection, dittoHeadersWithCorrelationId);
        final var modifiedConnection = ConnectivityModelFactory.newConnectionBuilder(connection)
                .failoverEnabled(false)
                .targets(Collections.singletonList(TestConstants.Targets.MESSAGE_TARGET))
                .build();
        modifyConnection = ModifyConnection.of(modifiedConnection, dittoHeadersWithCorrelationId);
        modifyClosedConnection = ModifyConnection.of(closedConnection, dittoHeadersWithCorrelationId);
        deleteConnection = DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId);
        createConnectionResponse = CreateConnectionResponse.of(connection, dittoHeadersWithCorrelationId);
        createClosedConnectionResponse = CreateConnectionResponse.of(closedConnection, dittoHeadersWithCorrelationId);
        modifyConnectionResponse = ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId);
        openConnection = OpenConnection.of(connectionId, dittoHeadersWithCorrelationId);
        closeConnection = CloseConnection.of(connectionId, dittoHeadersWithCorrelationId);
        testConnection = TestConnection.of(connection, dittoHeadersWithCorrelationId);
        testConnectionCausingFailure = TestConnection.of(connection,
                DittoHeaders.newBuilder(dittoHeadersWithCorrelationId).putHeader("fail", "true").build());
        testConnectionCausingException = TestConnection.of(connection,
                DittoHeaders.newBuilder(dittoHeadersWithCorrelationId).putHeader("error", "true").build());
        closeConnectionResponse = CloseConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId);
        deleteConnectionResponse = DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId);
        retrieveConnection = RetrieveConnection.of(connectionId, dittoHeadersWithCorrelationId);
        retrieveConnectionStatus = RetrieveConnectionStatus.of(connectionId, dittoHeadersWithCorrelationId);
        resetConnectionMetrics = ResetConnectionMetrics.of(connectionId, dittoHeadersWithCorrelationId);
        retrieveModifiedConnectionResponse =
                RetrieveConnectionResponse.of(modifiedConnection.toJson(), dittoHeadersWithCorrelationId);
        retrieveConnectionStatusOpenResponse =
                RetrieveConnectionStatusResponse.getBuilder(connectionId, dittoHeadersWithCorrelationId)
                        .connectionStatus(ConnectivityStatus.OPEN)
                        .liveStatus(ConnectivityStatus.OPEN)
                        .recoveryStatus(RecoveryStatus.SUCCEEDED)
                        .connectedSince(INSTANT)
                        .clientStatus(List.of(ConnectivityModelFactory.newClientStatus("client1",
                                ConnectivityStatus.OPEN,
                                RecoveryStatus.SUCCEEDED,
                                "connection is open",
                                INSTANT)))
                        .sourceStatus(List.of(ConnectivityModelFactory.newSourceStatus("client1",
                                        ConnectivityStatus.OPEN,
                                        "source1",
                                        "consumer started"),
                                ConnectivityModelFactory.newSourceStatus("client1",
                                        ConnectivityStatus.OPEN,
                                        "source2",
                                        "consumer started")))
                        .targetStatus(List.of(ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN,
                                        "target1",
                                        "publisher started"),
                                ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN,
                                        "target2",
                                        "publisher started"),
                                ConnectivityModelFactory.newTargetStatus("client1",
                                        ConnectivityStatus.OPEN,
                                        "target3",
                                        "publisher started")))
                        .sshTunnelStatus(List.of())
                        .build();
        connectionNotAccessibleException = ConnectionNotAccessibleException.newBuilder(connectionId).build();
        enableConnectionLogs = EnableConnectionLogs.of(connectionId, DittoHeaders.empty());
        enableConnectionLogsResponse = EnableConnectionLogsResponse.of(connectionId,
                enableConnectionLogs.getDittoHeaders());
    }

    @Test
    public void testConnection() {
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);
        final var testProbe = actorSystemResource1.newTestProbe();

        underTest.tell(testConnection, testProbe.ref());

        testProbe.expectMsg(TestConnectionResponse.success(connectionId, "mock", testConnection.getDittoHeaders()));
    }

    @Test
    public void testConnectionCausingFailure() {
        final var testProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);

        underTest.tell(testConnectionCausingFailure, testProbe.ref());

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnectionCausingFailure.getDittoHeaders())
                .build());
    }

    @Test
    public void testConnectionCausingException() {
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);
        final var testProbe = actorSystemResource1.newTestProbe();

        underTest.tell(testConnectionCausingException, testProbe.ref());

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnectionCausingException.getDittoHeaders())
                .build());
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        final var testProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);

        underTest.tell(deleteConnection, testProbe.ref());

        testProbe.expectMsg(ConnectionNotAccessibleException.newBuilder(connectionId)
                .dittoHeaders(deleteConnection.getDittoHeaders())
                .build());
    }

    @Test
    public void manageConnection() {
        final var clientActorTestProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor,
                mockClientActorPropsFactory(clientActorTestProbe.ref()),
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorTestProbe.expectMsg(enableConnectionLogs);
        clientActorTestProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // close connection
        underTest.tell(closeConnection, testProbe.ref());
        clientActorTestProbe.expectMsg(closeConnection);
        testProbe.expectMsg(closeConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);
        clientActorTestProbe.expectNoMessage();
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void deleteConnectionUpdatesSubscriptionsAndClosesConnection() {
        final var clientActorTestProbe = actorSystemResource1.newTestProbe();
        final var pubSubTestProbe = actorSystemResource1.newTestProbe("mock-pubSub-mediator");
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor,
                mockClientActorPropsFactory(clientActorTestProbe.ref()),
                pubSubTestProbe.ref());
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorTestProbe.expectMsg(enableConnectionLogs);
        clientActorTestProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        clientActorTestProbe.expectMsg(closeConnection);
        testProbe.expectMsg(deleteConnectionResponse);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void manageConnectionWith2Clients() throws Exception {
        startSecondActorSystemAndJoinCluster();

        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var gossipProbe = actorSystemResource1.newTestProbe("gossip");
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                (a, b, c, d, dittoHeaders, overwrites) -> MockClientActor.props(clientActorProbe.ref(),
                        gossipProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create closed connection
        underTest.tell(createClosedConnectionWith2Clients, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);

        // open connection: only local client actor is asked for a response.
        underTest.tell(openConnection, testProbe.ref());

        // perform gossip protocol on client actor startup
        underTest.tell(gossipProbe.expectMsgClass(ActorRef.class), ActorRef.noSender());
        underTest.tell(gossipProbe.expectMsgClass(ActorRef.class), ActorRef.noSender());

        // one client actor receives the command
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        clientActorProbe.expectMsg(enableConnectionLogs);
        testProbe.expectMsgClass(OpenConnectionResponse.class);

        // forward signal once
        underTest.tell(CreateSubscription.of(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build()), testProbe.ref());
        clientActorProbe.fishForMessage(scala.concurrent.duration.Duration.apply(30, TimeUnit.SECONDS),
                "CreateSubscription",
                PartialFunction.fromFunction(CreateSubscription.class::isInstance));

        // close connection: at least 1 client actor gets the command; the other may or may not be started.
        underTest.tell(closeConnection, testProbe.ref());
        clientActorProbe.expectMsg(closeConnection);
        testProbe.expectMsg(closeConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    private void startSecondActorSystemAndJoinCluster() throws Exception {
        final var latch = new CountDownLatch(2);
        final var cluster1 = Cluster.get(actorSystemResource1.getActorSystem());
        final var cluster2 = Cluster.get(actorSystemResource2.getActorSystem());
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        latch.await();
    }

    @Test
    public void createConnectionAfterDeleted() {
        final var clientActorMock = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorMock.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorMock.expectMsg(enableConnectionLogs);
        clientActorMock.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        clientActorMock.expectMsg(closeConnection);
        testProbe.expectMsg(deleteConnectionResponse);

        // create connection again (while ConnectionActor is in deleted state)
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);
        clientActorMock.expectMsg(enableConnectionLogs);
        clientActorMock.expectMsg(openConnection);
    }

    @Test
    public void openConnectionAfterDeletedFails() {
        final var clientActorMock = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorMock.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorMock.expectMsg(enableConnectionLogs);
        clientActorMock.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);

        // open connection should fail
        underTest.tell(openConnection, testProbe.ref());
        testProbe.expectMsg(connectionNotAccessibleException);
    }

    @Test
    public void createConnectionInClosedState() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createClosedConnection, testProbe.ref());
        testProbe.expectMsg(createClosedConnectionResponse);

        // assert that client actor is not called for closed connection
        clientActorProbe.expectNoMessage();
    }

    @Test
    public void createClosedConnectionWithUnknownHost() {
        final var createClosedConnectionWithUnknownHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://invalid:1234").build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(
                entry(createClosedConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostInvalid));
    }

    @Test
    public void testConnectionWithUnknownHost() {
        final var testConnectionWithUnknownHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://invalid:1234").build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(
                entry(testConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostInvalid));
    }

    @Test
    public void modifyClosedConnectionWithUnknownHost() {

        // connection is created with a valid host/IP
        final var createClosedConnectionWithValidHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://8.8.8.8:1234").build(),
                dittoHeadersWithCorrelationId
        );

        // later modified with an invalid host
        final var modifyClosedConnectionWithInvalidHost = ModifyConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(createClosedConnectionWithValidHost.getConnection())
                        .uri("amqp://invalid:1234")
                        .build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(

                // create is successful
                entry(createClosedConnectionWithValidHost, ConnectionPersistenceActorTest::assertConnectionCreated),

                // modify fails because the new host is invalid
                entry(modifyClosedConnectionWithInvalidHost, ConnectionPersistenceActorTest::assertHostInvalid)
        );
    }

    @Test
    public void createClosedConnectionWithBlockedHost() {
        final var createClosedConnectionWithBlockedHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://localhost:1234").build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(
                entry(createClosedConnectionWithBlockedHost, ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @Test
    public void testConnectionWithBlockedHost() {
        final var testConnectionWithUnknownHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://localhost:1234").build(),
                dittoHeadersWithCorrelationId);

        sendCommandWithEnabledBlocklist(
                entry(testConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @Test
    public void modifyClosedConnectionWithBlockedHost() {

        // connection is created with a valid host/IP
        final var createClosedConnectionWithValidHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://8.8.8.8:1234").build(),
                dittoHeadersWithCorrelationId
        );

        // later modified with a blocked host
        final var modifyClosedConnectionWithBlockedHost = ModifyConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(createClosedConnectionWithValidHost.getConnection())
                        .uri("amqp://localhost:1234")
                        .build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(

                // create is successful
                entry(createClosedConnectionWithValidHost, ConnectionPersistenceActorTest::assertConnectionCreated),

                // modify fails because the new host is invalid
                entry(modifyClosedConnectionWithBlockedHost, ConnectionPersistenceActorTest::assertHostBlocked)
        );
    }

    @SafeVarargs
    private void sendCommandWithEnabledBlocklist(final Map.Entry<ConnectivityCommand<?>, Consumer<Object>>... commands) {
        final var pubSubMediator = DistributedPubSub.get(actorSystemResourceWithBlocklist.getActorSystem()).mediator();

        final var clientActorProbe = actorSystemResourceWithBlocklist.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResourceWithBlocklist.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResourceWithBlocklist.newTestProbe();
        testProbe.watch(underTest);

        for (final var command : commands) {
            underTest.tell(command.getKey(), testProbe.ref());
            final var commandValue = command.getValue();

            commandValue.accept(testProbe.expectMsgClass(FiniteDuration.apply(30, TimeUnit.SECONDS), Object.class));
        }

        // assert that client actor is not called for closed connection
        clientActorProbe.expectNoMessage();
    }

    private static void assertHostInvalid(final Object response) {
        assertThat(response).isInstanceOfSatisfying(ConnectionConfigurationInvalidException.class,
                exception -> assertThat(exception).hasMessageContaining("The configured host 'invalid' is invalid"));
    }

    private static void assertHostBlocked(final Object response) {
        assertThat(response).isInstanceOfSatisfying(ConnectionConfigurationInvalidException.class,
                exception -> assertThat(exception).hasMessageContaining(
                        "The configured host 'localhost' may not be used for the connection"));
    }

    private static void assertConnectionCreated(final Object response) {
        assertThat(response).isInstanceOf(CreateConnectionResponse.class);
    }

    @Test
    public void modifyConnectionInClosedState() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // close connection
        underTest.tell(closeConnection, testProbe.ref());
        clientActorProbe.expectMsg(closeConnection);
        testProbe.expectMsg(closeConnectionResponse);

        // modify connection
        underTest.tell(modifyClosedConnection, testProbe.ref());

        // client actor is not informed about modification as it is not started
        clientActorProbe.expectNoMessage();
        testProbe.expectMsg(modifyConnectionResponse);
    }

    @Test
    public void retrieveMetricsInClosedStateDoesNotStartClientActor() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();

        // create connection
        underTest.tell(createClosedConnection, testProbe.ref());
        testProbe.expectMsg(createClosedConnectionResponse);
        clientActorProbe.expectNoMessage();

        // retrieve metrics
        underTest.tell(RetrieveConnectionMetrics.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        clientActorProbe.expectNoMessage();

        final var metricsResponse =
                RetrieveConnectionMetricsResponse.getBuilder(connectionId, dittoHeadersWithCorrelationId)
                        .connectionMetrics(ConnectivityModelFactory.emptyConnectionMetrics())
                        .sourceMetrics(ConnectivityModelFactory.emptySourceMetrics())
                        .targetMetrics(ConnectivityModelFactory.emptyTargetMetrics())
                        .build();
        testProbe.expectMsg(metricsResponse);
    }

    @Test
    public void modifyConnectionClosesAndRestartsClientActor() {
        final var mockClientProbe = actorSystemResource1.newTestProbe();
        final var commandSender = actorSystemResource1.newTestProbe();
        final var latestConnection = new AtomicReference<Connection>();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor,
                (connection, proxyActor, connectionActor, actorSystem, dittoHeaders, overwrites) -> {
                    latestConnection.set(connection);
                    return MockClientActor.props(mockClientProbe.ref());
                },
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();

        // create connection
        underTest.tell(createConnection, commandSender.ref());
        mockClientProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), enableConnectionLogs);
        mockClientProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), openConnection);
        commandSender.expectMsg(createConnectionResponse);

        final var clientActor = testProbe.watch(mockClientProbe.sender());

        // modify connection
        underTest.tell(modifyConnection, commandSender.ref());
        // modify triggers a CloseConnection
        mockClientProbe.expectMsg(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));

        // unsubscribe is called for topics of unmodified connection
        testProbe.expectTerminated(clientActor, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // and sends an open connection (if desired state is open). Since logging is enabled from creation
        // enabledConnectionLogs is also expected
        mockClientProbe.expectMsg(enableConnectionLogs);
        mockClientProbe.expectMsg(openConnection);

        // eventually the response is sent
        commandSender.expectMsg(modifyConnectionResponse);

        // modified connection contains an additional target for messages
        Awaitility.await().untilAtomic(latestConnection, CoreMatchers.is(modifyConnection.getConnection()));
    }

    @Test
    public void recoverOpenConnection() {
        final var mockClientProbe = actorSystemResource1.newTestProbe();
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(mockClientProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        // wait for open connection of initial creation
        mockClientProbe.expectMsg(enableConnectionLogs);
        mockClientProbe.expectMsg(openConnection);

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        final var recoveredMockClientProbe = actorSystemResource1.newTestProbe();
        underTest = Retry.untilSuccess(() -> TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(recoveredMockClientProbe.ref())));

        // connection is opened after recovery -> recovered client actor receives OpenConnection command
        recoveredMockClientProbe.expectMsg(openConnection.setDittoHeaders(DittoHeaders.empty()));

        // poll connection status until status is OPEN
        final var recoveredActor = underTest;
        Awaitility.await().untilAsserted(() -> {
            recoveredActor.tell(retrieveConnectionStatus, testProbe.ref());
            testProbe.expectMsg(retrieveConnectionStatusOpenResponse);
        });
    }

    @Test
    public void recoverModifiedConnection() {
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        // modify connection
        underTest.tell(modifyConnection, testProbe.ref());
        testProbe.expectMsg(modifyConnectionResponse);

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);

        // retrieve connection status
        underTest.tell(retrieveConnection, testProbe.ref());
        testProbe.expectMsg(retrieveModifiedConnectionResponse);
    }

    @Test
    public void recoverClosedConnection() {
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        // close connection
        underTest.tell(closeConnection, testProbe.ref());
        testProbe.expectMsg(closeConnectionResponse);

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = Retry.untilSuccess(() -> TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor));

        // retrieve connection status
        underTest.tell(retrieveConnectionStatus, testProbe.ref());
        final var response = testProbe.expectMsgClass(FiniteDuration.apply(20L, TimeUnit.SECONDS),
                RetrieveConnectionStatusResponse.class);

        assertThat((Object) response.getConnectionStatus()).isEqualTo(ConnectivityStatus.CLOSED);
        assertThat(response.getSourceStatus()).isEmpty();
        assertThat(response.getTargetStatus()).isEmpty();
        assertThat(response.getClientStatus())
                .hasSize(1)
                .first()
                .satisfies(clientStatus -> {
                    assertThat((CharSequence) clientStatus.getStatus()).isEqualTo(ConnectivityStatus.CLOSED);
                    assertThat(clientStatus.getStatusDetails())
                            .hasValue(String.format("[%s] connection is closed", BaseClientState.DISCONNECTED));
                });
    }

    @Test
    public void recoverDeletedConnection() {
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = Retry.untilSuccess(() -> TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor));

        // retrieve connection status
        underTest.tell(retrieveConnectionStatus, testProbe.ref());
        testProbe.expectMsg(connectionNotAccessibleException);
    }

    @Test
    public void exceptionDuringClientActorPropsCreation() {
        final var connectionActorProps = ConnectionPersistenceActor.props(TestConstants.createRandomConnectionId(),
                proxyActor,
                pubSubMediator,
                (connection, proxyActor, connectionActor, actorSystem, dittoHeaders, overwrites) -> {
                    throw ConnectionConfigurationInvalidException.newBuilder("validation failed...").build();
                },
                null,
                UsageBasedPriorityProvider::getInstance,
                ConfigFactory.empty());

        // create another actor because this it is stopped and we want to test if the child is terminated
        final var parent = actorSystemResource1.newTestKit();
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionActorRef = testProbe.watch(parent.childActorOf(connectionActorProps));

        // create connection
        connectionActorRef.tell(createConnection, parent.getRef());

        // expect ConnectionConfigurationInvalidException sent to parent
        final Exception exception = parent.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(exception).hasMessageContaining("validation failed...");

        // connection actor will stop after activity check.
    }

    @Test
    public void exceptionDueToCustomValidator() {
        final var connectionActorProps = ConnectionPersistenceActor.props(TestConstants.createRandomConnectionId(),
                proxyActor,
                pubSubMediator,
                mockClientActorPropsFactory,
                (command, connection) -> {
                    throw ConnectionUnavailableException.newBuilder(connectionId)
                            .dittoHeaders(command.getDittoHeaders())
                            .message("not valid")
                            .build();
                },
                UsageBasedPriorityProvider::getInstance,
                ConfigFactory.empty());

        // create another actor because we want to test if the child is terminated
        final var parent = actorSystemResource1.newTestKit();
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionActorRef = testProbe.watch(parent.childActorOf(connectionActorProps));

        // create connection
        connectionActorRef.tell(createConnection, parent.getRef());

        // expect ConnectionUnavailableException sent to parent
        final var exception = parent.expectMsgClass(ConnectionUnavailableException.class);
        assertThat(exception).hasMessageContaining("not valid");

        // do not expect passivation; it only happens for graceful shutdown.
    }

    @Test
    public void testResetConnectionMetrics() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // reset metrics
        underTest.tell(resetConnectionMetrics, testProbe.ref());
        clientActorProbe.expectMsg(resetConnectionMetrics);

        testProbe.expectMsg(ResetConnectionMetricsResponse.of(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void testConnectionActorRespondsToCleanupCommand() {
        final var probe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(probe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        probe.expectMsg(enableConnectionLogs);
        probe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // send cleanup command
        underTest.tell(CleanupPersistence.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(CleanupPersistenceResponse.success(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void enableConnectionLogs() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        //Close logging which are automatically enabled via create connection
        underTest.tell(LoggingExpired.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());

        // enable connection logs
        underTest.tell(enableConnectionLogs, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);

        testProbe.expectMsg(enableConnectionLogsResponse);
    }

    @Test
    public void retrieveLogsInClosedStateDoesNotStartClientActor() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createClosedConnection, testProbe.ref());
        testProbe.expectMsg(createClosedConnectionResponse);
        clientActorProbe.expectNoMessage();

        // retrieve logs
        underTest.tell(RetrieveConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        clientActorProbe.expectNoMessage();

        final var logsResponse = RetrieveConnectionLogsResponse.of(connectionId,
                Collections.emptyList(),
                null,
                null,
                dittoHeadersWithCorrelationId);
        testProbe.expectMsg(logsResponse);
    }

    @Test
    public void retrieveLogsIsAggregated() {
        final var now = Instant.now();
        final var innerResponse = RetrieveConnectionLogsResponse.of(connectionId,
                TestConstants.Monitoring.LOG_ENTRIES,
                now.minusSeconds(312),
                now.plusSeconds(123),
                dittoHeadersWithCorrelationId);

        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // retrieve logs
        final var retrieveConnectionLogs = RetrieveConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(retrieveConnectionLogs, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(retrieveConnectionLogs);

        // send answer to aggregator actor
        final var aggregatorActor = clientActorProbe.sender();
        clientActorProbe.send(aggregatorActor, innerResponse);
        testProbe.expectMsg(innerResponse);
    }

    @Test
    public void resetConnectionLogs() {
        final var resetConnectionLogs = ResetConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId);
        final var expectedResponse =
                ResetConnectionLogsResponse.of(connectionId, resetConnectionLogs.getDittoHeaders());

        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // reset logs
        underTest.tell(resetConnectionLogs, testProbe.ref());
        clientActorProbe.expectMsg(resetConnectionLogs);
        testProbe.expectMsg(expectedResponse);
    }

    @Test
    public void enabledConnectionLogsAreEnabledAgainAfterModify() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()),
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // Wait until connection is established
        // enable connection logs
        underTest.tell(enableConnectionLogs, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        testProbe.expectMsg(enableConnectionLogsResponse);

        // modify connection
        underTest.tell(modifyConnection, testProbe.ref());
        clientActorProbe.expectMsg(closeConnection);
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(modifyConnectionResponse);

        // expect the message twice, once for each client
        clientActorProbe.expectMsg(enableConnectionLogs);
    }

    @Test
    public void disabledConnectionLogsAreNotEnabledAfterModify() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                pubSubMediator,
                proxyActor,
                mockClientActorPropsFactory(clientActorProbe.ref()));
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        //Close logging which are automatically enabled via create connection
        underTest.tell(LoggingExpired.of(connectionId, DittoHeaders.empty()), testProbe.ref());

        // modify connection
        underTest.tell(modifyConnection, testProbe.ref());
        clientActorProbe.expectMsg(closeConnection);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(modifyConnectionResponse);
        clientActorProbe.expectNoMsg();
    }

    @Test
    public void forwardSearchCommands() {
        final var myConnectionId = ConnectionId.of(UUID.randomUUID().toString());
        final var gossipProbe = actorSystemResource1.newTestProbe("gossip");
        final var clientActorsProbe = actorSystemResource1.newTestProbe("clientActors");
        final var proxyActorProbe = actorSystemResource1.newTestProbe("proxyActor");
        final var pubSubMediatorProbe = actorSystemResource1.newTestProbe("pubSubMediator");
        final var testProbe = actorSystemResource1.newTestProbe();

        // Mock the client actors so that they forward all signals to clientActorsProbe with their own reference
        final ClientActorPropsFactory propsFactory = (a, b, connectionActor, aS, dittoHeaders, overwrites) ->
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
        final var connectionActorProps = Props.create(ConnectionPersistenceActor.class,
                () -> new ConnectionPersistenceActor(myConnectionId,
                        proxyActorProbe.ref(),
                        pubSubMediatorProbe.ref(),
                        propsFactory,
                        null,
                        UsageBasedPriorityProvider::getInstance,
                        Trilean.TRUE,
                        ConfigFactory.empty()));

        // GIVEN: connection persistence actor created with 2 client actors that are allowed to start on same node
        final var underTest = actorSystemResource1.newActor(connectionActorProps, myConnectionId.toString());
        underTest.tell(createClosedConnectionWith2Clients, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);
        underTest.tell(OpenConnection.of(myConnectionId, DittoHeaders.empty()), testProbe.ref());

        clientActorsProbe.fishForMessage(FiniteDuration.apply(5, TimeUnit.SECONDS),
                "CreateConnection",
                PartialFunction.fromFunction(msg -> isMessageSenderInstanceOf(msg, OpenConnection.class)));
        clientActorsProbe.reply(new Status.Success("connected"));
        testProbe.expectMsgClass(OpenConnectionResponse.class);

        // wait until gossip protocol completes
        gossipProbe.expectMsgClass(ActorRef.class);
        gossipProbe.expectMsgClass(ActorRef.class);

        // WHEN: 2 CreateSubscription commands are received
        // THEN: The 2 commands land in different client actors
        underTest.tell(CreateSubscription.of(DittoHeaders.empty()), testProbe.ref());
        underTest.tell(CreateSubscription.of(DittoHeaders.empty()), testProbe.ref());
        final var createSubscription1 = (WithSender<?>) clientActorsProbe.fishForMessage(
                FiniteDuration.apply(5, TimeUnit.SECONDS),
                "WithSender",
                PartialFunction.fromFunction(msg -> isMessageSenderInstanceOf(msg, CreateSubscription.class))
        );
        final var createSubscription2 = (WithSender<?>) clientActorsProbe.fishForMessage(
                FiniteDuration.apply(5, TimeUnit.SECONDS),
                "WithSender",
                PartialFunction.fromFunction(msg -> isMessageSenderInstanceOf(msg, CreateSubscription.class))
        );

        assertThat(createSubscription1.getSender()).isNotEqualTo(createSubscription2.getSender());
    }

    private static boolean isMessageSenderInstanceOf(final Object message, final Class<?> searchedClass) {
        final boolean result;
        if (message instanceof WithSender) {
            final var m = ((WithSender<?>) message).getMessage();
            result = searchedClass.isAssignableFrom(m.getClass());
        } else {
            result = false;
        }
        return result;
    }

    @Test
    public void retriesStartingClientActor() {
        final var failingClientActors =
                new FailingActorProvider(TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation());

        final var parent = actorSystemResource1.newTestKit();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                proxyActor,
                                pubSubMediator,
                                failingClientActors,
                                null,
                                UsageBasedPriorityProvider::getInstance,
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        Awaitility.await().until(failingClientActors::isActorSuccessfullyInitialized);
        assertThat(underTest.isTerminated()).isFalse();
    }

    @Test
    public void escalatesWhenClientActorFailsTooOften() {
        final var failingClientActors =
                new FailingActorProvider(1 + TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation());

        final var parent = actorSystemResource1.newTestKit();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                proxyActor,
                                pubSubMediator,
                                failingClientActors,
                                null,
                                UsageBasedPriorityProvider::getInstance,
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
        assertThat(failingClientActors.isActorSuccessfullyInitialized()).isFalse();
    }

    @Test
    public void deleteConnectionCommandEmitsEvent() {
        final var clientActorMock = actorSystemResource1.newTestProbe();
        final var pubSubTestProbe = actorSystemResource1.newTestProbe("mock-pubSub-mediator");
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                proxyActor,
                mockClientActorPropsFactory(clientActorMock.ref()),
                pubSubTestProbe.ref());
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);
        pubSubTestProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);

        pubSubTestProbe.fishForMessage(FiniteDuration.apply(5, TimeUnit.SECONDS),
                "connection deleted via pubSub",
                PartialFunction.fromFunction(msg -> msg instanceof DistributedPubSubMediator.Publish publish &&
                        publish.topic().equals(ConnectionDeleted.TYPE)));
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    /**
     * A {@link ClientActorPropsFactory} which provides an actor which will throw an exception {@code
     * retriesUntilSuccess} times in its constructor, before it starts up normally.
     */
    private static final class FailingActorProvider implements ClientActorPropsFactory {

        private final int retriesUntilSuccess;
        private int current = 0;
        private boolean initialized = false;

        public FailingActorProvider(final int retriesUntilSuccess) {
            this.retriesUntilSuccess = retriesUntilSuccess;
        }

        @Override
        public Props getActorPropsForType(final Connection connection,
                final ActorRef proxyActor,
                final ActorRef connectionActor,
                final ActorSystem system,
                final DittoHeaders dittoHeaders,
                final Config overwrites) {

            return Props.create(FailingActor.class, FailingActor::new);
        }

        private boolean isActorSuccessfullyInitialized() {
            return initialized;
        }

        private final class FailingActor extends AbstractActor {

            FailingActor() {
                if (current++ < retriesUntilSuccess) {
                    final var message =
                            MessageFormat.format("''FailingActor'' intentionally failing for {0} of {1} times",
                                    current, retriesUntilSuccess);
                    throw new IllegalStateException(message);
                }
            }

            @Override
            public void preStart() {
                initialized = true;
                System.out.println("'FailingActor' finally started without exception.");
            }

            @Override
            public void postStop() {
                System.out.println("'FailingActor' stopped.");
            }

            @Override
            public Receive createReceive() {
                return ReceiveBuilder.create().build();
            }

        }

    }

    private static ClientActorPropsFactory mockClientActorPropsFactory(final ActorRef actorRef) {
        return (connection, concierge, connectionActor, actorSystem, dittoHeaders, overwrites) ->
                MockClientActor.props(actorRef);
    }

}
