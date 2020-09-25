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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.compress.utils.Sets;
import org.awaitility.Awaitility;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.MockClientActor;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.WithMockServers;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.connectivity.BaseClientState;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
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
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestFromSubscription;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor}.
 */
public final class ConnectionPersistenceActorTest extends WithMockServers {

    private static final Set<String> SUBJECTS = Sets.newHashSet(TestConstants.Authorization.SUBJECT_ID,
            TestConstants.Authorization.UNAUTHORIZED_SUBJECT_ID);
    private static final Set<StreamingType> TWIN_AND_LIVE_EVENTS =
            EnumSet.of(StreamingType.EVENTS, StreamingType.LIVE_EVENTS);
    private static final Set<StreamingType> TWIN_AND_LIVE_EVENTS_AND_MESSAGES =
            EnumSet.of(StreamingType.EVENTS, StreamingType.LIVE_EVENTS, StreamingType.MESSAGES);
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
    private ThingModifiedEvent thingModified;
    private DittoProtocolSub dittoProtocolSubMock;

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
        thingModified = TestConstants.thingModified(Collections.singleton(TestConstants.Authorization.SUBJECT));
        dittoProtocolSubMock = Mockito.mock(DittoProtocolSub.class);
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

            final DittoRuntimeException exception =
                    DittoRuntimeException.newBuilder("some.error", HttpStatusCode.BAD_REQUEST).build();
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

            final DittoRuntimeException exception =
                    DittoRuntimeException.newBuilder("some.error", HttpStatusCode.BAD_REQUEST).build();
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
                    TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock),
                    pubSubMediator
            );
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);
            expectRemoveSubscriber(1);
            expectSubscribe(TWIN_AND_LIVE_EVENTS, SUBJECTS);

            // close connection
            underTest.tell(closeConnection, getRef());
            probe.expectMsg(closeConnection);
            expectMsg(closeConnectionResponse);
            expectRemoveSubscriber(2);

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
                    TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock),
                    pubSubMediator
            );
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);
            expectRemoveSubscriber(1);
            expectSubscribe(TWIN_AND_LIVE_EVENTS, SUBJECTS);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);
            expectRemoveSubscriber(2);
            probe.expectNoMessage();
            expectTerminated(underTest);
        }};

    }

    @Test
    public void manageConnectionWith2Clients() throws Exception {
        startSecondActorSystemAndJoinCluster();
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create closed connection
            underTest.tell(createClosedConnectionWith2Clients, getRef());
            expectMsgClass(CreateConnectionResponse.class);

            // open connection: only local client actor is asked for a response.
            underTest.tell(openConnection, getRef());
            probe.expectMsg(openConnection);
            expectMsgClass(OpenConnectionResponse.class);

            // forward signal once
            underTest.tell(thingModified, getRef());
            final Object outboundSignal = probe.expectMsgClass(Object.class);
            assertThat(outboundSignal).isInstanceOf(OutboundSignal.class);
            // probe.expectMsg(OutboundSignal.class); // does not work for some reason

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
            clientActorMock.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // delete connection
            underTest.tell(deleteConnection, getRef());
            expectMsg(deleteConnectionResponse);

            // create connection again (while ConnectionActor is in deleted state)
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);
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
        final ActorRef conciergeForwarder =
                systemWithBlocklist.actorOf(TestConstants.ProxyActorMock.props());

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
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // create connection
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
                            TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock),
                            pubSubMediator);

            // create connection
            underTest.tell(createConnection, commandSender.ref());
            mockClientProbe.expectMsg(openConnection);
            commandSender.expectMsg(createConnectionResponse);

            final ActorRef clientActor = watch(mockClientProbe.sender());

            expectSubscribe(TWIN_AND_LIVE_EVENTS, SUBJECTS);

            // modify connection
            underTest.tell(modifyConnection, commandSender.ref());
            // modify triggers a CloseConnection
            mockClientProbe.expectMsg(CloseConnection.of(connectionId, DittoHeaders.empty()));

            // unsubscribe is called for topics of unmodified connection
            expectRemoveSubscriber(1);
            expectTerminated(clientActor);

            // and sends an open connection (if desired state is open)
            mockClientProbe.expectMsg(openConnection);
            // finally the response is sent
            commandSender.expectMsg(modifyConnectionResponse);

            // modified connection contains an additional target for messages
            expectSubscribe(TWIN_AND_LIVE_EVENTS_AND_MESSAGES, SUBJECTS);

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
            mockClientProbe.expectMsg(OpenConnection.of(connectionId, DittoHeaders.empty()));

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
            recoveredMockClientProbe.expectMsg(OpenConnection.of(connectionId, DittoHeaders.empty()));

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

            ActorRef underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                    proxyActor, TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            expectRemoveSubscriber(1);
            expectSubscribe(TWIN_AND_LIVE_EVENTS, SUBJECTS);

            // modify connection
            underTest.tell(modifyConnection, getRef());
            expectMsg(modifyConnectionResponse);

            expectRemoveSubscriber(2);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                    proxyActor);

            expectSubscribe(TWIN_AND_LIVE_EVENTS_AND_MESSAGES, SUBJECTS);

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
                            TestConstants.dummyDittoProtocolSub(pubSubMediator), proxyActor,
                            (connection, proxyActor, connectionActor) -> {
                                throw ConnectionConfigurationInvalidException.newBuilder("validation failed...")
                                        .build();
                            }, null);
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
                            TestConstants.dummyDittoProtocolSub(pubSubMediator), proxyActor,
                            mockClientActorPropsFactory,
                            (command, connection) -> {
                                throw ConnectionUnavailableException.newBuilder(connectionId)
                                        .dittoHeaders(command.getDittoHeaders())
                                        .message("not valid")
                                        .build();
                            });

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
    public void testThingEventWithAuthorizedSubjectExpectIsForwarded() {
        final Set<AuthorizationSubject> valid = Collections.singleton(TestConstants.Authorization.SUBJECT);
        testForwardThingEvent(true, TestConstants.thingModified(valid));
    }

    @Test
    public void testThingEventIsForwardedToFilteredTarget() {
        final Connection connection = TestConstants.createConnection(connectionId,
                TestConstants.Targets.TARGET_WITH_PLACEHOLDER);

        // expect that address is still with placeholders (as replacement was moved to MessageMappingProcessorActor
        final Target expectedTarget = ConnectivityModelFactory.newTarget(TestConstants.Targets.TARGET_WITH_PLACEHOLDER,
                TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress(), null);

        final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
        testForwardThingEvent(createConnection, true, thingModified, expectedTarget);
    }

    @Test
    public void testThingEventWithUnauthorizedSubjectExpectIsNotForwarded() {
        final Set<AuthorizationSubject> invalid =
                Collections.singleton(AuthorizationModelFactory.newAuthSubject("iot:user"));
        testForwardThingEvent(false, TestConstants.thingModified(invalid));
    }

    @Test
    public void testLiveMessageWithAuthorizedSubjectExpectIsNotForwarded() {
        final Set<AuthorizationSubject> valid = Collections.singleton(TestConstants.Authorization.SUBJECT);
        testForwardThingEvent(false, TestConstants.sendThingMessage(valid));
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
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // send cleanup command
            underTest.tell(
                    CleanupPersistence.of(DefaultEntityId.of(
                            ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionId),
                            DittoHeaders.empty()),
                    getRef());
            expectMsg(CleanupPersistenceResponse.success(
                    DefaultEntityId.of(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionId),
                    DittoHeaders.empty()));
        }};
    }

    @Test
    public void enableConnectionLogs() {
        final EnableConnectionLogs enableConnectionLogs = EnableConnectionLogs.of(connectionId, DittoHeaders.empty());
        final EnableConnectionLogsResponse enableConnectionLogsResponse =
                EnableConnectionLogsResponse.of(connectionId, enableConnectionLogs.getDittoHeaders());
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

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
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // retrieve logs
            final RetrieveConnectionLogs retrieveConnectionLogs =
                    RetrieveConnectionLogs.of(connectionId, DittoHeaders.empty());
            underTest.tell(retrieveConnectionLogs, getRef());
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
        final EnableConnectionLogs enableConnectionLogs = EnableConnectionLogs.of(connectionId, DittoHeaders.empty());
        final EnableConnectionLogsResponse enableConnectionLogsResponse =
                EnableConnectionLogsResponse.of(connectionId, enableConnectionLogs.getDittoHeaders());
        new TestKit(actorSystem) {{

            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, proxyActor,
                            (connection, concierge, connectionActor) -> MockClientActor.props(probe.ref()),
                            TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock), pubSubMediator);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // Wait until connection is established
            expectAnySubscribe();

            // enable connection logs
            underTest.tell(enableConnectionLogs, getRef());
            probe.expectMsg(enableConnectionLogs);

            expectMsg(enableConnectionLogsResponse);

            // modify connection
            underTest.tell(modifyConnection, getRef());
            probe.expectMsg(closeConnection);
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
            probe.expectMsg(openConnection);
            expectMsg(createConnectionResponse);

            // modify connection
            underTest.tell(modifyConnection, getRef());
            probe.expectMsg(closeConnection);
            probe.expectMsg(openConnection);
            expectMsg(modifyConnectionResponse);

            probe.expectNoMsg();
        }};
    }

    @Test
    public void testHandleSignalWithAcknowledgementRequest() {
        new TestKit(actorSystem) {{
            final TestKit probe = new TestKit(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, proxyActor,
                            (connection, connectionActor, proxyActor) -> TestActor.props(probe),
                            TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock), pubSubMediator);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsgClass(CreateConnectionResponse.class);

            // Wait until connection is established
            expectAnySubscribe();

            final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("test-ack");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            final AttributeModified attributeModified = AttributeModified.of(
                    TestConstants.Things.THING_ID, JsonPointer.of("hello"), JsonValue.of("world!"), 5L, dittoHeaders);
            underTest.tell(attributeModified, getRef());

            final OutboundSignal unmappedOutboundSignal = probe.expectMsgClass(OutboundSignal.class);
            assertThat(unmappedOutboundSignal.getSource()).isEqualTo(attributeModified);

            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, TestConstants.Things.THING_ID, HttpStatusCode.OK,
                            dittoHeaders);
            underTest.tell(acknowledgement, getRef());

            expectMsg(acknowledgement);
        }};
    }

    @Test
    public void forwardSearchCommands() {
        new TestKit(actorSystem) {{
            final ConnectionId myConnectionId = ConnectionId.of(UUID.randomUUID().toString());
            final TestProbe clientActorsProbe = TestProbe.apply("clientActors", actorSystem);
            final TestProbe proxyActorProbe = TestProbe.apply("proxyActor", actorSystem);
            // Mock the client actors so that they forward all signals to clientActorsProbe with their own reference
            final ClientActorPropsFactory propsFactory = (a, b, c) -> Props.create(AbstractActor.class, () ->
                    new AbstractActor() {
                        @Override
                        public Receive createReceive() {
                            return ReceiveBuilder.create()
                                    .match(WithDittoHeaders.class, message ->
                                            clientActorsProbe.ref()
                                                    .tell(WithSender.of(message, getSelf()), getSender())
                                    )
                                    .build();
                        }
                    });
            final DittoProtocolSub dittoProtocolSub = Mockito.mock(DittoProtocolSub.class);
            when(dittoProtocolSub.subscribe(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
            final Props connectionActorProps = Props.create(ConnectionPersistenceActor.class, () ->
                    new ConnectionPersistenceActor(myConnectionId, dittoProtocolSub, proxyActorProbe.ref(),
                            propsFactory, null, 999
                    ));

            // GIVEN: connection persistence actor created with 2 client actors that are allowed to start on same node
            final ActorRef underTest = actorSystem.actorOf(connectionActorProps, myConnectionId.toString());
            underTest.tell(createClosedConnectionWith2Clients, getRef());
            expectMsgClass(CreateConnectionResponse.class);
            underTest.tell(OpenConnection.of(myConnectionId, DittoHeaders.empty()), getRef());
            assertThat(clientActorsProbe.expectMsgClass(WithSender.class).getMessage())
                    .isInstanceOf(OpenConnection.class);
            clientActorsProbe.reply(new Status.Success("connected"));
            expectMsgClass(OpenConnectionResponse.class);

            // WHEN: 2 CreateSubscription commands are received
            // THEN: The 2 commands land in different client actors
            underTest.tell(CreateSubscription.of(DittoHeaders.empty()), getRef());
            underTest.tell(CreateSubscription.of(DittoHeaders.empty()), getRef());
            final WithSender<?> createSubscription1 = clientActorsProbe.expectMsgClass(WithSender.class);
            final WithSender<?> createSubscription2 = clientActorsProbe.expectMsgClass(WithSender.class);
            assertThat(createSubscription1.getSender()).isNotEqualTo(createSubscription2.getSender());

            // WHEN: RequestSubscription command is sent with the prefix of a received CreateSubscription command
            // THEN: The command lands in the same client actor
            final String subscriptionId1 =
                    ((CreateSubscription) createSubscription1.getMessage()).getPrefix().orElseThrow() + "-suffix";
            underTest.tell(RequestFromSubscription.of(subscriptionId1, 99L, DittoHeaders.empty()), getRef());
            final WithSender<?> requestSubscription1 = clientActorsProbe.expectMsgClass(WithSender.class);
            assertThat(requestSubscription1.getMessage()).isInstanceOf(RequestFromSubscription.class);
            assertThat(requestSubscription1.getSender()).isEqualTo(createSubscription1.getSender());

            // Same for CancelSubscription
            final String subscriptionId2 =
                    ((CreateSubscription) createSubscription2.getMessage()).getPrefix().orElseThrow() + "-suffix";
            underTest.tell(CancelSubscription.of(subscriptionId2, DittoHeaders.empty()), getRef());
            final WithSender<?> cancelSubscription2 = clientActorsProbe.expectMsgClass(WithSender.class);
            assertThat(cancelSubscription2.getSender()).isEqualTo(createSubscription2.getSender());
        }};
    }

    private void testForwardThingEvent(final boolean isForwarded, final Signal<?> signal) {
        testForwardThingEvent(createConnection, isForwarded, signal, TestConstants.Targets.TWIN_TARGET);
    }

    private void testForwardThingEvent(final CreateConnection createConnection, final boolean isForwarded,
            final Signal<?> signal, final Target expectedTarget) {
        new TestKit(actorSystem) {{
            final TestKit probe = new TestKit(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem,
                            proxyActor,
                            (connection, conciergeForwarder, connectionActor) -> TestActor.props(probe),
                            TestConstants.dummyDittoProtocolSub(pubSubMediator, dittoProtocolSubMock), pubSubMediator);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsgClass(CreateConnectionResponse.class);

            // wait until connection actor is subscribed otherwise the signal won't be forwarded
            expectAnySubscribe();

            underTest.tell(signal, getRef());

            if (isForwarded) {
                final OutboundSignal unmappedOutboundSignal =
                        probe.expectMsgClass(OutboundSignal.class);
                assertThat(unmappedOutboundSignal.getSource()).isEqualTo(signal);

                // check target address only due to live migration
                assertThat(unmappedOutboundSignal.getTargets().stream().map(Target::getAddress))
                        .containsExactly(expectedTarget.getAddress());
            } else {
                probe.expectNoMessage();
            }
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

    static final class TestActor extends AbstractActor {

        private final TestKit probe;

        private TestActor(final TestKit probe) {
            this.probe = probe;
        }

        static Props props(final TestKit probe) {
            return Props.create(TestActor.class, new Creator<>() {
                private static final long serialVersionUID = 1L;

                @Override
                public TestActor create() {
                    return new TestActor(probe);
                }
            });
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(OpenConnection.class, cc -> sender().tell(new Status.Success("connected"), self()))
                    .matchAny(m -> probe.getRef().forward(m, context())).build();
        }
    }

    private void expectSubscribe(final Collection<StreamingType> streamingTypes, final Set<String> subjects) {
        verify(dittoProtocolSubMock, timeout(500))
                .subscribe(argThat(argument -> streamingTypes.equals(new HashSet<>(argument))),
                        eq(subjects),
                        any(ActorRef.class));
    }

    // expect any call to subscribe, just to wait for the subscription
    private void expectAnySubscribe() {
        verify(dittoProtocolSubMock, timeout(500)).subscribe(anyCollection(), anySet(), any(ActorRef.class));
    }

    private void expectRemoveSubscriber(final int howManyTimes) {
        verify(dittoProtocolSubMock, timeout(500).times(howManyTimes)).removeSubscriber(any(ActorRef.class));
    }

    private static void shutdown(final ActorSystem system) {
        if (system != null) {
            TestKit.shutdownActorSystem(system, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

}
