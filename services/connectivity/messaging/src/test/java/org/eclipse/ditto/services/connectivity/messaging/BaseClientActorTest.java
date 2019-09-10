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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.InitializationState.ResourceReady;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for basic {@link org.eclipse.ditto.services.connectivity.messaging.BaseClientActor} functionality.
 */
@RunWith(MockitoJUnitRunner.class)
public final class BaseClientActorTest {

    private static final Status.Success CONNECTED_STATUS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_STATUS = new Status.Success(BaseClientState.DISCONNECTED);
    private static ActorSystem actorSystem;
    private static DittoConnectivityConfig connectivityConfig;

    @Mock
    private BaseClientActor delegate;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connectivityConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
    }

    @AfterClass
    public static void tearDown() {
        if (null != actorSystem) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
        }
    }

    @Test
    public void reconnectsInConnectingStateIfFailureResponseReceived() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            andConnectionNotSuccessful(dummyClientActor);

            expectMsgClass(Status.Failure.class);

            thenExpectConnectClientCalled();
            thenExpectConnectClientCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingMinTimeout());
        }};
    }

    @Test
    public void handlesCloseConnectionInConnectingState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectConnectClientCalled();

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectDisconnectClientCalled();
            andDisconnectionSuccessful(dummyClientActor, getRef());

            expectMsg(new Status.Success(BaseClientState.DISCONNECTED));
        }};
    }

    @Test
    public void handlesOpenConnectionInConnectingState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();

            // send another OpenConnection command while connecting
            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalledOnceAfterTimeout(Duration.ofSeconds(1));

            expectMsgClass(ConnectionSignalIllegalException.class);
        }};
    }

    @Test
    public void handlesCloseConnectionInDisconnectingState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());

            expectMsg(CONNECTED_STATUS);

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectDisconnectClientCalled();

            // send another CloseConnection command while disconnecting
            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());

            expectMsgClass(ConnectionSignalIllegalException.class);
        }};
    }

    @Test
    public void handlesCloseConnectionInDisconnectedState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());

            expectMsg(CONNECTED_STATUS);

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectDisconnectClientCalled();

            andDisconnectionSuccessful(dummyClientActor, getRef());

            expectMsg(DISCONNECTED_STATUS);

            // send another CloseConnection command while disconnected
            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());

            expectMsg(DISCONNECTED_STATUS);
        }};
    }

    @Test
    public void handlesOpenConnectionInConnectedState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());
            expectMsg(CONNECTED_STATUS);

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalledOnceAfterTimeout(Duration.ofSeconds(1));

            expectMsg(CONNECTED_STATUS);
        }};
    }

    @Test
    public void reconnectsInConnectingStateIfNoResponseReceived() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectConnectClientCalled();

            andStateTimeoutSent(dummyClientActor);

            expectMsgClass(Status.Failure.class);

            thenExpectConnectClientCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingMinTimeout());
        }};
    }

    @Test
    public void shouldReconnectIfSocketIsClosed() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0])
                            .toBuilder()
                            .uri("amqps://username:password@127.0.0.1:65536") // port 65536 does not even exist ;)
                            .build();
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());

            expectMsgClass(Status.Failure.class);

            thenExpectCleanupResourcesCalled();
            Mockito.clearInvocations(delegate);
            thenExpectCleanupResourcesCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingMinTimeout());
            thenExpectNoConnectClientCalled();
        }};
    }

    @Test
    public void doesNotReconnectIfConnectionSuccessful() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectConnectClientCalled();
            Mockito.clearInvocations(delegate);
            andConnectionSuccessful(dummyClientActor, getRef());

            expectMsgClass(Status.Success.class);
            thenExpectNoConnectClientCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingMinTimeout());
        }};
    }

    @Test
    public void connectsAutomaticallyAfterActorStart() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId,new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            thenExpectConnectClientCalledAfterTimeout(Duration.ofSeconds(5L));
            Mockito.clearInvocations(delegate);
            andConnectionSuccessful(dummyClientActor, getRef());
        }};
    }

    private void thenExpectConnectClientCalled() {
        thenExpectConnectClientCalledAfterTimeout(Duration.ZERO);
    }

    private void thenExpectDisconnectClientCalled() {
        verify(delegate, timeout(200)).doDisconnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectConnectClientCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).atLeastOnce())
                .doConnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectConnectClientCalledOnceAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).times(1))
                .doConnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectNoConnectClientCalled() {
        thenExpectNoConnectClientCalledAfterTimeout(Duration.ZERO);
    }

    private void thenExpectNoConnectClientCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).times(0)).doConnectClient(any(Connection.class),
                nullable(ActorRef.class));
    }

    private void thenExpectCleanupResourcesCalled() {
        thenExpectCleanupResourcesCalledAfterTimeout(Duration.ZERO);
    }

    private void thenExpectCleanupResourcesCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).atLeastOnce()).cleanupResourcesForConnection();
    }

    private void whenOpeningConnection(final ActorRef clientActor, final OpenConnection openConnection, final ActorRef sender) {
        clientActor.tell(openConnection, sender);
    }
    private void andClosingConnection(final ActorRef clientActor, final CloseConnection closeConnection, final ActorRef sender) {
        clientActor.tell(closeConnection, sender);
    }

    private void andConnectionSuccessful(final ActorRef clientActor, final ActorRef origin) {
        clientActor.tell((ClientConnected) () -> Optional.of(origin), clientActor);
    }

    private void andDisconnectionSuccessful(final ActorRef clientActor, final ActorRef origin) {
        clientActor.tell((ClientDisconnected) () -> Optional.of(origin), clientActor);
    }

    private void andConnectionNotSuccessful(final ActorRef clientActor) {
        clientActor.tell(new ImmutableConnectionFailure(null, null, "expected exception"),
                clientActor);
    }

    private void andStateTimeoutSent(final ActorRef clientActor) {
        clientActor.tell(FSM.StateTimeout$.MODULE$, clientActor);
    }

    private static final class DummyClientActor extends BaseClientActor {
        private static final Logger LOGGER = LoggerFactory.getLogger(DummyClientActor.class);

        private final BaseClientActor delegate;

        public DummyClientActor(final Connection connection, final ConnectivityStatus desiredConnectionStatus,
                final ActorRef conciergeForwarder, final BaseClientActor delegate) {
            super(connection, desiredConnectionStatus, conciergeForwarder);
            this.delegate = delegate;
        }

        /**
         * Creates Akka configuration object for this actor.
         *
         * @param connection the connection.
         * @param conciergeForwarder the actor used to send signals to the concierge service.
         * @return the Akka configuration Props object.
         */
        public static Props props(final Connection connection, final ActorRef conciergeForwarder,
                final BaseClientActor delegate) {
            return Props.create(DummyClientActor.class, connection, connection.getConnectionStatus(),
                    conciergeForwarder, delegate);
        }

        @Override
        protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
            LOGGER.info("doTestConnection");
            return delegate.doTestConnection(connection);
        }

        @Override
        protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
            LOGGER.info("allocateResourcesOnConnection");
            delegate.allocateResourcesOnConnection(clientConnected);
            getSelf().tell(ResourceReady.publisherReady(getSelf()), getSelf());
            notifyConsumersReady();
        }

        @Override
        protected void cleanupResourcesForConnection() {
            LOGGER.info("cleanupResourcesForConnection");
            delegate.cleanupResourcesForConnection();
        }

        @Override
        protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
            LOGGER.info("doConnectClient");
            delegate.doConnectClient(connection, origin);
        }

        @Override
        protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
            LOGGER.info("doDisconnectClient");
            delegate.doDisconnectClient(connection, origin);
        }

    }

}
