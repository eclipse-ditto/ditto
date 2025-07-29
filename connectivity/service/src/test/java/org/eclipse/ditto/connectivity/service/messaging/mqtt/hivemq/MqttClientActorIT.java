/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.mqtt.IllegalSessionExpiryIntervalSecondsException;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;
import org.eclipse.ditto.internal.utils.test.docker.mosquitto.MosquittoResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.common.collect.Sets;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import scala.concurrent.duration.FiniteDuration;
import scala.jdk.CollectionConverters;

/**
 * Tests {@link MqttClientActor}.
 */
@RunWith(Parameterized.class)
public final class MqttClientActorIT {

    @Parameterized.Parameters(name = "MQTT version: {0}, separate publisher client: {1}, clean session: {2}")
    public static Collection<Object[]> parameters() {
        return Stream.of(MqttVersion.MQTT_3_1_1, MqttVersion.MQTT_5_0).flatMap(mqttVersion ->
                Stream.of(true, false).flatMap(separatePublisherClient ->
                        Stream.of(true, false).map(cleanSession ->
                                new Object[] { mqttVersion, separatePublisherClient, cleanSession })))
                .map(Object[].class::cast)
                .toList();
    }

    @Parameterized.Parameter(0)
    public static MqttVersion mqttVersion;

    @Parameterized.Parameter(1)
    public static Boolean separatePublisherClient;

    @Parameterized.Parameter(2)
    public static Boolean cleanSession;

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @ClassRule
    public static final MosquittoResource MOSQUITTO_RESOURCE = new MosquittoResource("mosquitto.conf");

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("connection");
    private static final String CLIENT_ID_DITTO = "ditto";
    private static final String TOPIC_NAME = "topic";
    private static final String ANOTHER_TOPIC_NAME = "topic2";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationModelFactory.newAuthSubject("nginx:ditto"));
    private static final FiniteDuration CONNECTION_TIMEOUT = FiniteDuration.apply(30, TimeUnit.SECONDS);
    private static final FiniteDuration COMMAND_TIMEOUT = FiniteDuration.apply(5, TimeUnit.SECONDS);
    private static final FiniteDuration NO_MESSAGE_TIMEOUT = FiniteDuration.apply(3, TimeUnit.SECONDS);
    private static final int MESSAGES_FROM_PREVIOUS_SESSION_TIMEOUT = 3;
    private static final SessionExpiryInterval SESSION_EXPIRY_INTERVAL;

    static {
        try {
            SESSION_EXPIRY_INTERVAL = SessionExpiryInterval.of(Duration.ofSeconds(60));
        } catch (IllegalSessionExpiryIntervalSecondsException e) {
            throw new RuntimeException(e);
        }
    }

    private static Config actorsTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Rule public TestName name = new TestName();

    private ActorSystem actorSystem;
    private TestProbe commandForwarderProbe;

    @BeforeClass
    public static void beforeClass() {
        actorsTestConfig = ConfigFactory.load("test.conf");
    }

    @Before
    public void before() {
        // Retry actor system creation if ports are in use
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                actorSystem = ActorSystem.create(getClass().getSimpleName(), actorsTestConfig);
                break;
            } catch (Exception e) {
                if (attempt == 2) throw e;
                try { Thread.sleep(1000 * (attempt + 1)); } catch (InterruptedException ie) { /* ignore */ }
            }
        }
        commandForwarderProbe = TestProbe.apply("commandForwarder", actorSystem);
        cleanPreviousSession();
    }

    private void cleanPreviousSession() {
        final var mqttClient = getMqttClient(getDittoClientId());
        mqttClient.cleanSession();
    }

    @After
    public void after() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void testSingleTopic() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));
            connect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);

            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testMultipleTopics() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME, ANOTHER_TOPIC_NAME }));
            connect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            expectMergeThingMessage(commandForwarderProbe, "key2", "test2");
            expectNoMessage(NO_MESSAGE_TIMEOUT);

            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testMultipleSources() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }, new String[] { ANOTHER_TOPIC_NAME }));
            connect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessages(commandForwarderProbe,
                    Map.of("key", "test",
                    "key2", "test2"));
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);

            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};}

    @Test
    public void testPersistentSession() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));

            // create session
            connect(underTest, this);
            disconnect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            connect(underTest, this);
            expectMergeThingMessageIfNotCleanSession(commandForwarderProbe, "key", "test");
            expectMergeThingMessageIfNotCleanSession(commandForwarderProbe, "key2", "test2");
            commandForwarderProbe.expectNoMsg(NO_MESSAGE_TIMEOUT);
            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testPersistentSessionWithMultipleSources() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }, new String[] { ANOTHER_TOPIC_NAME }));

            // create session
            connect(underTest, this);
            disconnect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key3", "test3");
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key2", "test2");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key4", "test4");
            mqttClient.disconnect();

            connect(underTest, this);
            expectMergeThingMessagesIfNotCleanSession(commandForwarderProbe,
                    Map.of("key", "test",
                            "key2", "test2",
                            "key3", "test3",
                            "key4", "test4"));
            commandForwarderProbe.expectNoMsg(NO_MESSAGE_TIMEOUT);
            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testPersistentSessionMessageFromTopicWhichIsNoLongerSubscribed() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));

            // create session for ditto client ID with subscription to 2 topics
            final var dittoMqttClient = getMqttClient(getDittoClientId());
            dittoMqttClient.connect(GenericMqttConnect.newInstance(false, KeepAliveInterval.defaultKeepAlive(), SESSION_EXPIRY_INTERVAL, ReceiveMaximum.defaultReceiveMaximum()));
            dittoMqttClient.subscribe(GenericMqttSubscribe.of(Set.of(
                    GenericMqttSubscription.newInstance(MqttTopicFilter.of(TOPIC_NAME), MqttQos.EXACTLY_ONCE),
                    GenericMqttSubscription.newInstance(MqttTopicFilter.of(ANOTHER_TOPIC_NAME), MqttQos.EXACTLY_ONCE))));
            dittoMqttClient.disconnect();

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            connect(underTest, this);
            expectMergeThingMessageIfNotCleanSession(commandForwarderProbe, "key", "test");
            commandForwarderProbe.expectNoMsg(NO_MESSAGE_TIMEOUT);
            disconnect(underTest, this);

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testSingleTopicAfterReconnect() throws InterruptedException {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));
            connect(underTest, this);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");

            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);

            disconnect(underTest, this);
            connect(underTest, this);
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);
            disconnect(underTest, this);
            mqttClient.disconnect();

            ensureAllMessagesAcknowledged();
        }};
    }

    private ActorRef getMqttClientActor(final Connection connection) {
        return actorSystem.actorOf(MqttClientActor.props(
                connection,
                commandForwarderProbe.ref(),
                TestProbe.apply("connectionActor", actorSystem).ref(),
                DittoHeaders.empty(),
                ConfigFactory.empty()));
    }

    private String getDittoClientId() {
        return CLIENT_ID_DITTO + "-" + name.getMethodName();
    }

    private Connection getConnection(final String[]... sourcesTopics) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        mqttVersion.equals(MqttVersion.MQTT_3_1_1) ? ConnectionType.MQTT : ConnectionType.MQTT_5,
                        ConnectivityStatus.CLOSED,
                        "tcp://" + MOSQUITTO_RESOURCE.getBindIp() + ":" + MOSQUITTO_RESOURCE.getPort())
                .specificConfig(Map.of(
                        "clientId", getDittoClientId(),
                        "cleanSession", String.valueOf(cleanSession),
                        "separatePublisherClient", String.valueOf(separatePublisherClient)))
                .setSources(Arrays.stream(sourcesTopics)
                        .map(topics -> ConnectivityModelFactory.newSourceBuilder()
                                .authorizationContext(AUTHORIZATION_CONTEXT)
                                .qos(MqttQos.EXACTLY_ONCE.getCode())
                                .addresses(Sets.newHashSet(topics))
                                .build())
                        .toList())
                .build();
    }

    private static void connect(final ActorRef underTest, final TestKit testKit) {
        underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), testKit.getRef());
        testKit.expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));
    }

    private static void disconnect(final ActorRef underTest, final TestKit testKit) {
        underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), testKit.getRef());
        testKit.expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));
    }

    private static void publishMergeThingMessage(final GenericBlockingMqttClient mqttClient, final String topic, final String key, final String value) {
        mqttClient.publish(GenericMqttPublish.builder(MqttTopic.of(topic), MqttQos.EXACTLY_ONCE)
                    .payload(ByteBuffer.wrap(String.format("""
{
    "topic": "test/thing-01/things/twin/commands/merge",
    "path": "/attributes/%s",
    "headers": {
        "content-type": "application/merge-patch+json",
        "requested-acks": []
    },
    "value": "%s"
}
""", key, value).getBytes(StandardCharsets.UTF_8)))
                    .contentType("application/vnd.eclipse.ditto+json")
                    .build()
        );
    }

    private static void sleep(final int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
        }
    }

    private GenericBlockingMqttClient getMqttClient(final String clientId) {
        return GenericBlockingMqttClientBuilder.newInstance(mqttVersion, MOSQUITTO_RESOURCE.getBindIp(), MOSQUITTO_RESOURCE.getPort())
                .clientIdentifier(clientId)
                .build();
    }

    private void expectMergeThingMessageIfNotCleanSession(final TestProbe testProbe, final String key, final String value) {
        if (!cleanSession) {
            expectMergeThingMessage(testProbe, key, value);
        }
    }

    private void expectMergeThingMessagesIfNotCleanSession(final TestProbe testProbe, final Map<String, String> updates) {
        if (!cleanSession) {
            expectMergeThingMessages(testProbe, updates);
        }
    }

    private void expectMergeThingMessage(final TestProbe testProbe, final String key, final String value) {
        final var command = testProbe.expectMsgClass(COMMAND_TIMEOUT, MergeThing.class);
        softly.assertThat((CharSequence) command.getEntityId())
                .as("entity ID")
                .isEqualTo(ThingId.of("test:thing-01"));
        softly.assertThat((CharSequence) command.getPath())
                .as("path")
                .isEqualTo(JsonFactory.newPointer(String.format("/attributes/%s", key)));
        softly.assertThat(command.getValue())
                .as("value")
                .isEqualTo(JsonFactory.newValue(value));
    }

    private void expectMergeThingMessages(final TestProbe testProbe, final Map<String, String> updates) {
        final var messages = CollectionConverters.SeqHasAsJava(testProbe.receiveN(updates.size(), COMMAND_TIMEOUT)).asJava();
        assertThat(messages).hasSize(updates.size());
        // Can't create map here, because in case of error the key might duplicate, and we'll get ugly error instead of
        // pretty assertion failure.
        final var actualUpdates = messages.stream()
                .filter(MergeThing.class::isInstance)
                .map(MergeThing.class::cast)
                .map(m -> Map.entry(
                        String.valueOf(m.getPath()).replace("/attributes/", ""),
                        m.getValue().asString()))
                .toList();
        assertThat(actualUpdates).containsExactlyInAnyOrderElementsOf(updates.entrySet());
    }

    private void ensureAllMessagesAcknowledged() {
        final var mqttClient = getMqttClient(getDittoClientId());

        final var unacknowledgedPublishes = new ArrayList<GenericMqttPublish>();
        mqttClient.setPublishesCallback(MqttGlobalPublishFilter.ALL, unacknowledgedPublishes::add);

        mqttClient.connect(GenericMqttConnect.newInstance(false, KeepAliveInterval.defaultKeepAlive(), SessionExpiryInterval.defaultSessionExpiryInterval(), ReceiveMaximum.defaultReceiveMaximum()));
        sleep(MESSAGES_FROM_PREVIOUS_SESSION_TIMEOUT);
        mqttClient.disconnect();

        assertThat(unacknowledgedPublishes).isEmpty();
    }

    private interface GenericBlockingMqttClient {
        void connect();
        void connect(final GenericMqttConnect connect);
        void disconnect();
        void cleanSession();
        void setPublishesCallback(final MqttGlobalPublishFilter mqttGlobalPublishFilter, final Consumer<GenericMqttPublish> callback);
        void publish(final GenericMqttPublish publish);
        void subscribe(final GenericMqttSubscribe subscribe);
    }

    private static class GenericBlockingMqttClientBuilder {
        private final MqttVersion mqttVersion;
        private final String host;
        private final Integer port;
        @Nullable private String clientId;

        private GenericBlockingMqttClientBuilder(final MqttVersion mqttVersion, final String host, final int port) {
            this.mqttVersion = mqttVersion;
            this.host = host;
            this.port = port;
        }

        public static GenericBlockingMqttClientBuilder newInstance(final MqttVersion mqttVersion, final String host, final int port) {
            return new GenericBlockingMqttClientBuilder(mqttVersion, host, port);
        }

        public GenericBlockingMqttClientBuilder clientIdentifier(final String clientId) {
            this.clientId = clientId;
            return this;
        }

        public GenericBlockingMqttClient build() {
            return mqttVersion == MqttVersion.MQTT_3_1_1 ?
                    new Mqtt3BlockingMqttClient(this) :
                    new Mqtt5BlockingMqttClient(this);
        }

        private static final class Mqtt3BlockingMqttClient implements GenericBlockingMqttClient {

            private final Mqtt3BlockingClient client;

            private Mqtt3BlockingMqttClient(final GenericBlockingMqttClientBuilder builder) {
                var mqtt3ClientBuilder = MqttClient.builder()
                        .useMqttVersion3()
                        .serverHost(builder.host)
                        .serverPort(builder.port);
                if (builder.clientId != null) {
                    mqtt3ClientBuilder = mqtt3ClientBuilder.identifier(builder.clientId);
                }

                client = mqtt3ClientBuilder.buildBlocking();
            }

            @Override
            public void connect() {
                client.connect();
            }

            @Override
            public void connect(final GenericMqttConnect connect) {
                client.connect(connect.getAsMqtt3Connect());
            }

            @Override
            public void disconnect() {
                client.disconnect();
            }

            @Override
            public void cleanSession() {
                client.connectWith().cleanSession(true).send();
                client.disconnect();
            }

            @Override
            public void setPublishesCallback(final MqttGlobalPublishFilter mqttGlobalPublishFilter, final Consumer<GenericMqttPublish> callback) {
                client.toAsync().publishes(mqttGlobalPublishFilter, p -> callback.accept(GenericMqttPublish.ofMqtt3Publish(p)));
            }

            @Override
            public void publish(final GenericMqttPublish publish) {
                client.publish(publish.getAsMqtt3Publish());
            }

            @Override
            public void subscribe(final GenericMqttSubscribe subscribe) {
                client.subscribe(subscribe.getAsMqtt3Subscribe());
            }
        }

        private static final class Mqtt5BlockingMqttClient implements GenericBlockingMqttClient {

            private final Mqtt5BlockingClient client;

            private Mqtt5BlockingMqttClient(final GenericBlockingMqttClientBuilder builder) {
                var mqtt5ClientBuilder = MqttClient.builder()
                        .useMqttVersion5()
                        .serverHost(builder.host)
                        .serverPort(builder.port);
                if (builder.clientId != null) {
                    mqtt5ClientBuilder = mqtt5ClientBuilder.identifier(builder.clientId);
                }

                client = mqtt5ClientBuilder.buildBlocking();
            }

            @Override
            public void connect() {
                client.connect();
            }

            @Override
            public void connect(final GenericMqttConnect connect) {
                client.connect(connect.getAsMqtt5Connect());
            }

            @Override
            public void disconnect() {
                client.disconnect();
            }

            @Override
            public void cleanSession() {
                client.connectWith().cleanStart(true).send();
                client.disconnect();
            }

            @Override
            public void setPublishesCallback(final MqttGlobalPublishFilter mqttGlobalPublishFilter, final Consumer<GenericMqttPublish> callback) {
                client.toAsync().publishes(mqttGlobalPublishFilter, p -> callback.accept(GenericMqttPublish.ofMqtt5Publish(p)));
            }

            @Override
            public void publish(final GenericMqttPublish publish) {
                client.publish(publish.getAsMqtt5Publish());
            }

            @Override
            public void subscribe(final GenericMqttSubscribe subscribe) {
                client.subscribe(subscribe.getAsMqtt5Subscribe());
            }
        }
    }

}
