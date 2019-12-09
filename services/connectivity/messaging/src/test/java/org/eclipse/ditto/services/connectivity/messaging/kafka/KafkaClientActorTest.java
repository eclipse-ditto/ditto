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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaClientActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
@RunWith(MockitoJUnitRunner.class)
public final class KafkaClientActorTest extends AbstractBaseClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClientActorTest.class);

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final String HOST = "localhost";
    private static final String TOPIC = "target";
    private static final Target TARGET = newTarget(TOPIC, AUTHORIZATION_CONTEXT, null, 0, Topic.TWIN_EVENTS);

    private static ActorSystem actorSystem;
    private static ServerSocket mockServer;

    private ConnectionId connectionId;
    private Connection connection;

    @Mock
    private KafkaPublisherActorFactory publisherActorFactory;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        startMockServer();
    }

    private static void startMockServer() {
        mockServer = TestConstants.newMockServer();
        LOGGER.info("Started mock server on port {}", mockServer.getLocalPort());
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
        stopMockServer();
    }

    private static void stopMockServer() {
        if (null != mockServer) {
            try {
                mockServer.close();
                LOGGER.info("Successfully closed mock server.");
            } catch (final IOException e) {
                LOGGER.info("Got unexpected exception while closing the mock server.", e);
            }
        } else {
            LOGGER.info("Could not stop mock server as it unexpectedly was <null>.");
        }
    }

    @Before
    public void initializeConnection() {
        connectionId = TestConstants.createRandomConnectionId();
        final String hostAndPort = HOST + ":" + mockServer.getLocalPort();
        final String serverHost = "tcp://" + hostAndPort;
        final Map<String, String> specificConfig = specificConfigWithBootstrapServers(hostAndPort);
        connection = ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.KAFKA,
                ConnectivityStatus.CLOSED, serverHost)
                .targets(singletonList(TARGET))
                .failoverEnabled(true)
                .specificConfig(specificConfig)
                .build();
    }

    @Test
    public void testConnect() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(getSystem());
            final Props props = getKafkaClientActorProps(probe.ref());
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            kafkaClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            expectPublisherReceivedShutdownSignal(probe);
        }};
    }

    @Test
    public void testPublishToTopic() {
        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(getSystem());
            final Props props = getKafkaClientActorProps(probe.ref());
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singleton(""));
            final String expectedJson = TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent);

            LOGGER.info("Sending thing modified message: {}", thingModifiedEvent);
            final OutboundSignal.Mapped mappedSignal = Mockito.mock(OutboundSignal.Mapped.class);
            when(mappedSignal.getTargets()).thenReturn(singletonList(TARGET));
            when(mappedSignal.getSource()).thenReturn(thingModifiedEvent);
            kafkaClientActor.tell(mappedSignal, getRef());

            final OutboundSignal.Mapped message =
                    probe.expectMsgClass(OutboundSignal.Mapped.class);
            assertThat(message.getExternalMessage().getTextPayload()).contains(expectedJson);

            kafkaClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            expectPublisherReceivedShutdownSignal(probe);
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef());
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
        }};
    }

    @Test
    public void testTestConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef(),
                    new Status.Failure(new IllegalStateException("just for testing")));
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testRetrieveConnectionMetrics() {
        new TestKit(actorSystem) {{
            final Props props = getKafkaClientActorProps(getRef());
            final ActorRef kafkaClientActor = actorSystem.actorOf(props);

            kafkaClientActor.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            kafkaClientActor.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionMetricsResponse.class);
        }};
    }

    private Props getKafkaClientActorProps(final ActorRef ref) {
        return getKafkaClientActorProps(ref, new Status.Success(Done.done()));
    }

    private Props getKafkaClientActorProps(final ActorRef ref, final Status.Status status) {
        return KafkaClientActor.props(connection, ref, new KafkaPublisherActorFactory() {
            @Override
            public String getActorName() {
                return "testPublisherActor";
            }

            @Override
            public Props props(final Connection c, final KafkaConnectionFactory factory, final boolean dryRun) {
                return MockKafkaPublisherActor.props(ref, status);
            }
        });
    }

    private static Map<String, String> specificConfigWithBootstrapServers(final String... hostAndPort) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("bootstrapServers", String.join(",", hostAndPort));
        return specificConfig;
    }

    @Override
    protected Connection getConnection() {
        return connection;
    }

    @Override
    protected Props createClientActor(final ActorRef conciergeForwarder) {
        return getKafkaClientActorProps(conciergeForwarder);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    private void expectPublisherReceivedShutdownSignal(final TestProbe probe) {
        probe.expectMsg(KafkaPublisherActor.GracefulStop.INSTANCE);
    }

    private static final class MockKafkaPublisherActor extends AbstractActor {

        private final ActorRef target;

        private MockKafkaPublisherActor(final ActorRef target, final Status.Status status) {
            this.target = target;
            getContext().getParent().tell(status, getSelf());
        }

        static Props props(final ActorRef target, final Status.Status status) {
            return Props.create(MockKafkaPublisherActor.class, target, status);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(any -> target.forward(any, getContext())).build();
        }

    }

}
