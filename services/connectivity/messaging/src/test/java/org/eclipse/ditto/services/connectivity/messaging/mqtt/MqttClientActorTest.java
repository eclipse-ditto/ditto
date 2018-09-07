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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

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
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;


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
    public void initializeConnection() {
        connectionId = TestConstants.createRandomConnectionId();
        serverHost = "tcp://localhost:" + freePort.getPort();
        connection =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT, ConnectionStatus.OPEN,
                        serverHost)
                        .sources(singletonList(
                                newMqttSource(1, 1, AUTHORIZATION_CONTEXT, 1, SOURCE_ADDRESS)))
                        .targets(singleton(TARGET))
                        .failoverEnabled(true)
                        .build();
    }

    @Test
    public void testConnect() {
        new TestKit(actorSystem) {{
            final Props props = mqttClientActor(connection, getRef(), MockMqttConnectionFactory.with(getRef()));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeFromTopic() {
        testConsumeModifyThing(connection, SOURCE_ADDRESS)
                .expectMsgClass(ModifyThing.class);
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcement() {
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
        testConsumeModifyThing(connectionWithEnforcement, "eclipse/ditto/thing")
                .expectMsgClass(ModifyThing.class);
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcementExpectErrorResponse() {
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

        final MqttMessage message = testConsumeModifyThing(connectionWithEnforcement, "eclipse/invalid/address")
                .expectMsgClass(MqttMessage.class);

        final String payload = new String(message.payload().toByteBuffer().array(), UTF_8);

        assertThat(payload).contains(IdEnforcementFailedException.ERROR_CODE);
    }

    private TestKit testConsumeModifyThing(final Connection connection, final String publishTopic) {
        return new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = mqttClientActor(connection, getRef(),
                    MockMqttConnectionFactory.with(getRef(), mqttMessage(publishTopic, TestConstants.modifyThing())));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testReconnectAndConsumeFromTopic() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = mqttClientActor(connection, getRef(),
                    MockMqttConnectionFactory.with(getRef(), mqttMessage(SOURCE_ADDRESS, TestConstants.modifyThing())));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            // ModifyThing automatically published by mock connection
            expectMsgClass(ModifyThing.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            // ModifyThing automatically published by mock connection
            expectMsgClass(ModifyThing.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMultipleSources() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);

            final List<String> irrelevantTopics = Arrays.asList("irrelevant", "topics");
            final String[] subscriptions =
                    new String[]{"A1", "A1", "A1", "B1", "B1", "B2", "B2", "C1", "C2", "C3"};
            final MqttMessage[] mockMessages =
                    Stream.concat(irrelevantTopics.stream(), Arrays.stream(subscriptions))
                            .map(topic -> mqttMessage(topic, TestConstants.modifyThing()))
                            .toArray(MqttMessage[]::new);

            final Connection multipleSources =
                    ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT,
                            ConnectionStatus.OPEN, serverHost)
                            .sources(Arrays.asList(
                                    newMqttSource(3, 1, AUTHORIZATION_CONTEXT, 1, "A1"),
                                    newMqttSource(2, 2, AUTHORIZATION_CONTEXT, 1, "B1", "B2"),
                                    newMqttSource(1, 3, AUTHORIZATION_CONTEXT, 1, "C1", "C2", "C3"))
                            )
                            .build();

            final String connectionId = TestConstants.createRandomConnectionId();
            final Props props = mqttClientActor(multipleSources, getRef(),
                    MockMqttConnectionFactory.with(getRef(), mockMessages));
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            final List<String> receivedTopics = new LinkedList<>();
            IntStream.range(0, subscriptions.length).forEach(i -> {
                LOGGER.info("Consuming message {}", i);
                final String topic = expectMsgClass(ModifyThing.class).getDittoHeaders().get("mqtt.topic");
                LOGGER.info("Got message with topic {}", topic);
                receivedTopics.add(topic);
            });

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);

            assertThat(receivedTopics).containsExactlyInAnyOrder(subscriptions);
        }};
    }

    @Test
    public void testPublishToTopic() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = mqttClientActor(connection, getRef(), MockMqttConnectionFactory.with(getRef()));
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singleton(""));
            final String expectedJson = TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent);

            LOGGER.info("Sending thing modified message: {}", thingModifiedEvent);
            final OutboundSignal.WithExternalMessage mappedSignal =
                    Mockito.mock(OutboundSignal.WithExternalMessage.class);
            final ExternalMessage externalMessage =
                    ConnectivityModelFactory.newExternalMessageBuilder(new HashMap<>()).withText(expectedJson).build();
            when(mappedSignal.getExternalMessage()).thenReturn(externalMessage);
            when(mappedSignal.getTargets()).thenReturn(singleton(TARGET));
            underTest.tell(mappedSignal, getRef());

            final MqttMessage receivedMessage = expectMsgClass(MqttMessage.class);
            LOGGER.info("Got thing modified message at topic {}", receivedMessage.topic());

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);

            assertThat(receivedMessage.topic()).isEqualTo(TARGET.getAddress());
            assertThat(receivedMessage.payload()).isEqualTo(ByteString.fromString(expectedJson));
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props = mqttClientActor(connection, getRef(), MockMqttConnectionFactory.with(getRef()));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
        }};
    }

    @Test
    public void testTestConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props = mqttClientActor(connection, getRef(),
                    MockMqttConnectionFactory.withError(getRef(), new StreamCorruptedException("Psalms 38:5")));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);
        }};
    }

    @Test
    public void testRetrieveConnectionMetrics() {
        new TestKit(actorSystem) {
            {
                final Connection connectionWithAdditionalSources = connection.toBuilder()
                        .sources(singletonList(
                                ConnectivityModelFactory.newMqttSource(1, 2, AUTHORIZATION_CONTEXT, 1,
                                        "topic1", "topic2"))).build();
                final String modifyThing = TestConstants.modifyThing();

                final Props props = mqttClientActor(connectionWithAdditionalSources, getRef(),
                        MockMqttConnectionFactory.with(getRef(), mqttMessage(SOURCE_ADDRESS, modifyThing)));
                final ActorRef underTest = actorSystem.actorOf(props);

                final TestProbe controlProbe = TestProbe.apply(actorSystem);
                underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), controlProbe.ref());
                controlProbe.expectMsg(CONNECTED_SUCCESS);

                expectMsgClass(ModifyThing.class);

                underTest.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

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

    private static Props mqttClientActor(final Connection connection, final ActorRef testProbe,
            final BiFunction<Connection, DittoHeaders, MqttConnectionFactory> factoryCreator) {

        return Props.create(MqttClientActor.class, () ->
                new MqttClientActor(connection, connection.getConnectionStatus(), testProbe, factoryCreator));
    }

    private static MqttMessage mqttMessage(final String topic, final String payload) {
        return MqttMessage.create(topic, ByteString.fromArray(payload.getBytes(UTF_8)));
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

    /**
     * Fools the fast-failing mechanism of BaseClientActor so that MqttClientActor can be tested.
     * BaseClientActor does not attempt to connect if the host address of the connection URI is not reachable.
     */
    private static class MqttServerRule extends ExternalResource {

        private final int port;

        @Nullable
        private ServerSocket serverSocket;

        private MqttServerRule(final int port) {
            LOGGER.info("Starting server at port {}", port);
            this.port = port;
        }

        @Override
        protected void before() throws Exception {
            serverSocket = new ServerSocket(port);
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
        }

        @Override
        protected void after() {
            try {
                if (serverSocket != null) {
                    // should complete future exceptionally via IOException in dispatcher thread
                    serverSocket.close();
                }
            } catch (final IOException e) {
                // don't care; next test uses a new port.
            }
        }
    }
}
