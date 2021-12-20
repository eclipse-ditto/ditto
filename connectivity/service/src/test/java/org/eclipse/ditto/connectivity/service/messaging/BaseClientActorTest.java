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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for basic {@link BaseClientActor} functionality.
 */
@RunWith(MockitoJUnitRunner.class)
public final class BaseClientActorTest {

    private static final Status.Success CONNECTED_STATUS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_STATUS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final Duration DEFAULT_MESSAGE_TIMEOUT = Duration.ofSeconds(3);
    private static Duration DISCONNECT_TIMEOUT;
    private static ActorSystem actorSystem;
    private static DittoConnectivityConfig connectivityConfig;

    @Mock
    private BaseClientActor delegate;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        DISCONNECT_TIMEOUT = connectivityConfig.getClientConfig().getDisconnectAnnouncementTimeout()
                .plus(connectivityConfig.getClientConfig().getDisconnectingMaxTimeout());
    }

    @AfterClass
    public static void tearDown() {
        if (null != actorSystem) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void reconnectsInConnectingStateIfFailureResponseReceived() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            andConnectionNotSuccessful(dummyClientActor);

            expectMsgClass(Status.Failure.class);

            thenExpectConnectClientCalled();
            thenExpectConnectClientCalledAfterTimeout(connectivityConfig.getClientConfig().getConnectingMinTimeout());
        }};
    }

    @Test
    public void reconnectsInConnectingStateAfterBackoffWhenMultipleFailuresAreReceived() {
        // expect reconnects after 100ms + 200ms + 400ms + 400ms = 1100ms
        final long expectedTotalBackoffMs = 1100L;
        reconnectsAfterBackoffWhenMultipleFailuresReceived(false, expectedTotalBackoffMs);
    }

    @Test
    public void reconnectsFromConnectedStateAfterBackoffWhenMultipleFailuresAreReceived() {
        // expect reconnects after 200ms + 400ms + 400ms + 400ms = 1400ms backoff in total
        // because we transition from CONNECTED -> CONNECTING which already adds 100ms backoff
        final long expectedTotalBackoffMs = 1400L;
        reconnectsAfterBackoffWhenMultipleFailuresReceived(true, expectedTotalBackoffMs);
    }

    private void reconnectsAfterBackoffWhenMultipleFailuresReceived(final boolean initialConnectionSucceeds,
            final long expectedTotalBackoffMs) {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);
            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();

            if (initialConnectionSucceeds) {
                // simulate initial connection succeeds i.e. state is CONNECTED when first failure occurs
                andConnectionSuccessful(dummyClientActor, getRef());
                expectMsg(CONNECTED_STATUS);
                andConnectionNotSuccessful(dummyClientActor);
                thenExpectConnectClientCalledAfterTimeout(
                        connectivityConfig.getClientConfig().getConnectingMinTimeout());
            }

            final int nrOfBackoffs = 4;
            final long start = System.currentTimeMillis();
            for (int i = 0; i < nrOfBackoffs; i++) {
                // BaseClientActor receives multiple failures while backing off in CONNECTING state
                andConnectionNotSuccessful(dummyClientActor);
                andConnectionNotSuccessful(dummyClientActor);
                andConnectionNotSuccessful(dummyClientActor);

                // verify that doConnectClient is called after correct backoff
                thenExpectConnectClientCalledAfterTimeout(i + 2, connectivityConfig.getClientConfig().getMaxBackoff());
            }
            final long totalBackoffDurationMs = System.currentTimeMillis() - start;
            final long tolerancePerBackoffMs = 100L; // allow 100ms tolerance per backoff until connectClient is called
            assertThat(totalBackoffDurationMs).isGreaterThan(expectedTotalBackoffMs);
            assertThat(totalBackoffDurationMs).isLessThan(
                    expectedTotalBackoffMs + (nrOfBackoffs * tolerancePerBackoffMs));
        }};
    }

    @Test
    public void handlesCloseConnectionInConnectingState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
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
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            final TestProbe probe1 = TestProbe.apply(actorSystem);
            final TestProbe probe2 = TestProbe.apply(actorSystem);

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    probe1.ref());
            thenExpectConnectClientCalled();

            // send another OpenConnection command while connecting
            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    probe2.ref());

            andConnectionSuccessful(dummyClientActor, probe1.ref());

            // both recipients get responses
            probe1.expectMsg(CONNECTED_STATUS);
            probe2.expectMsg(CONNECTED_STATUS);
        }};
    }

    @Test
    public void handlesCloseConnectionInDisconnectingState() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

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
            final Props props =
                    DummyClientActor.props(connection, actorSystem.deadLetters(), getRef(), getRef(), delegate);

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
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

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
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
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
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());

            expectMsgClass(Status.Failure.class);

            thenExpectCleanupResourcesCalled();
            Mockito.clearInvocations(delegate);
            thenExpectCleanupResourcesCalledAfterTimeout(
                    connectivityConfig.getClientConfig().getConnectingMinTimeout());
            thenExpectNoConnectClientCalled();
        }};
    }

    @Test
    public void doesNotReconnectIfConnectionSuccessful() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
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
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            thenExpectConnectClientCalledAfterTimeout(Duration.ofSeconds(5L));
            Mockito.clearInvocations(delegate);
            andConnectionSuccessful(dummyClientActor, getRef());
        }};
    }

    @Test
    public void dispatchesSearchCommandsAccordingToSubscriptionIdPrefix() {
        new TestKit(actorSystem) {{
            final int clientCount = 12; // 12 clients fit inside 1 hexadecimal digit
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId).toBuilder()
                    .clientCount(clientCount)
                    .build();
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef underTest = watch(actorSystem.actorOf(props, "zToBeLargerThanTestProbeRefs"));
            expectMsg(underTest);

            final List<TestProbe> probes = new ArrayList<>(clientCount - 1);
            for (int i = 0; i < clientCount - 1; ++i) {
                final TestProbe ithProbe = TestProbe.apply("aProbe" + Integer.toHexString(i), actorSystem);
                probes.add(ithProbe);
                underTest.tell(ithProbe.ref(), ActorRef.noSender());
            }

            for (int i = 0; i < probes.size(); ++i) {
                final String prefix = Integer.toHexString(i);
                final String subscriptionId = prefix + "-subscription-id";
                final CancelSubscription command = CancelSubscription.of(subscriptionId, DittoHeaders.empty());
                underTest.tell(InboundSignal.of(command), getRef());
                probes.get(i).expectMsg(command);
            }
        }};
    }

    @Test
    public void doesNotDispatchSearchCommandsWithInvalidSubscriptionId() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Connection connection = TestConstants.createConnection(connectionId).toBuilder()
                    .clientCount(2)
                    .build();
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), getRef(), delegate);

            final ActorRef underTest = watch(actorSystem.actorOf(props, "underTest"));
            expectMsg(underTest);

            final String prefix = Integer.toHexString(5); // out-of-bound
            final String subscriptionId = prefix + "-subscription-id";
            final CancelSubscription command = CancelSubscription.of(subscriptionId, DittoHeaders.empty());
            underTest.tell(InboundSignal.of(command), getRef());
            expectNoMessage();
        }};
    }

    @Test
    public void sendsConnectionOpenedAnnouncementAfterConnect() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection = createConnectionWithConnectivityAnnouncementTarget(randomConnectionId);
            final TestProbe publisherActor = TestProbe.apply(getSystem());
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), publisherActor.ref(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());
            expectMsg(CONNECTED_STATUS);
            thenExpectConnectionOpenedAnnouncement(publisherActor, randomConnectionId);
        }};
    }

    @Test
    public void sendsConnectionOpenedAnnouncementAfterReconnect() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection = createConnectionWithConnectivityAnnouncementTarget(randomConnectionId);
            final TestProbe publisherActor = TestProbe.apply(getSystem());
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), publisherActor.ref(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());
            expectMsg(CONNECTED_STATUS);
            thenExpectConnectionOpenedAnnouncement(publisherActor, randomConnectionId);

            andConnectionFails(dummyClientActor, getRef());
            // not expecting a closed announcement after connection failure, since it's not possible to send a message
            // if connecting is failed and thus not connected

            andConnectionSuccessful(dummyClientActor, getRef());

            thenExpectConnectionOpenedAnnouncement(publisherActor, randomConnectionId);
        }};
    }

    @Test
    public void sendsConnectionClosedAnnouncementWhenConnectionGetsClosed() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection = createConnectionWithConnectivityAnnouncementTarget(randomConnectionId);
            final TestProbe publisherActor = TestProbe.apply(getSystem());
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), publisherActor.ref(), delegate);

            final ActorRef dummyClientActor = watch(actorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());
            expectMsg(CONNECTED_STATUS);
            thenExpectConnectionOpenedAnnouncement(publisherActor, randomConnectionId);

            andClosingConnection(dummyClientActor, CloseConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());

            thenExpectDisconnectClientNotCalledForAtLeast(
                    connectivityConfig.getClientConfig().getConnectingMinTimeout().dividedBy(2L));
            thenExpectConnectionClosedAnnouncement(publisherActor, randomConnectionId);
            thenExpectDisconnectClientCalled();
        }};
    }

    @Test
    public void sendsConnectionClosedAnnouncementBeforeSystemShutdown() {
        final ActorSystem closableActorSystem =
                ActorSystem.create("AkkaTestSystem-closableActorSystem", TestConstants.CONFIG);
        new TestKit(closableActorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connection = createConnectionWithConnectivityAnnouncementTarget(randomConnectionId);
            final TestProbe publisherActor = TestProbe.apply(getSystem());
            final Props props = DummyClientActor.props(connection, getRef(), getRef(), publisherActor.ref(), delegate);

            final ActorRef dummyClientActor = watch(closableActorSystem.actorOf(props));

            whenOpeningConnection(dummyClientActor, OpenConnection.of(randomConnectionId, DittoHeaders.empty()),
                    getRef());
            thenExpectConnectClientCalled();
            andConnectionSuccessful(dummyClientActor, getRef());
            expectMsg(CONNECTED_STATUS);
            thenExpectConnectionOpenedAnnouncement(publisherActor, randomConnectionId);

            getSystem().terminate();
            thenExpectConnectionClosedAnnouncement(publisherActor, randomConnectionId);
        }};

        closableActorSystem.terminate();
    }

    private Connection createConnectionWithConnectivityAnnouncementTarget(final ConnectionId connectionId) {
        final Target target = ConnectivityModelFactory.newTargetBuilder()
                .address("/")
                .topics(Collections.singleton(
                        ConnectivityModelFactory.newFilteredTopicBuilder(Topic.CONNECTION_ANNOUNCEMENTS).build()))
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                                AuthorizationSubject.newInstance("nginx:ditto")))
                .build();
        return TestConstants.createConnection(connectionId, target);
    }

    private void thenExpectConnectionOpenedAnnouncement(final TestProbe publisherActorProbe,
            final ConnectionId connectionId) {
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(connectionId, Instant.now(), DittoHeaders.empty());

        thenExpectConnectivityAnnouncement(publisherActorProbe, announcement);
    }

    private void thenExpectConnectionClosedAnnouncement(final TestProbe publisherActorProbe,
            final ConnectionId connectionId) {
        final ConnectionClosedAnnouncement announcement =
                ConnectionClosedAnnouncement.of(connectionId, Instant.now(), DittoHeaders.empty());

        thenExpectConnectivityAnnouncement(publisherActorProbe, announcement);
    }

    private void thenExpectConnectivityAnnouncement(final TestProbe publisherActorProbe,
            final ConnectivityAnnouncement<?> announcement) {
        final Adaptable expectedAdaptable = SignalMapperFactory.newConnectivityAnnouncementSignalMapper()
                .mapSignalToAdaptable(announcement, TopicPath.Channel.NONE);

        final OutboundSignal.MultiMapped outboundSignal =
                publisherActorProbe.expectMsgClass(OutboundSignal.MultiMapped.class);
        final Adaptable sentAdaptable = outboundSignal.first().getAdaptable();

        assertThat(sentAdaptable.getTopicPath()).isEqualTo(expectedAdaptable.getTopicPath());
    }

    private void thenExpectConnectClientCalled() {
        thenExpectConnectClientCalledAfterTimeout(DEFAULT_MESSAGE_TIMEOUT);
    }

    private void thenExpectDisconnectClientNotCalledForAtLeast(final Duration duration) {
        verify(delegate, timeout(duration.toMillis()).times(0))
                .doDisconnectClient(any(Connection.class), nullable(ActorRef.class), anyBoolean());
    }

    private void thenExpectDisconnectClientCalled() {
        verify(delegate, timeout(DISCONNECT_TIMEOUT.toMillis())).doDisconnectClient(any(Connection.class),
                nullable(ActorRef.class), anyBoolean());
    }

    private void thenExpectConnectClientCalledAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).atLeastOnce())
                .doConnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectConnectClientCalledOnceAfterTimeout(final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).times(1))
                .doConnectClient(any(Connection.class), nullable(ActorRef.class));
    }

    private void thenExpectConnectClientCalledAfterTimeout(final int invocations,
            final Duration connectingTimeout) {
        verify(delegate, timeout(connectingTimeout.toMillis() + 200).times(invocations))
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
        verify(delegate, timeout(connectingTimeout.toMillis() + 500).atLeastOnce()).cleanupResourcesForConnection();
    }

    private static void whenOpeningConnection(final ActorRef clientActor, final OpenConnection openConnection,
            final ActorRef sender) {

        clientActor.tell(openConnection, sender);
    }

    private static void andClosingConnection(final ActorRef clientActor, final CloseConnection closeConnection,
            final ActorRef sender) {
        clientActor.tell(closeConnection, sender);
    }

    private static void andConnectionSuccessful(final ActorRef clientActor, final ActorRef origin) {
        clientActor.tell((ClientConnected) () -> Optional.of(origin), clientActor);
    }

    private static void andConnectionFails(final ActorRef clientActor, final ActorRef origin) {
        clientActor.tell(ConnectionFailure.of(null, null, null), clientActor);
    }

    private static void andDisconnectionSuccessful(final ActorRef clientActor, final ActorRef origin) {
        clientActor.tell(ClientDisconnected.of(origin, false), clientActor);
    }

    private static void andConnectionNotSuccessful(final ActorRef clientActor) {
        clientActor.tell(ConnectionFailure.of(null, null, "expected exception"), clientActor);
    }

    private static void andStateTimeoutSent(final ActorRef clientActor) {
        clientActor.tell(FSM.StateTimeout$.MODULE$, clientActor);
    }

    private static final class DummyClientActor extends BaseClientActor {

        private final ActorRef publisherActor;
        private final BaseClientActor delegate;

        public DummyClientActor(final Connection connection,
                final ActorRef connectionActor,
                final ActorRef proxyActor,
                final ActorRef publisherActor,
                final BaseClientActor delegate) {

            super(connection, proxyActor, connectionActor, DittoHeaders.empty(), ConfigFactory.empty());
            this.publisherActor = publisherActor;
            this.delegate = delegate;
        }

        /**
         * Creates Akka configuration object for this actor.
         *
         * @param connection the connection.
         * @param connectionActor the connectionPersistenceActor which created this client.
         * @param proxyActor the actor used to send signals into the ditto cluster.
         * @param publisherActor the actor that publishes to external system
         * @return the Akka configuration Props object.
         */
        public static Props props(final Connection connection, final ActorRef connectionActor,
                final ActorRef proxyActor,
                final ActorRef publisherActor, final BaseClientActor delegate) {
            return Props.create(DummyClientActor.class, connection, connectionActor, proxyActor,
                    publisherActor, delegate);
        }

        @Override
        protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
            logger.withCorrelationId(testConnectionCommand)
                    .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, testConnectionCommand.getEntityId())
                    .info("doTestConnection");
            return delegate.doTestConnection(testConnectionCommand);
        }

        @Override
        protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
            logger.info("allocateResourcesOnConnection");
            delegate.allocateResourcesOnConnection(clientConnected);
        }

        @Override
        protected void cleanupResourcesForConnection() {
            logger.info("cleanupResourcesForConnection");
            delegate.cleanupResourcesForConnection();
        }

        @Override
        protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
            logger.info("doConnectClient");
            delegate.doConnectClient(connection, origin);
        }

        @Override
        protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
                final boolean shutdownAfterDisconnect) {
            logger.info("doDisconnectClient");
            delegate.doDisconnectClient(connection, origin, false);
        }

        @Override
        protected CompletionStage<Status.Status> startPublisherActor() {
            return CompletableFuture.completedFuture(DONE);
        }

        @Nullable
        @Override
        protected ActorRef getPublisherActor() {
            return publisherActor;
        }

    }

}
