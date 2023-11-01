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
import java.util.*;
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
import org.eclipse.ditto.connectivity.model.*;
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
import org.junit.*;
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
    private static final String TOPIC_NAME = "data";
    private static final String ANOTHER_TOPIC_NAME = "data2";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationModelFactory.newAuthSubject("nginx:ditto"));
    private static final FiniteDuration CONNECTION_TIMEOUT = FiniteDuration.apply(30, TimeUnit.SECONDS);
    private static final FiniteDuration COMMAND_TIMEOUT = FiniteDuration.apply(5, TimeUnit.SECONDS);
    private static final FiniteDuration NO_MESSAGE_TIMEOUT = FiniteDuration.apply(3, TimeUnit.SECONDS);
    // https://github.com/eclipse-ditto/ditto/issues/1767
    private static final int TIMEOUT_BEFORE_PUBLISH = 3;
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
//        mongoClient = provideClientWrapper();
    }

    @Before
    public void before() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), actorsTestConfig);
        commandForwarderProbe = TestProbe.apply("commandForwarder", actorSystem);
        cleanPreviousSession();
//        thingsCollection = mongoClient.getDefaultDatabase().getCollection("PersistenceConstants.THINGS_COLLECTION_NAME");
    }

    private void cleanPreviousSession() {
        final var mqttClient = getMqttClient(CLIENT_ID_DITTO);
        mqttClient.cleanSession();
    }

    @After
    public void after() {
//        if (mongoClient != null) {
//            thingsCollection.drop();
//        }
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void testSingleTopic() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));
            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));

            sleep(TIMEOUT_BEFORE_PUBLISH);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            mqttClient.disconnect();

            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);

            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testMultipleTopics() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME, ANOTHER_TOPIC_NAME }));
            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));

            sleep(TIMEOUT_BEFORE_PUBLISH);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessage(commandForwarderProbe, "key", "test");
            expectMergeThingMessage(commandForwarderProbe, "key2", "test2");
            expectNoMessage(NO_MESSAGE_TIMEOUT);

            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testMultipleSources() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }, new String[] { ANOTHER_TOPIC_NAME }));
            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));

            sleep(TIMEOUT_BEFORE_PUBLISH);

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            publishMergeThingMessage(mqttClient, ANOTHER_TOPIC_NAME, "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessages(commandForwarderProbe,
                    Map.of("key", "test",
                    "key2", "test2"));
            commandForwarderProbe.expectNoMessage(NO_MESSAGE_TIMEOUT);

            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));

            // https://github.com/eclipse-ditto/ditto/issues/1768
//            ensureAllMessagesAcknowledged();
        }};}

    @Test
    @Ignore("https://github.com/eclipse-ditto/ditto/issues/1767")
    public void testPersistentSession() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));

            // create session
            System.out.println("Connecting to create session...");
            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));
            System.out.println("Connected");
            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));
            System.out.println("Disconnected");

            final var mqttClient = getMqttClient(name.getMethodName());
            mqttClient.connect();
            publishMergeThingMessage(mqttClient, TOPIC_NAME, "key", "test");
            mqttClient.disconnect();

            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));

            expectMergeThingMessageIfNotCleanSession(commandForwarderProbe, "key", "test");

            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testPersistentSessionMessageFromTopicWhichIsNoLongerSubscribed() {
        new TestKit(actorSystem) {{
            final var underTest = getMqttClientActor(getConnection(new String[] { TOPIC_NAME }));
            // create session for ditto client ID with subscription to 2 topics
            final var dittoMqttClient = getMqttClient(CLIENT_ID_DITTO);
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

            underTest.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.CONNECTED));

            expectMergeThingMessageIfNotCleanSession(commandForwarderProbe, "key", "test");

            underTest.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTION_TIMEOUT, new Status.Success(BaseClientState.DISCONNECTED));

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

    private static Connection getConnection(final String[]... sourcesTopics) {
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

    private static void sleep(int seconds) {
        if (seconds == 0) {
            return;
        }

        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
        }
    }

    private static GenericBlockingMqttClient getMqttClient(final String clientId) {
        return GenericBlockingMqttClientBuilder.newInstance(mqttVersion, MOSQUITTO_RESOURCE.getBindIp(), MOSQUITTO_RESOURCE.getPort())
                .clientIdentifier(clientId)
                .build();
    }

    private void expectMergeThingMessageIfNotCleanSession(final TestProbe testProbe, final String key, final String value) {
        if (!cleanSession) {
            expectMergeThingMessage(testProbe, key, value);
        }

        testProbe.expectNoMsg(NO_MESSAGE_TIMEOUT);
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
        final var messages = CollectionConverters.SeqHasAsJava(testProbe.receiveN(2, COMMAND_TIMEOUT)).asJava();
        assertThat(messages).hasSize(2);
        final var actualUpdates = messages.stream()
                .filter(MergeThing.class::isInstance)
                .map(MergeThing.class::cast)
                .map(m -> Map.entry(
                        String.valueOf(m.getPath()).replace("/attributes/", ""),
                        m.getValue().asString()))
                .toList();
        assertThat(actualUpdates).containsExactlyInAnyOrderElementsOf(updates.entrySet());
    }

    private static void ensureAllMessagesAcknowledged() {
        final var mqttClient = getMqttClient(CLIENT_ID_DITTO);

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

            private Mqtt3BlockingMqttClient(GenericBlockingMqttClientBuilder builder) {
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

            private Mqtt5BlockingMqttClient(GenericBlockingMqttClientBuilder builder) {
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

    /*@Test
    public void testStream() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(MqttClientActor.props(queryParser, readPersistence,
                    actorSystem.deadLetters()));

            insertTestThings();

            underTest.tell(queryThings(null, null), getRef());

            final SourceRef<?> response = expectMsgClass(SourceRef.class);
            final JsonArray searchResult = response.getSource()
                    .runWith(Sink.seq(), actorSystem)
                    .toCompletableFuture()
                    .join()
                    .stream()
                    .map(thingId -> wrapAsSearchResult((String) thingId))
                    .collect(JsonCollectors.valuesToArray());

            assertThat(searchResult).isEqualTo(expectedIds(4, 2, 0, 1, 3));
        }};
    }

    @Test
    public void testCursorSearch() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(MqttClientActor.props(queryParser, readPersistence,
                    actorSystem.deadLetters()));
            final Supplier<AssertionError> noCursor =
                    () -> new AssertionError("No cursor where a cursor is expected");

            insertTestThings();

            underTest.tell(queryThings(1, null), getRef());
            final QueryThingsResponse response0 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response0.getSearchResult().getItems()).isEqualTo(expectedIds(4));

            underTest.tell(queryThings(1, response0.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response1 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response1.getSearchResult().getItems()).isEqualTo(expectedIds(2));

            underTest.tell(queryThings(1, response1.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response2 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response2.getSearchResult().getItems()).isEqualTo(expectedIds(0));

            underTest.tell(queryThings(1, response2.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response3 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response3.getSearchResult().getItems()).isEqualTo(expectedIds(1));

            underTest.tell(queryThings(1, response3.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response4 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response4.getSearchResult().getItems()).isEqualTo(expectedIds(3));

            assertThat(response4.getSearchResult().getCursor()).isEmpty();
        }};
    }

    private static Command<?> queryThings(@Nullable final Integer size, final @Nullable String cursor) {
        final List<String> options = new ArrayList<>();
        final String sort = "sort(-attributes/c,+attributes/b,-attributes/a,+attributes/null/1,-attributes/null/2)";
        if (cursor == null) {
            options.add(sort);
        }
        if (size != null) {
            options.add("size(" + size + ")");
        }
        if (cursor != null) {
            options.add("cursor(" + cursor + ")");
        }
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AUTH_CONTEXT)
                .build();
        final String filter = "and(eq(attributes/x,5),eq(_metadata/attributes/x/type,\"x attribute\"))";
        if (size != null) {
            return QueryThings.of(filter, options, null, null, dittoHeaders);
        } else {
            return StreamThings.of(filter, null, sort, null, dittoHeaders);
        }
    }

    private void insertTestThings() {
        final Thing baseThing = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing", "00"))
                .setRevision(1234L)
                .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                .setMetadata(MetadataModelFactory.newMetadataBuilder()
                        .set("attributes", JsonObject.newBuilder()
                                .set("x", JsonObject.newBuilder()
                                        .set("type", "x attribute")
                                        .build())
                                .build())
                        .build())
                .build();

        final Thing irrelevantThing = baseThing.toBuilder().removeAllAttributes().build();

        writePersistence.write(template(baseThing, 0, "a"), policy, 0L)
                .concat(writePersistence.write(template(baseThing, 1, "b"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 2, "a"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 3, "b"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 4, "c"), policy, 0L))
                .concat(writePersistence.write(template(irrelevantThing, 5, "c"), policy, 0L))
                .runWith(Sink.ignore(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private static Policy createPolicy() {
        final Collection<Subject> subjects =
                AUTH_CONTEXT.getAuthorizationSubjectIds().stream()
                        .map(subjectId -> Subject.newInstance(subjectId, SubjectType.GENERATED))
                        .toList();
        final Collection<Resource> resources = Collections.singletonList(Resource.newInstance(
                ResourceKey.newInstance("thing:/"),
                EffectedPermissions.newInstance(Collections.singletonList("READ"), Collections.emptyList())
        ));
        final PolicyEntry policyEntry = PolicyEntry.newInstance("viewer", subjects, resources);
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(policyEntry)
                .setRevision(1L)
                .build();
    }

    private static JsonArray expectedIds(final int... thingOrdinals) {
        return Arrays.stream(thingOrdinals)
                .mapToObj(i -> "thing:" + i)
                .map(MqttClientActorIT::wrapAsSearchResult)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonValue wrapAsSearchResult(final CharSequence thingId) {
        return JsonFactory.readFrom(String.format("{\"thingId\":\"%s\"}", thingId));
    }

    private static Thing template(final Thing thing, final int i, final CharSequence attribute) {
        return thing.toBuilder()
                .setId(ThingId.of("thing", String.valueOf(i)))
                .setAttribute(JsonPointer.of(attribute), JsonValue.of(i))
                .build();
    }*/

}
