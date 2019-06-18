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
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for basic {@link org.eclipse.ditto.services.connectivity.messaging.BaseClientActor} funcionality.
 */
@RunWith(MockitoJUnitRunner.class)
public final class BaseClientActorTest {

    private static final Object STATE_TIMEOUT = FSM.StateTimeout$.MODULE$;
    private static ActorSystem actorSystem;
    private static DittoConnectivityConfig connectivityConfig;

    @Mock
    private BaseClientActor delegate;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connectivityConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void shouldReconnectIfSocketIsClosed() {
        new TestKit(actorSystem) {{
            final String randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, actorSystem, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = actorSystem.actorOf(props);
            watch(dummyClientActor);

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            andConnectionNotSuccessful(dummyClientActor);

            expectMsgClass(Status.Failure.class);

            thenExpectConnectClientCalled();
            thenExpectConnectClientCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingTimeout());
        }};
    }

    @Test
    public void handlesCloseConnectionInConnectingState() {
        new TestKit(actorSystem) {{
            final String randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, actorSystem, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), delegate);

            final ActorRef dummyClientActor = actorSystem.actorOf(props);
            watch(dummyClientActor);

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectConnectClientCalled();

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            thenExpectDisconnectClientCalled();
            andDisconnectionSuccessful(dummyClientActor, getRef());

            expectMsgClass(Status.Success.class);
        }};
    }

    @Test
    public void reconnectsInConnectingStateIfNoResponseReceived() {
        // TODO
    }

    @Test
    public void reconnectsInConnectingStateIfFailureResponseReceived() {
        // TODO
    }

    private void thenExpectConnectClientCalled() {
        thenExpectConnectClientCalledAfterTimeout(Duration.ZERO);
    }

    private void thenExpectDisconnectClientCalled() {
        verify(delegate, timeout(100)).doDisconnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectConnectClientCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 100)).doConnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectNoConnectClientCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 100).times(0)).doConnectClient(any(Connection.class), nullable(ActorRef.class));
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

    private void andConnectionTimedOut(final ActorRef clientActor) {
        clientActor.tell(STATE_TIMEOUT, clientActor);
    }

    private static final class DummyClientActor extends BaseClientActor {

        private final BaseClientActor delegate;

        public DummyClientActor(final Connection connection, final ConnectivityStatus desiredConnectionStatus,
                final ActorRef congiereForwarder, final BaseClientActor delegate) {
            super(connection, desiredConnectionStatus, congiereForwarder);
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
            return delegate.doTestConnection(connection);
        }

        @Override
        protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
            delegate.allocateResourcesOnConnection(clientConnected);
        }

        @Override
        protected void cleanupResourcesForConnection() {
            delegate.cleanupResourcesForConnection();
        }

        @Override
        protected ActorRef getPublisherActor() {
            return delegate.getPublisherActor();
        }

        @Override
        protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
            delegate.doConnectClient(connection, origin);
        }

        @Override
        protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
            delegate.doDisconnectClient(connection, origin);
        }

    }

}
