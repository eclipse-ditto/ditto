/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newFilteredMqttSource;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newMqttSource;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newMqttTarget;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.IdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.MqttSource;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.OutboundSignal;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.server.Server;
import io.moquette.server.config.MemoryConfig;


@RunWith(MockitoJUnitRunner.class)
public class MqttClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClientActorTest.class);

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final Target TARGET =
            newMqttTarget("target", AUTHORIZATION_CONTEXT, 1, Topic.TWIN_EVENTS);
    private static final String SOURCE_ADDRESS = "source";

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;
    private String connectionId;
    private static Connection connection;
    private String serverHost;

    private final FreePort freePort = new FreePort();
    @Rule public final MqttServerRule mqttServer = new MqttServerRule(freePort.getPort());
    @Rule public final MqttClientRule mqttClient = new MqttClientRule(freePort.getPort());

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @Before
    public void startServer() {
        connectionId = TestConstants.createRandomConnectionId();
        serverHost = "tcp://localhost:" + freePort.getPort();
        connection =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT, ConnectionStatus.OPEN,
                        serverHost)
                        .sources(singletonList(
                                newMqttSource(1, 1, AUTHORIZATION_CONTEXT, 1, SOURCE_ADDRESS)))
                        .targets(singleton(TARGET))
                        .build();
    }

    @Test
    public void testConnect() {
        new TestKit(actorSystem) {{
            final Props props = MqttClientActor.props(connection, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);
            watch(mqttClientActor);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // wait for consumer + publisher + test client
            Awaitility.await().untilAtomic(mqttServer.getConnectionCount(), equalTo(3L));

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeFromTopic() throws MqttException {
        testConsumeFromTopic(connection, SOURCE_ADDRESS, true);
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcement() throws MqttException {
        final MqttSource mqttSource = newFilteredMqttSource(1, 1, AUTHORIZATION_CONTEXT,
                "eclipse/{{ thing:namespace }}/{{ thing:name }}",
                1,
                "eclipse/+/+");
        final Connection connectionWithEnforcement =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT,
                        ConnectionStatus.OPEN,
                        serverHost)
                        .sources(singletonList(mqttSource))
                        .build();
        testConsumeFromTopic(connectionWithEnforcement, "eclipse/ditto/thing", true);
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcementExpectErrorResponse() throws MqttException {
        final MqttSource mqttSource = newFilteredMqttSource(1, 1, AUTHORIZATION_CONTEXT,
                "eclipse/{{ thing:namespace }}/{{ thing:name }}", // enforcement filter
                1, // qos
                "eclipse/+/+" // subscribed topic
        );

        final Connection connectionWithEnforcement =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT,
                        ConnectionStatus.OPEN,
                        serverHost)
                        .sources(singletonList(mqttSource))
                        .build();

        final AtomicBoolean receivedErrorResponse = new AtomicBoolean(false);
        mqttClient.subscribeWithResponse("replies", 1, (topic, message) -> {
            assertThat(new String(message.getPayload())).contains(IdEnforcementFailedException.ERROR_CODE);
            receivedErrorResponse.set(true);
        }).waitForCompletion();

        testConsumeFromTopic(connectionWithEnforcement, "eclipse/invalid/address", false);

        Awaitility.await().untilTrue(receivedErrorResponse);
    }

    private void testConsumeFromTopic(final Connection connection, final String publishTopic,
            final boolean expectSuccess) throws MqttException {
        new TestKit(actorSystem) {{

            final Props props = MqttClientActor.props(connection, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            watch(mqttClientActor);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // wait for consumer + publisher + test client
            Awaitility.await().untilAtomic(mqttServer.getConnectionCount(), equalTo(3L));

            mqttClient.publish(publishTopic, TestConstants.modifyThing().getBytes(UTF_8), 0, false);

            if (expectSuccess) {
                expectMsgClass(ModifyThing.class);
            }
        }};
    }

    @Test
    public void testReconnectAndConsumeFromTopic() throws MqttException {
        new TestKit(actorSystem) {{
            final Props props = MqttClientActor.props(connection, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);
            watch(mqttClientActor);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // wait for consumer + publisher + test client
            Awaitility.await().untilAtomic(mqttServer.getConnectionCount(), equalTo(3L));

            final String modifyThing = TestConstants.modifyThing();

            mqttClient.publish(SOURCE_ADDRESS, modifyThing.getBytes(UTF_8), 0, false);

            expectMsgClass(ModifyThing.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            mqttClient.publish(SOURCE_ADDRESS, modifyThing.getBytes(UTF_8), 0, false);
            expectMsgClass(ModifyThing.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMultipleTopics() {
        new TestKit(actorSystem) {{

            final List<String> subscriptions =
                    Arrays.asList("A1", "A1", "A1", "B1", "B1", "B2", "B2", "C1", "C2", "C3");
            final Collection<String> expectedSubscriptions = new ConcurrentSkipListSet<>(subscriptions);
            final AtomicLong connected = new AtomicLong(0);

            mqttServer.addInterceptHandler(new AbstractInterceptHandler() {
                @Override
                public String getID() {
                    return connectionId;
                }

                @Override
                public void onSubscribe(final InterceptSubscribeMessage msg) {
                    expectedSubscriptions.remove(msg.getTopicFilter());
                }
            });

            final Connection multipleSources =
                    ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT,
                            ConnectionStatus.OPEN, serverHost)
                            .sources(Arrays.asList(
                                    newFilteredMqttSource(3, 1, AUTHORIZATION_CONTEXT, "filter", 1, "A1"),
                                    newFilteredMqttSource(2, 2, AUTHORIZATION_CONTEXT, "filter", 1, "B1", "B2"),
                                    newFilteredMqttSource(1, 3, AUTHORIZATION_CONTEXT, "filter", 1, "C1", "C2", "C3"))
                            )
                            .build();

            final String connectionId = TestConstants.createRandomConnectionId();
            final Props props = MqttClientActor.props(multipleSources, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            Awaitility.await().until(expectedSubscriptions::isEmpty);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            Awaitility.await().untilAtomic(connected, equalTo(0L));
        }};
    }

    @Test
    public void testPublishToTopic() throws MqttException {
        new TestKit(actorSystem) {{
            final Props props = MqttClientActor.props(connection, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);
            watch(mqttClientActor);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // wait for consumer + publisher + test client
            Awaitility.await().untilAtomic(mqttServer.getConnectionCount(), equalTo(3L));

            final AtomicReference<MqttMessage> receivedMessage = new AtomicReference<>();
            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singleton(""));
            final String expectedJson = TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent);

            mqttClient.subscribeWithResponse(TARGET.getAddress(), 0, (topic, message) -> {
                final String received = new String(message.getPayload(), UTF_8);
                LOGGER.info("Received message in test client on topic <{}>: {}", topic, received);
                receivedMessage.set(message);
            }).waitForCompletion();

            LOGGER.info("Sending thing modified message: {}", thingModifiedEvent);
            final OutboundSignal.WithExternalMessage mappedSignal =
                    Mockito.mock(OutboundSignal.WithExternalMessage.class);
            final ExternalMessage externalMessage =
                    ConnectivityModelFactory.newExternalMessageBuilder(new HashMap<>()).withText(expectedJson).build();
            when(mappedSignal.getExternalMessage()).thenReturn(externalMessage);
            when(mappedSignal.getTargets()).thenReturn(singleton(TARGET));
            mqttClientActor.tell(mappedSignal, getRef());

            Awaitility.await().until(() -> {
                if (receivedMessage.get() == null) {
                    LOGGER.info("No message received so far...");
                    return false;
                }
                return expectedJson.equals(new String(receivedMessage.get().getPayload(), UTF_8));
            });

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props = MqttClientActor.props(connection, getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);
            watch(mqttClientActor);

            mqttClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
        }};
    }

    @Test
    public void testTestConnectionFails() throws Exception {
        new TestKit(actorSystem) {{

            final int newPort = new FreePort().getPort();
            final Connection newConnection = connection.toBuilder()
                    .uri("tcp://localhost:" + newPort)
                    .build();

            try (final ServerSocket serverSocket = new ServerSocket(newPort)) {
                // start server that closes accepted socket immediately
                // so that failure is not triggered by connection failure shortcut
                CompletableFuture.runAsync(() -> {
                    while (true) {
                        try (final Socket socket = serverSocket.accept()) {
                            LOGGER.info("Incoming connection to port {} accepted at port {} ",
                                    serverSocket.getLocalPort(),
                                    socket.getPort());
                        } catch (final IOException e) {
                            // server socket closed, quitting.
                            break;
                        }
                    }
                });

                final Props props = MqttClientActor.props(newConnection, getRef());
                final ActorRef mqttClientActor = actorSystem.actorOf(props);

                mqttClientActor.tell(TestConnection.of(newConnection, DittoHeaders.empty()), getRef());
                final Status.Failure failure = expectMsgClass(Status.Failure.class);
                assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);
            }
        }};
    }

    @Test
    public void testRetrieveConnectionMetrics() throws MqttException {
        new TestKit(actorSystem) {
            {
                final Connection connectionWithAdditionalSources = connection.toBuilder()
                        .sources(singletonList(
                                ConnectivityModelFactory.newMqttSource(1, 2, AUTHORIZATION_CONTEXT, 1,
                                        "topic1", "topic2"))).build();

                final Props props = MqttClientActor.props(connectionWithAdditionalSources, getRef());
                final ActorRef mqttClientActor = actorSystem.actorOf(props);

                // wait for 2 consumers + publisher + test client
                Awaitility.await().untilAtomic(mqttServer.getConnectionCount(), equalTo(4L));

                final String modifyThing = TestConstants.modifyThing();
                mqttClient.publish(SOURCE_ADDRESS, modifyThing.getBytes(UTF_8), 0, false);
                expectMsgClass(ModifyThing.class);

                mqttClientActor.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

                final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse =
                        expectMsgClass(RetrieveConnectionMetricsResponse.class);

                final ConnectionMetrics connectionMetrics = retrieveConnectionMetricsResponse.getConnectionMetrics();
                assertThat((Object) connectionMetrics.getConnectionStatus()).isEqualTo(ConnectionStatus.OPEN);
                assertThat(connectionMetrics.getClientState()).isEqualTo(BaseClientState.CONNECTED.name());
                assertThat(findSourceAddressMetricForTopic(connectionMetrics, SOURCE_ADDRESS))
                        .hasValueSatisfying(m -> assertThat(m.getMessageCount()).isEqualTo(1L));
                assertThat(findSourceMetricsForTopic(connectionMetrics, SOURCE_ADDRESS))
                        .hasValueSatisfying(m -> assertThat(m.getConsumedMessages()).isEqualTo(1L));
                assertThat(findSourceAddressMetricForTopic(connectionMetrics, "topic1")).isNotEmpty();
                assertThat(findSourceAddressMetricForTopic(connectionMetrics, "topic2")).isNotEmpty();
                assertThat(findTargetMetricsForTopic(connectionMetrics, TARGET.getAddress())).isNotEmpty();
            }

            private Optional<AddressMetric> findSourceAddressMetricForTopic(final ConnectionMetrics connectionMetrics,
                    final String address) {

                System.out.println(connectionMetrics.toJsonString());

                return connectionMetrics.getSourcesMetrics().stream()
                        .flatMap(metric -> metric.getAddressMetrics()
                                .entrySet()
                                .stream()
                                .filter(e -> e.getKey().contains(address))
                                .map(Map.Entry::getValue))
                        .findFirst();
            }

            private Optional<SourceMetrics> findSourceMetricsForTopic(final ConnectionMetrics connectionMetrics,
                    final String address) {
                return connectionMetrics.getSourcesMetrics().stream()
                        .filter(metric -> metric.getAddressMetrics().containsKey(address)).findFirst();
            }

            private Optional<TargetMetrics> findTargetMetricsForTopic(final ConnectionMetrics connectionMetrics,
                    final String address) {
                return connectionMetrics.getTargetsMetrics().stream()
                        .filter(metric -> metric.getAddressMetrics().keySet().contains(address))
                        .findFirst();
            }
        };
    }

    private static class FreePort {

        private final int port;

        private FreePort() {
            try (final ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            } catch (final IOException e) {
                LOGGER.info("Failed to find local port: " + e.getMessage());
                throw new IllegalStateException(e);
            }
        }

        private int getPort() {
            return port;
        }
    }

    private static class MqttClientRule extends ExternalResource {

        private final int port;
        private final String clientId;
        private final MqttClient mqttClient;

        private MqttClientRule(final int port) {
            this(port, "test-client");
        }

        private MqttClientRule(final int port, final String clientId) {
            this.port = port;
            this.clientId = clientId;
            try {
                mqttClient = new MqttClient("tcp://localhost:" + this.port, this.clientId);
            } catch (final MqttException e) {
                throw new IllegalStateException(e);
            }
        }

        private void publish(final String topic, final byte[] payload, final int qos, final boolean retained)
                throws MqttException {
            mqttClient.publish(topic, payload, qos, retained);
        }

        private IMqttToken subscribeWithResponse(final String topicFilter, final int qos,
                final IMqttMessageListener messageListener)
                throws MqttException {
            return mqttClient.subscribeWithResponse(topicFilter, qos, messageListener);
        }


        @Override
        protected void before() throws Throwable {
            mqttClient.connect();
        }

        @Override
        protected void after() {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close(true);
            } catch (final MqttException e) {
                LOGGER.error("Failed to disconnect: {}", e.getMessage(), e);
            }
        }
    }

    private static class MqttServerRule extends ExternalResource {

        private final String serverId = "TestServer-" + UUID.randomUUID();
        private final int port;
        private Server server;
        private final AtomicLong connectionCount = new AtomicLong(0);

        private MqttServerRule(final int port) {
            LOGGER.info("Starting server at port {}", port);
            this.port = port;
        }

        private AtomicLong getConnectionCount() {
            return connectionCount;
        }

        private void addInterceptHandler(final InterceptHandler interceptHandler) {
            server.addInterceptHandler(interceptHandler);
        }

        private void stop() {
            server.stopServer();
        }

        @Override
        protected void before() throws Throwable {
            final MemoryConfig memoryConfig = new MemoryConfig(new Properties());
            memoryConfig.setProperty("port", Integer.toString(port));
            server = new Server();
            server.startServer(memoryConfig);
            server.addInterceptHandler(new AbstractInterceptHandler() {
                @Override
                public String getID() {
                    return serverId;
                }

                @Override
                public void onConnect(final InterceptConnectMessage msg) {
                    connectionCount.incrementAndGet();
                }

                @Override
                public void onDisconnect(final InterceptDisconnectMessage msg) {
                    connectionCount.decrementAndGet();
                }
            });
        }

        @Override
        protected void after() {
            stop();
        }
    }
}
