/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.MockClientActor.mockClientActorPropsFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.AggregatedConnectivityCommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.Creator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ConnectionActor}.
 */
@SuppressWarnings("NullableProblems")
public final class ConnectionActorTest {

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;
    private static ActorRef conciergeForwarder;
    private String connectionId;
    private CreateConnection createConnection;
    private CreateConnection createClosedConnection;
    private ModifyConnection modifyConnection;
    private ModifyConnection modifyClosedConnection;
    private DeleteConnection deleteConnection;
    private CreateConnectionResponse createConnectionResponse;
    private CreateConnectionResponse createClosedConnectionResponse;
    private ModifyConnectionResponse modifyConnectionResponse;
    private OpenConnection openConnection;
    private CloseConnection closeConnection;
    private CloseConnectionResponse closeConnectionResponse;
    private DeleteConnectionResponse deleteConnectionResponse;
    private RetrieveConnection retrieveConnection;
    private RetrieveConnectionStatus retrieveConnectionStatus;
    private RetrieveConnectionResponse retrieveModifiedConnectionResponse;
    private RetrieveConnectionStatusResponse retrieveConnectionStatusOpenResponse;
    private RetrieveConnectionStatusResponse retrieveConnectionStatusClosedResponse;
    private ConnectionNotAccessibleException connectionNotAccessibleException;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        conciergeForwarder = actorSystem.actorOf(TestConstants.ConciergeForwarderActorMock.props());
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Before
    public void init() {
        connectionId = TestConstants.createRandomConnectionId();
        final Connection connection = TestConstants.createConnection(connectionId, actorSystem);
        final Connection closedConnection =
                TestConstants.createConnection(connectionId, actorSystem, ConnectionStatus.CLOSED,
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        createConnection = CreateConnection.of(connection, DittoHeaders.empty());
        createClosedConnection = CreateConnection.of(closedConnection, DittoHeaders.empty());
        final Connection modifiedConnection = connection.toBuilder().failoverEnabled(false).build();
        modifyConnection = ModifyConnection.of(modifiedConnection, DittoHeaders.empty());
        modifyClosedConnection = ModifyConnection.of(closedConnection, DittoHeaders.empty());
        deleteConnection = DeleteConnection.of(connectionId, DittoHeaders.empty());
        createConnectionResponse = CreateConnectionResponse.of(connection, DittoHeaders.empty());
        createClosedConnectionResponse = CreateConnectionResponse.of(closedConnection, DittoHeaders.empty());
        modifyConnectionResponse = ModifyConnectionResponse.modified(connectionId, DittoHeaders.empty());
        openConnection = OpenConnection.of(connectionId, DittoHeaders.empty());
        closeConnection = CloseConnection.of(connectionId, DittoHeaders.empty());
        closeConnectionResponse = CloseConnectionResponse.of(connectionId, DittoHeaders.empty());
        deleteConnectionResponse = DeleteConnectionResponse.of(connectionId, DittoHeaders.empty());
        retrieveConnection = RetrieveConnection.of(connectionId, DittoHeaders.empty());
        retrieveConnectionStatus = RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty());
        retrieveModifiedConnectionResponse =
                RetrieveConnectionResponse.of(modifiedConnection, DittoHeaders.empty());
        retrieveConnectionStatusOpenResponse =
                RetrieveConnectionStatusResponse.of(connectionId, ConnectionStatus.OPEN, DittoHeaders.empty());
        retrieveConnectionStatusClosedResponse =
                RetrieveConnectionStatusResponse.of(connectionId, ConnectionStatus.CLOSED, DittoHeaders.empty());
        connectionNotAccessibleException = ConnectionNotAccessibleException.newBuilder(connectionId).build();
    }

    @Test
    public void tryToSendOtherCommandThanCreateDuringInitialization() {
        new TestKit(actorSystem) {{
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder);

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
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, (connection, concierge) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
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
    public void createConnectionInClosedState() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, (connection, concierge) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createClosedConnection, getRef());
            expectMsg(createClosedConnectionResponse);

            // assert that client actor is not called for closed connection
            probe.expectNoMessage();
        }};
    }

    @Test
    public void modifyConnectionInClosedState() {
        new TestKit(actorSystem) {{
            final TestProbe probe = TestProbe.apply(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, (connection, concierge) -> MockClientActor.props(probe.ref()));
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
                            conciergeForwarder, (connection, concierge) -> MockClientActor.props(probe.ref()));
            watch(underTest);

            // create connection
            underTest.tell(createClosedConnection, getRef());
            expectMsg(createClosedConnectionResponse);
            probe.expectNoMessage();

            // create connection
            underTest.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());
            probe.expectNoMessage();

            final ConnectionMetrics metrics =
                    ConnectivityModelFactory.newConnectionMetrics(ConnectionStatus.CLOSED, "connection is closed",
                            Instant.EPOCH, BaseClientState.DISCONNECTED.name(), Collections.emptyList(),
                            Collections.emptyList());
            final RetrieveConnectionMetricsResponse metricsResponse =
                    RetrieveConnectionMetricsResponse.of(connectionId, metrics, DittoHeaders.empty());
            final AggregatedConnectivityCommandResponse aggregatedConnectivityCommandResponse =
                    AggregatedConnectivityCommandResponse.of(connectionId,
                            Collections.singletonList(metricsResponse), metricsResponse.getType(), HttpStatusCode.OK,
                            DittoHeaders.empty());
            expectMsg(aggregatedConnectivityCommandResponse);
        }};
    }

    @Test
    public void modifyConnectionClosesAndRestartsClientActor() {
        new TestKit(actorSystem) {{
            final TestProbe mockClientProbe = TestProbe.apply(actorSystem);
            final TestProbe commandSender = TestProbe.apply(actorSystem);
            final AtomicReference<Connection> latestConnection = new AtomicReference<>();
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, (connection, concierge) -> {
                                latestConnection.set(connection);
                                return MockClientActor.props(mockClientProbe.ref());
                            });

            // create connection
            underTest.tell(createConnection, commandSender.ref());
            mockClientProbe.expectMsg(openConnection);
            commandSender.expectMsg(createConnectionResponse);

            final ActorRef clientActor = watch(mockClientProbe.sender());
            watch(clientActor);


            // modify connection
            underTest.tell(modifyConnection, commandSender.ref());
            // modify triggers a CloseConnection
            mockClientProbe.expectMsg(CloseConnection.of(connectionId, DittoHeaders.empty()));


            expectTerminated(clientActor);

            // and sends an open connection (if desired state is open)
            mockClientProbe.expectMsg(openConnection);
            // finally the response is sent
            commandSender.expectMsg(modifyConnectionResponse);

            Awaitility.await().untilAtomic(latestConnection, CoreMatchers.is(modifyConnection.getConnection()));
        }};
    }

    @Test
    public void recoverOpenConnection() {
        new TestKit(actorSystem) {{
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder);
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            expectMsg(createConnectionResponse);

            // stop actor
            getSystem().stop(underTest);
            expectTerminated(underTest);

            // recover actor
            underTest = Retry.untilSuccess(() ->
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder));

            // retrieve connection status
            underTest.tell(retrieveConnectionStatus, getRef());
            expectMsg(retrieveConnectionStatusOpenResponse);
        }};
    }

    @Test
    public void recoverModifiedConnection() {
        new TestKit(actorSystem) {{
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder);
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
                    conciergeForwarder);

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
                            conciergeForwarder);
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
                            conciergeForwarder));

            // retrieve connection status
            underTest.tell(retrieveConnectionStatus, getRef());
            expectMsg(retrieveConnectionStatusClosedResponse);
        }};
    }

    @Test
    public void recoverDeletedConnection() {
        new TestKit(actorSystem) {{
            ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder);
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
                            conciergeForwarder));

            // retrieve connection status
            underTest.tell(retrieveConnectionStatus, getRef());
            expectMsg(connectionNotAccessibleException);
        }};
    }

    @Test
    public void exceptionDuringClientActorPropsCreation() {
        new TestKit(actorSystem) {{
            final Props connectionActorProps =
                    ConnectionActor.props(TestConstants.createRandomConnectionId(), pubSubMediator, conciergeForwarder,
                            (connection, conciergeForwarder) -> {
                                throw ConnectionConfigurationInvalidException.newBuilder("validation failed...")
                                        .build();
                            }, null);
            // create another actor because this it is stopped and we want to test if the child is terminated
            final TestKit parent = new TestKit(actorSystem);
            final ActorRef connectionActorRef = watch(parent.childActorOf(connectionActorProps));

            // create connection
            connectionActorRef.tell(createConnection, parent.getRef());
            parent.expectMsgClass(ConnectionSupervisorActor.ManualReset.class); // is sent after "empty" recovery

            // expect ConnectionConfigurationInvalidException sent to parent
            final Exception exception = parent.expectMsgClass(ConnectionConfigurationInvalidException.class);
            assertThat(exception).hasMessageContaining("validation failed...");

            // expect the connection actor is terminated
            expectTerminated(connectionActorRef);
        }};
    }

    @Test
    public void exceptionDueToCustomValidator() {
        new TestKit(actorSystem) {{
            final Props connectionActorProps =
                    ConnectionActor.props(TestConstants.createRandomConnectionId(), pubSubMediator,
                            conciergeForwarder, mockClientActorPropsFactory,
                            command -> {
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
            parent.expectMsgClass(ConnectionSupervisorActor.ManualReset.class); // is sent after "empty" recovery

            // expect ConnectionUnavailableException sent to parent
            final ConnectionUnavailableException exception =
                    parent.expectMsgClass(ConnectionUnavailableException.class);
            assertThat(exception).hasMessageContaining("not valid");

            // expect the connection actor is terminated
            expectTerminated(connectionActorRef);
        }};
    }

    @Test
    public void testThingEventWithAuthorizedSubjectExpectIsForwarded() {
        final Set<String> valid = Collections.singleton(TestConstants.Authorization.SUBJECT_ID);
        testForwardThingEvent(true, TestConstants.thingModified(valid));
    }

    @Test
    public void testThingEventIsForwardedToFilteredTarget() {
        final Connection connection = TestConstants.createConnection(connectionId, actorSystem,
                TestConstants.Targets.TARGET_WITH_PLACEHOLDER);

        final Target expectedTarget = ConnectivityModelFactory.newTarget(TestConstants.Targets.TARGET_WITH_PLACEHOLDER,
                "target:" + TestConstants.Things.NAMESPACE + "/" +
                        TestConstants.Things.ID);

        final CreateConnection createConnection = CreateConnection.of(connection, DittoHeaders.empty());
        final Set<String> valid = Collections.singleton(TestConstants.Authorization.SUBJECT_ID);
        testForwardThingEvent(createConnection, true, TestConstants.thingModified(valid), expectedTarget);
    }

    @Test
    public void testThingEventWithUnauthorizedSubjectExpectIsNotForwarded() {
        final Set<String> invalid = Collections.singleton("iot:user");
        testForwardThingEvent(false, TestConstants.thingModified(invalid));
    }

    @Test
    public void testLiveMessageWithAuthorizedSubjectExpectIsNotForwarded() {
        final Set<String> valid = Collections.singleton(TestConstants.Authorization.SUBJECT_ID);
        testForwardThingEvent(false, TestConstants.sendThingMessage(valid));
    }

    private void testForwardThingEvent(final boolean isForwarded, final Signal<?> signal) {
        testForwardThingEvent(createConnection, isForwarded, signal, TestConstants.Targets.TWIN_TARGET);
    }

    private void testForwardThingEvent(final CreateConnection createConnection, final boolean isForwarded,
            final Signal<?> signal, final Target expectedTarget) {
        new TestKit(actorSystem) {{
            final TestKit probe = new TestKit(actorSystem);
            final ActorRef underTest =
                    TestConstants.createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                            conciergeForwarder, (connection, conciergeForwarder) -> TestActor.props(probe));
            watch(underTest);

            // create connection
            underTest.tell(createConnection, getRef());
            final Object o = expectMsgAnyClassOf(Object.class);
            System.out.println(o);
            //CreateConnectionResponse.of(createConnection.getConnection(), DittoHeaders.empty()));

            underTest.tell(signal, getRef());

            if (isForwarded) {
                final UnmappedOutboundSignal unmappedOutboundSignal =
                        probe.expectMsgClass(UnmappedOutboundSignal.class);
                assertThat(unmappedOutboundSignal.getSource()).isEqualTo(signal);
                assertThat(unmappedOutboundSignal.getTargets()).isEqualTo(Collections.singleton(expectedTarget));
            } else {
                probe.expectNoMessage();
            }
        }};
    }

    static class TestActor extends AbstractActor {

        private final TestKit probe;

        private TestActor(final TestKit probe) {
            this.probe = probe;
        }

        static Props props(final TestKit probe) {
            return Props.create(TestActor.class, new Creator<TestActor>() {
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
}
