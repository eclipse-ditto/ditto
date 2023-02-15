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
import static org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory.gossipProbe;
import static org.eclipse.ditto.connectivity.service.messaging.MockClientActorPropsFactory.mockClientActorProbe;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.INSTANT;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
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
import org.eclipse.ditto.connectivity.service.messaging.MockCommandValidator;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.WithMockServers;
import org.eclipse.ditto.connectivity.service.messaging.hono.DefaultHonoConnectionFactoryTest;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActor;
import akka.testkit.TestProbe;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorTest extends WithMockServers {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @Rule
    public final ActorSystemResource actorSystemResource1 = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory.extension-class",
                    MockClientActorPropsFactory.class.getName(),
                    "ditto.connectivity.connection.allowed-hostnames",
                    ConfigValueFactory.fromAnyRef("127.0.0.1,hono-endpoint"),
                    "ditto.connectivity.connection.blocked-hostnames",
                    ConfigValueFactory.fromAnyRef("127.0.0.2"),
                    "ditto.extensions.custom-connectivity-command-interceptor-provider",
                    MockCommandValidator.class.getName(),
                    "ditto.extensions.hono-connection-factory",
                    ConfigValueFactory.fromAnyRef(
                            "org.eclipse.ditto.connectivity.service.messaging.hono.DefaultHonoConnectionFactory"),
                    "ditto.connectivity.hono.base-uri", ConfigValueFactory.fromAnyRef("tcp://localhost:9922"),
                    "ditto.connectivity.hono.validate-certificates", ConfigValueFactory.fromAnyRef("false"),
                    "ditto.connectivity.hono.sasl-mechanism", ConfigValueFactory.fromAnyRef("PLAIN"),
                    "ditto.connectivity.hono.bootstrap-servers",
                    ConfigValueFactory.fromAnyRef("tcp://server1:port1,tcp://server2:port2,tcp://server3:port3")
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final ActorSystemResource actorSystemResource2 = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.of(
                    "ditto.extensions.client-actor-props-factory.extension-class",
                    MockClientActorPropsFactory.class.getName(),
                    "ditto.connectivity.connection.allowed-hostnames",
                    ConfigValueFactory.fromAnyRef("127.0.0.1"),
                    "ditto.connectivity.connection.blocked-hostnames",
                    ConfigValueFactory.fromAnyRef("127.0.0.2"),
                    "ditto.extensions.custom-connectivity-command-interceptor-provider",
                    MockCommandValidator.class.getName()
            )).withFallback(TestConstants.CONFIG));

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private TestProbe pubSubMediatorProbe;
    private ActorRef commandForwarderActor;
    private DittoHeaders dittoHeadersWithCorrelationId;
    private ConnectionId connectionId;
    private Connection connection;
    private Connection closedConnectionWith2Clients;
    private Connection closedConnection;

    @Before
    public void init() {
        final var pubSubMediator = DistributedPubSub.get(actorSystemResource1.getActorSystem()).mediator();
        pubSubMediatorProbe = TestProbe.apply(actorSystemResource1.getActorSystem());
        pubSubMediatorProbe.setAutoPilot(new TestActor.AutoPilot() {
            @Override
            public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                pubSubMediator.tell(msg, sender);
                return keepRunning();
            }
        });
        commandForwarderActor = actorSystemResource1.newActor(TestConstants.ProxyActorMock.props());
        connectionId = TestConstants.createRandomConnectionId();
        connection = TestConstants.createConnection(connectionId);
        closedConnectionWith2Clients =
                connection.toBuilder().clientCount(2).connectionStatus(ConnectivityStatus.CLOSED).build();
        closedConnection = TestConstants.createConnection(connectionId,
                ConnectivityStatus.CLOSED,
                TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);

        dittoHeadersWithCorrelationId =
                DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
        mockClientActorProbe = actorSystemResource1.newTestProbe();
        gossipProbe = actorSystemResource1.newTestProbe();
    }

    @Test
    public void testConnection() {
        //GIVEN
        final var connection = TestConstants.createConnection(connectionId);
        final var testConnection = TestConnection.of(connection, dittoHeadersWithCorrelationId);
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionSupervisorActor = createSupervisor();

        //WHEN
        connectionSupervisorActor.tell(testConnection, testProbe.ref());

        //THEN
        final var testConnectionWithDryRunHeader = TestConnection.of(connection, dittoHeadersWithCorrelationId
                .toBuilder()
                .dryRun(true)
                .build());
        expectMockClientActorMessage(testConnectionWithDryRunHeader);
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(TestConnectionResponse.success(connectionId, "mock", testConnection.getDittoHeaders()));
    }

    @Test
    public void testConnectionTypeHono() throws IOException {
        //GIVEN
        final var honoConnection = generateConnectionObjectFromJsonFile("hono-connection-custom-test.json", null)
                .toBuilder()
                .id(connectionId)
                .build();
        final var expectedHonoConnection = generateConnectionObjectFromJsonFile("hono-connection-custom-expected.json", connectionId)
                .toBuilder()
                .id(connectionId)
                .build();
        final var testConnection = TestConnection.of(honoConnection, dittoHeadersWithCorrelationId);
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionSupervisorActor = createSupervisor();

        //WHEN
        connectionSupervisorActor.tell(testConnection, testProbe.ref());

        //THEN
        final var testConnectionWithDryRunHeader = TestConnection.of(expectedHonoConnection,
                dittoHeadersWithCorrelationId
                        .toBuilder()
                        .dryRun(true)
                        .build());
        expectMockClientActorMessage(testConnectionWithDryRunHeader);
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(
                TestConnectionResponse.success(honoConnection.getId(), "mock", testConnection.getDittoHeaders()));
    }

    @Test
    public void testRestartByConnectionType() throws IOException {
        // GIVEN
        final var honoConnection = generateConnectionObjectFromJsonFile("hono-connection-custom-test.json", null);
        mockClientActorProbe.setAutoPilot(new TestActor.AutoPilot() {
            @Override
            public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                if (msg instanceof WithSender<?> withSender && withSender.getMessage() instanceof OpenConnection) {
                    sender.tell(new Status.Success("connected"), mockClientActorProbe.ref());
                }
                return keepRunning();
            }
        });
        final var testProbe = actorSystemResource1.newTestProbe();

        final var connectionActorProps = Props.create(ConnectionPersistenceActor.class,
                () -> new ConnectionPersistenceActor(connectionId,
                        Mockito.mock(MongoReadJournal.class),
                        commandForwarderActor,
                        pubSubMediatorProbe.ref(),
                        Trilean.TRUE,
                        ConfigFactory.empty()));

        final var underTest = actorSystemResource1.newActor(connectionActorProps, connectionId.toString());
        final CreateConnection createConnection = createConnection(
                honoConnection.toBuilder().id(connectionId).build()
        );
        underTest.tell(createConnection, testProbe.ref());
        testProbe.expectMsgClass(FiniteDuration.apply(20, "s"), CreateConnectionResponse.class);

        Arrays.stream(ConnectionType.values()).forEach(connectionType -> {
            // WHEN
            underTest.tell(new ConnectionSupervisorActor.RestartByConnectionType(connectionType), testProbe.ref());

            // THEN
            if (connectionType == honoConnection.getConnectionType()) {
                testProbe.expectMsg(ConnectionSupervisorActor.RestartConnection.of(null));
            } else {
                testProbe.expectNoMsg();
            }
        });
    }

    private static Connection generateConnectionObjectFromJsonFile(final String fileName,
            @Nullable ConnectionId connectionId) throws IOException {
        final var testClassLoader = DefaultHonoConnectionFactoryTest.class.getClassLoader();
        try (final var connectionJsonFileStreamReader = new InputStreamReader(
                testClassLoader.getResourceAsStream(fileName)
        )) {
            JsonObject jsonObject = JsonFactory.readFrom(connectionJsonFileStreamReader).asObject();
            var connId = jsonObject.getValue("id");
            if (connectionId != null && connId.isPresent()) {
                var jsonString = jsonObject.formatAsString().replace(connId.get().asString(), connectionId);
                jsonObject = JsonFactory.readFrom(jsonString).asObject();
            }
            return ConnectivityModelFactory.connectionFromJson(jsonObject);
        }
    }

    @Test
    public void testConnectionCausingFailure() {
        //GIVEN
        final var connection = TestConstants.createConnection(connectionId);
        final var testConnection = TestConnection.of(connection, dittoHeadersWithCorrelationId);
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionSupervisorActor = createSupervisor();

        //WHEN
        connectionSupervisorActor.tell(testConnection, testProbe.ref());

        //THEN
        final var testConnectionWithDryRunHeader = TestConnection.of(connection, dittoHeadersWithCorrelationId
                .toBuilder()
                .dryRun(true)
                .build());
        expectMockClientActorMessage(testConnectionWithDryRunHeader);
        mockClientActorProbe.reply(new Status.Failure(ConnectionIdInvalidException.newBuilder("invalid").build()));

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnection.getDittoHeaders())
                .build());
    }

    @Test
    public void testConnectionCausingException() {
        //GIVEN
        final var connection = TestConstants.createConnection(connectionId);
        final var testConnection = TestConnection.of(connection, dittoHeadersWithCorrelationId);
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionSupervisorActor = createSupervisor();

        //WHEN
        connectionSupervisorActor.tell(testConnection, testProbe.ref());

        //THEN
        final var testConnectionWithDryRunHeader = TestConnection.of(connection, dittoHeadersWithCorrelationId
                .toBuilder()
                .dryRun(true)
                .build());
        expectMockClientActorMessage(testConnectionWithDryRunHeader);
        mockClientActorProbe.reply(ConnectionIdInvalidException.newBuilder("invalid").build());

        testProbe.expectMsg(ConnectionIdInvalidException.newBuilder("invalid")
                .dittoHeaders(testConnection.getDittoHeaders())
                .build());
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        final var testProbe = actorSystemResource1.newTestProbe();
        final var underTest = createSupervisor();

        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());

        testProbe.expectMsg(ConnectionNotAccessibleException.newBuilder(connectionId)
                .dittoHeaders(dittoHeadersWithCorrelationId)
                .build());
    }

    @Test
    public void manageConnection() {
        final var underTest = createSupervisor();

        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // close connection
        final CloseConnection closeConnection = CloseConnection.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(closeConnection, testProbe.ref());
        expectMockClientActorMessage(closeConnection);
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(CloseConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.expectNoMessage();
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void deleteConnectionUpdatesSubscriptionsAndClosesConnection() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void manageConnectionWith2Clients() throws Exception {
        startSecondActorSystemAndJoinCluster();
        ConnectionPubSub.get(actorSystemResource1.getActorSystem())
                .subscribe(connectionId, mockClientActorProbe.ref(), false)
                .toCompletableFuture()
                .join();
        mockClientActorProbe.setAutoPilot(new TestActor.AutoPilot() {
            @Override
            public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                if (msg instanceof WithSender<?> withSender && withSender.getMessage() instanceof OpenConnection) {
                    sender.tell(new Status.Success("connected"), mockClientActorProbe.ref());
                }
                return keepRunning();
            }
        });
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create closed connection
        underTest.tell(createConnection(closedConnectionWith2Clients), testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);

        // open connection: only local client actor is asked for a response.
        underTest.tell(OpenConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        gossipProbe.expectMsgClass(ActorRef.class);
        gossipProbe.expectMsgClass(ActorRef.class);

        // handled by autopilot and response is received by sender
        testProbe.expectMsgClass(OpenConnectionResponse.class);

        // forward signal once
        final CreateSubscription createSubscription = CreateSubscription.of(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build());
        underTest.tell(createSubscription, testProbe.ref());
        mockClientActorProbe.fishForMessage(
                FiniteDuration.apply(30, TimeUnit.SECONDS),
                "WithSender",
                PartialFunction.fromFunction(msg -> msg instanceof CreateSubscription)
        );

        // close connection: at least 1 client actor gets the command; the other may or may not be started.
        final CloseConnection closeConnection = CloseConnection.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(closeConnection, testProbe.ref());
        mockClientActorProbe.fishForMessage(
                FiniteDuration.apply(30, TimeUnit.SECONDS),
                "WithSender",
                PartialFunction.fromFunction(msg -> isMessageSenderInstanceOf(msg, CloseConnection.class))
        );
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(CloseConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
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
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // create connection again (while ConnectionActor is in deleted state)
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);
    }

    @Test
    public void openConnectionAfterDeletedFails() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // open connection should fail
        underTest.tell(OpenConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(ConnectionNotAccessibleException.newBuilder(connectionId).build());
    }

    @Test
    public void createConnectionInClosedState() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(closedConnection), testProbe.ref());
        expectCreateConnectionResponse(testProbe, closedConnection);

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
    }

    @Test
    public void createClosedConnectionWithUnknownHost() {
        final var createClosedConnectionWithUnknownHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://invalid:1234").build(),
                dittoHeadersWithCorrelationId
        );

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createClosedConnectionWithUnknownHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host 'invalid' is invalid");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
    }

    @Test
    public void testConnectionWithUnknownHost() {
        final var testConnectionWithUnknownHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://invalid:1234").build(),
                dittoHeadersWithCorrelationId
        );

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(testConnectionWithUnknownHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host 'invalid' is invalid");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
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

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createClosedConnectionWithValidHost, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);

        underTest.tell(modifyClosedConnectionWithInvalidHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host 'invalid' is invalid");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
    }

    @Test
    public void createClosedConnectionWithBlockedHost() {
        final var createClosedConnectionWithBlockedHost = CreateConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://127.0.0.2:1234").build(),
                dittoHeadersWithCorrelationId
        );

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createClosedConnectionWithBlockedHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host '127.0.0.2' may not be used for the connection");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
    }

    @Test
    public void testConnectionWithBlockedHost() {
        final var testConnectionWithBlockedHost = TestConnection.of(
                ConnectivityModelFactory.newConnectionBuilder(closedConnection).uri("amqp://127.0.0.2:1234").build(),
                dittoHeadersWithCorrelationId);

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(testConnectionWithBlockedHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host '127.0.0.2' may not be used for the connection");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
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
                        .uri("amqp://127.0.0.2:1234")
                        .build(),
                dittoHeadersWithCorrelationId
        );

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        underTest.tell(createClosedConnectionWithValidHost, testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);

        underTest.tell(modifyClosedConnectionWithBlockedHost, testProbe.ref());
        final var connectionConfigurationInvalidException =
                testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(connectionConfigurationInvalidException)
                .hasMessageContaining("The configured host '127.0.0.2' may not be used for the connection");

        // assert that client actor is not called for closed connection
        mockClientActorProbe.expectNoMessage();
    }

    @Test
    public void modifyConnectionInClosedState() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // close connection
        underTest.tell(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(CloseConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // modify connection
        underTest.tell(ModifyConnection.of(closedConnection, dittoHeadersWithCorrelationId), testProbe.ref());

        // client actor is not informed about modification as it is not started
        mockClientActorProbe.expectNoMessage(FiniteDuration.apply(3, TimeUnit.SECONDS));
        testProbe.expectMsg(ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void retrieveMetricsInClosedStateDoesNotStartClientActor() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();

        // create connection
        underTest.tell(createConnection(closedConnection), testProbe.ref());
        expectCreateConnectionResponse(testProbe, closedConnection);
        mockClientActorProbe.expectNoMessage();

        // retrieve metrics
        underTest.tell(RetrieveConnectionMetrics.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        mockClientActorProbe.expectNoMessage();

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
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // modify connection | Implicitly validates the restart by waiting for pubsub subscribe from client actor.
        underTest.tell(ModifyConnection.of(connection, dittoHeadersWithCorrelationId), testProbe.ref());
        // modify triggers a CloseConnection
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));

        // and sends an open connection (if desired state is open). Since logging is enabled from creation
        // enabledConnectionLogs is also expected
        simulateSuccessfulOpenConnectionInClientActor();

        // eventually the response is sent
        testProbe.expectMsg(ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void recoverOpenConnection() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        final var recoveredActor = Retry.untilSuccess(this::createSupervisor);
        recoveredActor.tell(PingCommand.of(connectionId, null, null), null);

        expectMockClientActorMessage(OpenConnection.of(connectionId, DittoHeaders.empty()));

        Awaitility.await().untilAsserted(() -> {
            recoveredActor.tell(RetrieveConnectionStatus.of(connectionId, dittoHeadersWithCorrelationId),
                    testProbe.ref());
            expectMockClientActorMessage(RetrieveConnectionStatus.class);
            final ActorRef lastSender = mockClientActorProbe.lastSender();
            lastSender.tell(ConnectivityModelFactory.newClientStatus("client1",
                            ConnectivityStatus.OPEN, RecoveryStatus.SUCCEEDED, "connection is open",
                            TestConstants.INSTANT),
                    mockClientActorProbe.ref());

            // simulate consumer and pusblisher actor response
            lastSender.tell(ConnectivityModelFactory.newSourceStatus("client1",
                            ConnectivityStatus.OPEN, "source1", "consumer started"),
                    mockClientActorProbe.ref());
            lastSender.tell(ConnectivityModelFactory.newSourceStatus("client1",
                            ConnectivityStatus.OPEN, "source2", "consumer started"),
                    mockClientActorProbe.ref());
            lastSender.tell(ConnectivityModelFactory.newTargetStatus("client1",
                            ConnectivityStatus.OPEN, "target1", "publisher started"),
                    mockClientActorProbe.ref());
            lastSender.tell(ConnectivityModelFactory.newTargetStatus("client1",
                            ConnectivityStatus.OPEN, "target2", "publisher started"),
                    mockClientActorProbe.ref());
            lastSender.tell(ConnectivityModelFactory.newTargetStatus("client1",
                            ConnectivityStatus.OPEN, "target3", "publisher started"),
                    mockClientActorProbe.ref());
            testProbe.expectMsg(retrieveConnectionStatusResponse());
        });
    }

    @Test
    public void recoverModifiedConnection() {
        var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // modify connection
        final var modifiedConnection = ConnectivityModelFactory.newConnectionBuilder(connection)
                .failoverEnabled(false)
                .targets(Collections.singletonList(TestConstants.Targets.MESSAGE_TARGET))
                .build();
        underTest.tell(ModifyConnection.of(modifiedConnection, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.class);
        mockClientActorProbe.reply(new Status.Success("mock"));
        simulateSuccessfulOpenConnectionInClientActor();
        testProbe.expectMsg(ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = createSupervisor();

        // retrieve connection status
        underTest.tell(RetrieveConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(RetrieveConnectionResponse.of(modifiedConnection.toJson(),
                dittoHeadersWithCorrelationId.toBuilder()
                        .eTag(EntityTag.fromString("\"rev:2\""))
                        .build()
        ));
    }

    @Test
    public void recoverClosedConnection() {
        var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // close connection
        underTest.tell(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.class);
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(CloseConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // stop actor
        actorSystemResource1.stopActor(underTest);
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = Retry.untilSuccess(this::createSupervisor);

        // retrieve connection status
        underTest.tell(RetrieveConnectionStatus.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
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
        var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.class);
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));

        // recover actor
        underTest = Retry.untilSuccess(this::createSupervisor);

        // retrieve connection status
        underTest.tell(RetrieveConnectionStatus.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(ConnectionNotAccessibleException.newBuilder(connectionId).build());
    }

    @Test
    public void exceptionDuringClientActorPropsCreation() {

        final var testProbe = actorSystemResource1.newTestProbe();
        final var supervisor = actorSystemResource1.newTestProbe();

        final var connectionActorProps = ConnectionPersistenceActor.props(
                connectionId,
                Mockito.mock(MongoReadJournal.class),
                commandForwarderActor,
                pubSubMediatorProbe.ref(),
                ConfigFactory.empty());
        final var connectionActorRef = supervisor.childActorOf(connectionActorProps, "connection");

        // create connection
        final CreateConnection createConnection = createConnection();
        final var headersIndicatingException = createConnection.getDittoHeaders().toBuilder()
                .putHeader("should-throw-exception", "true")
                .build();
        connectionActorRef.tell(createConnection.setDittoHeaders(headersIndicatingException), testProbe.ref());

        // expect ConnectionConfigurationInvalidException as response
        final Exception exception = testProbe.expectMsgClass(ConnectionConfigurationInvalidException.class);
        assertThat(exception).hasMessageContaining("validation failed...");
        // supervisor gets passivate indicator because of internal failure
        supervisor.expectMsg(AbstractPersistenceSupervisor.Control.PASSIVATE);

        // connection actor will stop after activity check.
    }

    @Test
    public void exceptionDueToCustomValidator() {
        final var connectionActorProps = ConnectionPersistenceActor.props(connectionId,
                Mockito.mock(MongoReadJournal.class),
                commandForwarderActor,
                pubSubMediatorProbe.ref(),
                ConfigFactory.empty());

        final var testProbe = actorSystemResource1.newTestProbe();
        final var parent = actorSystemResource1.newTestProbe();
        final var connectionActorRef = parent.childActorOf(connectionActorProps);

        // create connection
        final CreateConnection createConnection = createConnection();
        final var headersIndicatingException = createConnection.getDittoHeaders().toBuilder()
                .putHeader("validator-should-throw-exception", "true")
                .build();
        connectionActorRef.tell(createConnection.setDittoHeaders(headersIndicatingException), testProbe.ref());

        // expect ConnectionUnavailableException as response
        final var exception = testProbe.expectMsgClass(ConnectionUnavailableException.class);
        assertThat(exception).hasMessageContaining("not valid");
        // supervisor gets passivate indicator because of internal failure
        parent.expectMsg(AbstractPersistenceSupervisor.Control.PASSIVATE);

        // do not expect passivation; it only happens for graceful shutdown.
    }

    @Test
    public void testResetConnectionMetrics() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // reset metrics
        final var resetConnectionMetrics = ResetConnectionMetrics.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(resetConnectionMetrics, testProbe.ref());
        expectMockClientActorMessage(resetConnectionMetrics);

        testProbe.expectMsg(ResetConnectionMetricsResponse.of(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void testConnectionActorRespondsToCleanupCommand() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // send cleanup command
        underTest.tell(CleanupPersistence.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(CleanupPersistenceResponse.success(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void enableConnectionLogs() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        //Close logging which are automatically enabled via create connection
        underTest.tell(LoggingExpired.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());

        // enable connection logs
        final var enableConnectionLogs = EnableConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(enableConnectionLogs, testProbe.ref());
        expectMockClientActorMessage(enableConnectionLogs);

        testProbe.expectMsg(EnableConnectionLogsResponse.of(connectionId, dittoHeadersWithCorrelationId));
    }

    @Test
    public void retrieveLogsInClosedStateDoesNotStartClientActor() {
        final var clientActorProbe = actorSystemResource1.newTestProbe();
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(closedConnection), testProbe.ref());
        expectCreateConnectionResponse(testProbe, closedConnection);
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

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // retrieve logs
        final var retrieveConnectionLogs = RetrieveConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId);
        underTest.tell(retrieveConnectionLogs, testProbe.ref());
        expectMockClientActorMessage(EnableConnectionLogs.of(connectionId, DittoHeaders.empty()));
        expectMockClientActorMessage(retrieveConnectionLogs);

        // send answer to aggregator actor
        final var aggregatorActor = mockClientActorProbe.sender();
        mockClientActorProbe.send(aggregatorActor, innerResponse);
        testProbe.expectMsg(innerResponse);
    }

    @Test
    public void resetConnectionLogs() {
        final var resetConnectionLogs = ResetConnectionLogs.of(connectionId, dittoHeadersWithCorrelationId);
        final var expectedResponse =
                ResetConnectionLogsResponse.of(connectionId, resetConnectionLogs.getDittoHeaders());

        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // reset logs
        underTest.tell(resetConnectionLogs, testProbe.ref());
        expectMockClientActorMessage(resetConnectionLogs);
        testProbe.expectMsg(expectedResponse);
    }

    @Test
    public void enabledConnectionLogsAreEnabledAgainAfterModify() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        // Wait until connection is established
        // enable connection logs
        final var enableConnectionLogs = EnableConnectionLogs.of(connectionId, DittoHeaders.empty());
        underTest.tell(enableConnectionLogs, testProbe.ref());
        expectMockClientActorMessage(enableConnectionLogs);
        testProbe.expectMsg(EnableConnectionLogsResponse.of(connectionId, enableConnectionLogs.getDittoHeaders()));

        // modify connection
        underTest.tell(ModifyConnection.of(connection, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        simulateSuccessfulOpenConnectionInClientActor();
        testProbe.expectMsg(ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        // expect the message twice, once for each client
        expectMockClientActorMessage(enableConnectionLogs);
    }

    @Test
    public void disabledConnectionLogsAreNotEnabledAfterModify() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(), testProbe.ref());
        simulateSuccessfulOpenConnectionInClientActor();
        expectCreateConnectionResponse(testProbe, connection);

        //Close logging which are automatically enabled via create connection
        underTest.tell(LoggingExpired.of(connectionId, DittoHeaders.empty()), testProbe.ref());

        // modify connection
        underTest.tell(ModifyConnection.of(connection, dittoHeadersWithCorrelationId), testProbe.ref());
        expectMockClientActorMessage(CloseConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        expectMockClientActorMessage(OpenConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
        testProbe.expectMsg(ModifyConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        mockClientActorProbe.expectNoMsg();
    }

    @Test
    public void forwardSearchCommands() {
        final var mockClientActorProbe1 = TestProbe.apply(actorSystemResource1.getActorSystem());
        final var mockClientActorProbe2 = TestProbe.apply(actorSystemResource1.getActorSystem());
        ConnectionPubSub.get(actorSystemResource1.getActorSystem())
                .subscribe(connectionId, mockClientActorProbe1.ref(), false)
                .toCompletableFuture()
                .join();
        ConnectionPubSub.get(actorSystemResource1.getActorSystem())
                .subscribe(connectionId, mockClientActorProbe2.ref(), false)
                .toCompletableFuture()
                .join();
        mockClientActorProbe.setAutoPilot(new TestActor.AutoPilot() {
            @Override
            public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                if (msg instanceof WithSender<?> withSender && withSender.getMessage() instanceof OpenConnection) {
                    sender.tell(new Status.Success("connected"), mockClientActorProbe.ref());
                }
                return keepRunning();
            }
        });
        final var testProbe = actorSystemResource1.newTestProbe();
        final var connectionActorProps = Props.create(ConnectionPersistenceActor.class,
                () -> new ConnectionPersistenceActor(connectionId,
                        Mockito.mock(MongoReadJournal.class),
                        commandForwarderActor,
                        pubSubMediatorProbe.ref(),
                        Trilean.TRUE,
                        ConfigFactory.empty()));

        // GIVEN: connection persistence actor created with 2 client actors that are allowed to start on same node
        final var underTest = actorSystemResource1.newActor(connectionActorProps, connectionId.toString());
        underTest.tell(createConnection(closedConnectionWith2Clients), testProbe.ref());
        testProbe.expectMsgClass(CreateConnectionResponse.class);
        underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), testProbe.ref());
        // wait until gossip protocol completes
        gossipProbe.expectMsgClass(ActorRef.class);
        gossipProbe.expectMsgClass(ActorRef.class);

        // WHEN: 2 CreateSubscription commands are received
        underTest.tell(CreateSubscription.of(DittoHeaders.empty()), testProbe.ref());
        underTest.tell(CreateSubscription.of(DittoHeaders.empty()), testProbe.ref());

        // THEN: The 2 commands land in different client actors
        final var createSubscription1 = (CreateSubscription) mockClientActorProbe1.fishForMessage(
                FiniteDuration.apply(5, TimeUnit.SECONDS),
                "CreateSubscription",
                PartialFunction.fromFunction(msg -> msg instanceof CreateSubscription)
        );
        final var createSubscription2 = (CreateSubscription) mockClientActorProbe2.fishForMessage(
                FiniteDuration.apply(5, TimeUnit.SECONDS),
                "CreateSubscription",
                PartialFunction.fromFunction(msg -> msg instanceof CreateSubscription)
        );
        assertThat(createSubscription1.getPrefix()).isNotEqualTo(createSubscription2.getPrefix());
    }

    private static <T> WithSender<T> expectMockClientActorMessage(final Class<T> searchedClass) {
        final WithSender<?> withSender = mockClientActorProbe.expectMsgClass(WithSender.class);
        assertThat(withSender.getMessage()).isInstanceOf(searchedClass);
        return (WithSender<T>) withSender;
    }

    private static <T> WithSender<T> expectMockClientActorMessage(final Object expectedMessage) {
        final WithSender<?> withSender = mockClientActorProbe.expectMsgClass(WithSender.class);
        assertThat(withSender.getMessage()).isEqualTo(expectedMessage);
        return (WithSender<T>) withSender;
    }

    private static boolean isMessageSenderInstanceOf(final Object message, final Class<?> searchedClass) {
        final boolean result;
        if (message instanceof WithSender withSender) {
            final var m = withSender.getMessage();
            result = searchedClass.isAssignableFrom(m.getClass());
        } else {
            result = false;
        }
        return result;
    }

    @Test
    @Ignore("TODO unignore and stabilize flaky test")
    public void retriesStartingClientActor() {
        final var parent = actorSystemResource1.newTestProbe();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                Mockito.mock(MongoReadJournal.class),
                                commandForwarderActor,
                                pubSubMediatorProbe.ref(),
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        final CreateConnection createConnection = createConnection();
        final DittoHeaders headersIndicatingFailingInstantiation = createConnection.getDittoHeaders().toBuilder()
                .putHeader("number-of-instantiation-failures",
                        String.valueOf(TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation()))
                .responseRequired(false)
                .build();
        underTest.tell(createConnection.setDittoHeaders(headersIndicatingFailingInstantiation), testProbe.ref());
        final CreateConnectionResponse resp =
                expectCreateConnectionResponse(testProbe, connection);
        assertThat(resp.getDittoHeaders())
                .isEqualTo(headersIndicatingFailingInstantiation);

        assertThat(underTest.isTerminated()).isFalse();
    }

    @Test
    public void escalatesWhenClientActorFailsTooOften() {
        final var parent = actorSystemResource1.newTestKit();
        final var underTest = parent.childActorOf(
                Props.create(
                        ConnectionPersistenceActor.class,
                        () -> new ConnectionPersistenceActor(connectionId,
                                Mockito.mock(MongoReadJournal.class),
                                commandForwarderActor,
                                pubSubMediatorProbe.ref(),
                                Trilean.FALSE,
                                ConfigFactory.empty())
                )
        );
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        final CreateConnection createConnection = createConnection();
        final DittoHeaders headersIndicatingFailingInstantiation = createConnection.getDittoHeaders().toBuilder()
                .putHeader("number-of-instantiation-failures",
                        String.valueOf(TestConstants.CONNECTION_CONFIG.getClientActorRestartsBeforeEscalation() + 1))
                .responseRequired(false)
                .build();
        underTest.tell(createConnection.setDittoHeaders(headersIndicatingFailingInstantiation), testProbe.ref());
        final CreateConnectionResponse resp =
                expectCreateConnectionResponse(testProbe, connection);
        assertThat(resp.getDittoHeaders())
                .isEqualTo(headersIndicatingFailingInstantiation);

        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    @Test
    public void deleteConnectionCommandEmitsEvent() {
        final var underTest = createSupervisor();
        final var testProbe = actorSystemResource1.newTestProbe();
        testProbe.watch(underTest);

        // create connection
        underTest.tell(createConnection(closedConnection), testProbe.ref());
        expectCreateConnectionResponse(testProbe, closedConnection);
        pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

        // delete connection
        underTest.tell(DeleteConnection.of(connectionId, dittoHeadersWithCorrelationId), testProbe.ref());
        testProbe.expectMsg(DeleteConnectionResponse.of(connectionId, dittoHeadersWithCorrelationId));

        pubSubMediatorProbe.fishForMessage(FiniteDuration.apply(5, TimeUnit.SECONDS),
                "connection deleted via pubSub",
                PartialFunction.fromFunction(msg -> msg instanceof DistributedPubSubMediator.Publish publish &&
                        publish.topic().equals(ConnectionDeleted.TYPE)));
        testProbe.expectTerminated(underTest, FiniteDuration.apply(3, TimeUnit.SECONDS));
    }

    private ActorRef createSupervisor() {
        return TestConstants.createConnectionSupervisorActor(connectionId,
                actorSystemResource1.getActorSystem(),
                commandForwarderActor,
                pubSubMediatorProbe.ref());
    }

    private CreateConnection createConnection() {
        return createConnection(connection);
    }

    private CreateConnection createConnection(final Connection connection) {
        return CreateConnection.of(connection, dittoHeadersWithCorrelationId);
    }

    private CreateConnectionResponse expectCreateConnectionResponse(final TestProbe probe, 
            final Connection theConnection) {
        final CreateConnectionResponse resp =
                probe.expectMsgClass(CreateConnectionResponse.class);
        Assertions.assertThat(resp.getConnection())
                .usingRecursiveComparison()
                .ignoringFields("revision", "modified", "created")
                .isEqualTo(theConnection);
        return resp;
    }
    
    private void simulateSuccessfulOpenConnectionInClientActor() {
        expectMockClientActorMessage(EnableConnectionLogs.of(connectionId, DittoHeaders.empty()));
        expectMockClientActorMessage(OpenConnection.of(connectionId, dittoHeadersWithCorrelationId));
        mockClientActorProbe.reply(new Status.Success("mock"));
    }

    private RetrieveConnectionStatusResponse retrieveConnectionStatusResponse() {
        return RetrieveConnectionStatusResponse.getBuilder(connectionId, dittoHeadersWithCorrelationId)
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
    }

}
