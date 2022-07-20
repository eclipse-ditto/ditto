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
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.INSTANT;

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
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
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
import org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.WithMockServers;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.serialization.Serialization;
import akka.testkit.TestProbe;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorTest extends WithMockServers {

    @Rule
    public final ActorSystemResource actorSystemResource1 = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory"
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource actorSystemResource2 = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory"
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource actorSystemResourceWithBlocklist = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                            "ditto.extensions.client-actor-props-factory",
                            "org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory",
                            "ditto.connectivity.connection.blocked-hostnames",
                            ConfigValueFactory.fromAnyRef("127.0.0.1")
                    ))
                    .withFallback(TestConstants.CONFIG)
    );

    @Rule
    public final ActorSystemResource exceptionalClientProviderSystemResource = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.ExceptionClientActorPropsFactory"
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource exceptionalCommandValidatorSystemResource = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory",
                    "ditto.extensions.custom-connectivity-command-interceptor-provider",
                    "org.eclipse.ditto.connectivity.service.messaging.ExceptionalCommandValidator"
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource failingClientProviderSystemResource = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.FailingActorProvider",
                    "failingRetries",
                    TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation()
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource tooManyFailingClientProviderSystemResource = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.FailingActorProvider", "failingRetries",
                    1 + TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation()
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource searchForwardingSystemResource = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory",
                    "org.eclipse.ditto.connectivity.service.messaging.SearchForwardingClientActorPropsFactory"
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private ActorRef pubSubMediator;
    private ActorRef commandForwarderActor;
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
        commandForwarderActor = actorSystemResource1.newActor(TestConstants.ProxyActorMock.props());
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
        final var testProbe = actorSystemResource1.newTestProbe();

        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);

        sendClientActorStartingCommand(underTest, testProbe, testConnection, actorSystemResource1, null, null);

        testProbe.expectMsg(Duration.create(65, TimeUnit.SECONDS),
                TestConnectionResponse.success(connectionId, "mock", testConnection.getDittoHeaders()));
    }

    @Test
    public void testConnectionCausingFailure() {
        final var testProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);

        sendClientActorStartingCommand(underTest, testProbe, testConnectionCausingFailure, actorSystemResource1, null,
                null);

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnectionCausingFailure.getDittoHeaders())
                .build());
    }

    @Test
    public void testConnectionCausingException() {
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();

        sendClientActorStartingCommand(underTest, testProbe, testConnectionCausingException, actorSystemResource1, null,
                null);

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnectionCausingException.getDittoHeaders())
                .build());
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        final var testProbe = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);

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
                commandForwarderActor,
                pubSubMediator);

        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorTestProbe.ref(),
                null);
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

    private void sendClientActorStartingCommand(final ActorRef underTest,
            final TestProbe testProbe,
            @Nullable final Command<?> command,
            final ActorSystemResource actorSystemResource,
            @Nullable final ActorRef delegate,
            @Nullable final ActorRef gossip) {

        final var pubSubMediator = DistributedPubSub.get(actorSystemResource.getActorSystem()).mediator();
        final var testProbe2 = actorSystemResource.newTestProbe();
        final var subscriptionMessage =
                DistPubSubAccess.subscribe("mockClientActor:subscribed", testProbe2.ref());
        pubSubMediator.tell(subscriptionMessage, testProbe2.ref());
        testProbe2.expectMsgClass(DistributedPubSubMediator.SubscribeAck.class);
        if (null != command) {
            underTest.tell(command, testProbe.ref());
        }
        testProbe2.expectMsgClass(FiniteDuration.apply(5, TimeUnit.SECONDS),
                MockClientActorPropsFactory.MockClientActor.Subscribed.class);
        publishChange(new MockClientActorPropsFactory.MockClientActor.ChangeActorRef(
                delegate != null ? Serialization.serializedActorPath(delegate) : null,
                gossip != null ? Serialization.serializedActorPath(gossip) : null), testProbe2.ref(), pubSubMediator);
        testProbe2.fishForMessage(scala.concurrent.duration.Duration.apply(3, TimeUnit.SECONDS),
                "ActorRefChanged",
                PartialFunction.fromFunction(
                        MockClientActorPropsFactory.MockClientActor.ActorRefChanged.class::isInstance));
    }


    private void publishChange(final MockClientActorPropsFactory.MockClientActor.ChangeActorRef changeActorRef,
            final ActorRef testProbe,
            final ActorRef pubSubMediator) {

        final var publish = DistPubSubAccess.publish("mockClientActor:change", changeActorRef);
        pubSubMediator.tell(publish, testProbe);
    }

    @Test
    public void deleteConnectionUpdatesSubscriptionsAndClosesConnection() {
        final var clientActorTestProbe = actorSystemResource1.newTestProbe();
        final var pubSubTestProbe = actorSystemResource1.newTestProbe("mock-pubSub-mediator");
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubTestProbe.ref());
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorTestProbe.ref(),
                null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create closed connection
        underTest.tell(createClosedConnectionWith2Clients, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);

        // open connection: only local client actor is asked for a response.
        sendClientActorStartingCommand(underTest, testProbe, openConnection, actorSystemResource1,
                clientActorProbe.ref(), gossipProbe.ref());
        underTest.tell(gossipProbe.expectMsgClass(FiniteDuration.apply(5, TimeUnit.SECONDS), ActorRef.class),
                ActorRef.noSender());
        //sendClientActorStartingCommand(underTest, testProbe, null, actorSystemResource2, gossipProbe.ref(),
        //        testProbe.ref());

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
        testProbe.expectTerminated(underTest, FiniteDuration.apply(5, TimeUnit.SECONDS));
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorMock.ref(), null);
        clientActorMock.expectMsg(enableConnectionLogs);
        clientActorMock.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        clientActorMock.expectMsg(closeConnection);
        testProbe.expectMsg(deleteConnectionResponse);

        // create connection again (while ConnectionActor is in deleted state)
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorMock.ref(), null);
        testProbe.expectMsg(createConnectionResponse);
        clientActorMock.expectMsg(enableConnectionLogs);
        clientActorMock.expectMsg(openConnection);
    }

    @Test
    public void openConnectionAfterDeletedFails() {
        final var clientActorMock = actorSystemResource1.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorMock.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
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

                false,

                entry(createClosedConnectionWithUnknownHost, ConnectionPersistenceActorTest::assertHostInvalid));
    }

    @Test
    public void testConnectionWithUnknownHost() {
        final var testConnectionWithUnknownHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://invalid:1234").build(),
                dittoHeadersWithCorrelationId
        );

        sendCommandWithEnabledBlocklist(

                false,

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

                false,

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

        sendCommandWithEnabledBlocklist(false,
                entry(createClosedConnectionWithBlockedHost, ConnectionPersistenceActorTest::assertHostBlocked));
    }

    @Test
    public void testConnectionWithBlockedHost() {
        final var testConnectionWithUnknownHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://localhost:1234").build(),
                dittoHeadersWithCorrelationId);

        sendCommandWithEnabledBlocklist(false,
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

                false,

                // create is successful
                entry(createClosedConnectionWithValidHost, ConnectionPersistenceActorTest::assertConnectionCreated),

                // modify fails because the new host is invalid
                entry(modifyClosedConnectionWithBlockedHost, ConnectionPersistenceActorTest::assertHostBlocked)
        );
    }

    @SafeVarargs
    private void sendCommandWithEnabledBlocklist(
            final boolean firstCommandStartsClientActor,
            final Map.Entry<ConnectivityCommand<?>, Consumer<Object>>... commands) {

        final var pubSubMediator = DistributedPubSub.get(actorSystemResourceWithBlocklist.getActorSystem()).mediator();

        final var clientActorProbe = actorSystemResourceWithBlocklist.newTestProbe();
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResourceWithBlocklist.getActorSystem(),
                pubSubMediator,
                commandForwarderActor);
        final var testProbe = actorSystemResourceWithBlocklist.newTestProbe();
        testProbe.watch(underTest);

        for (final var command : commands) {
            if (firstCommandStartsClientActor && command.equals(commands[0])) {
                sendClientActorStartingCommand(underTest, testProbe, command.getKey(), actorSystemResourceWithBlocklist,
                        clientActorProbe.ref(), null);
            } else {
                underTest.tell(command.getKey(), testProbe.ref());
            }
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();

        // create connection
        sendClientActorStartingCommand(underTest, commandSender, createConnection, actorSystemResource1,
                mockClientProbe.ref(),
                null);
        mockClientProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), enableConnectionLogs);
        mockClientProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), openConnection);
        commandSender.expectMsg(createConnectionResponse);

        final var clientActor = testProbe.watch(mockClientProbe.sender());

        // modify connection | Implicitly validates the restart by waiting for pubsub subscribe from client actor.
        sendClientActorStartingCommand(underTest, commandSender, modifyConnection, actorSystemResource1,
                mockClientProbe.ref(),
                null);
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
    }

    @Test
    public void recoverOpenConnection() throws InterruptedException {
        final var mockClientProbe = actorSystemResource1.newTestProbe();
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                mockClientProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator));
        underTest.tell(PingCommand.of(connectionId, null, null), null);

        Awaitility.await().untilAsserted(() -> {
            publishChange(new MockClientActorPropsFactory.MockClientActor.ChangeActorRef(
                            Serialization.serializedActorPath(recoveredMockClientProbe.ref()), null), testProbe.ref(),
                    pubSubMediator);
            testProbe.fishForMessage(scala.concurrent.duration.Duration.apply(3, TimeUnit.SECONDS),
                    "ActorRefChanged",
                    PartialFunction.fromFunction(
                            MockClientActorPropsFactory.MockClientActor.ActorRefChanged.class::isInstance));
        });
        recoveredMockClientProbe.expectMsg(FiniteDuration.apply(5, TimeUnit.SECONDS),
                openConnection.setDittoHeaders(DittoHeaders.empty()));

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
                commandForwarderActor);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1, null, null);
        testProbe.expectMsg(createConnectionResponse);

        // modify connection
        sendClientActorStartingCommand(underTest, testProbe, modifyConnection, actorSystemResource1, null, null);
        testProbe.expectMsg(modifyConnectionResponse);

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);

        // retrieve connection status
        underTest.tell(retrieveConnection, testProbe.ref());
        testProbe.expectMsg(retrieveModifiedConnectionResponse);
    }

    @Test
    public void recoverClosedConnection() {
        var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1, null, null);
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
                commandForwarderActor,
                pubSubMediator));

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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1, null, null);
        testProbe.expectMsg(createConnectionResponse);

        // delete connection
        underTest.tell(deleteConnection, testProbe.ref());
        testProbe.expectMsg(deleteConnectionResponse);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = Retry.untilSuccess(() -> TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediator));

        // retrieve connection status
        underTest.tell(retrieveConnectionStatus, testProbe.ref());
        testProbe.expectMsg(connectionNotAccessibleException);
    }

    @Test
    public void exceptionDuringClientActorPropsCreation() {

        final var connectionActorProps = ConnectionPersistenceActor.props(
                TestConstants.createRandomConnectionId(), commandForwarderActor, pubSubMediator, ConfigFactory.empty()
        );

        // create another actor because this it is stopped and we want to test if the child is terminated
        final var parent = exceptionalClientProviderSystemResource.newTestKit();
        final var testProbe = exceptionalClientProviderSystemResource.newTestProbe();
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
                commandForwarderActor,
                pubSubMediator,
                ConfigFactory.empty());

        // create another actor because we want to test if the child is terminated
        final var parent = exceptionalCommandValidatorSystemResource.newTestKit();
        final var testProbe = exceptionalCommandValidatorSystemResource.newTestProbe();
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1, probe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        // Wait until connection is established
        // enable connection logs
        underTest.tell(enableConnectionLogs, testProbe.ref());
        clientActorProbe.expectMsg(enableConnectionLogs);
        testProbe.expectMsg(enableConnectionLogsResponse);

        // modify connection
        sendClientActorStartingCommand(underTest, testProbe, modifyConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
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
                commandForwarderActor,
                pubSubMediator);
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
        clientActorProbe.expectMsg(enableConnectionLogs);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(createConnectionResponse);

        //Close logging which are automatically enabled via create connection
        underTest.tell(LoggingExpired.of(connectionId, DittoHeaders.empty()), testProbe.ref());

        // modify connection
        sendClientActorStartingCommand(underTest, testProbe, modifyConnection, actorSystemResource1,
                clientActorProbe.ref(), null);
        clientActorProbe.expectMsg(closeConnection);
        clientActorProbe.expectMsg(openConnection);
        testProbe.expectMsg(modifyConnectionResponse);
        clientActorProbe.expectNoMsg();
    }

    @Test
    public void forwardSearchCommands() {
        final var myConnectionId = ConnectionId.of(UUID.randomUUID().toString());
        final var gossipProbe = searchForwardingSystemResource.newTestProbe("gossip");
        final var clientActorsProbe = searchForwardingSystemResource.newTestProbe("clientActors");
        final var proxyActorProbe = searchForwardingSystemResource.newTestProbe("proxyActor");
        final var pubSubMediatorProbe = searchForwardingSystemResource.newTestProbe("pubSubMediator");
        final var testProbe = searchForwardingSystemResource.newTestProbe();
        final var connectionActorProps = Props.create(ConnectionPersistenceActor.class,
                () -> new ConnectionPersistenceActor(myConnectionId,
                        proxyActorProbe.ref(),
                        pubSubMediatorProbe.ref(),
                        Trilean.TRUE,
                        ConfigFactory.empty()));

        // GIVEN: connection persistence actor created with 2 client actors that are allowed to start on same node
        final var underTest = searchForwardingSystemResource.newActor(connectionActorProps, myConnectionId.toString());
        underTest.tell(createClosedConnectionWith2Clients, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);
        sendClientActorStartingCommand(underTest, testProbe, OpenConnection.of(myConnectionId, DittoHeaders.empty()),
                searchForwardingSystemResource, clientActorsProbe.ref(), gossipProbe.ref());

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

        final var parent = failingClientProviderSystemResource.newTestKit();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                commandForwarderActor,
                                pubSubMediator,
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = failingClientProviderSystemResource.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        assertThat(underTest.isTerminated()).isFalse();
    }

    @Test
    public void escalatesWhenClientActorFailsTooOften() {
        final var parent = tooManyFailingClientProviderSystemResource.newTestKit();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                commandForwarderActor,
                                pubSubMediator,
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = tooManyFailingClientProviderSystemResource.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsg(createConnectionResponse);

        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void deleteConnectionCommandEmitsEvent() {
        final var clientActorMock = actorSystemResource1.newTestProbe();
        final var pubSubTestProbe = actorSystemResource1.newTestProbe("mock-pubSub-mediator");
        final var underTest = TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubTestProbe.ref());
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        sendClientActorStartingCommand(underTest, testProbe, createConnection, actorSystemResource1,
                clientActorMock.ref(), null);
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

}
