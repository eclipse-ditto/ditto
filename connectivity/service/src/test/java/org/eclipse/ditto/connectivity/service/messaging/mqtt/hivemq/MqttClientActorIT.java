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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Sets;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.*;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.config.DefaultTracingConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.junit.*;
import org.junit.rules.TestName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Tests {@link MqttClientActor}.
 */
public final class MqttClientActorIT {

//    @ClassRule
//    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
//            DittoTracingInitResource.disableDittoTracing();

//    @ClassRule
//    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
//    private static DittoMongoClient mongoClient;
    private static final String CLIENT_ID_DITTO = "ditto";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationModelFactory.newAuthSubject("nginx:ditto"));

    private static Config actorsTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Rule public TestName name = new TestName();

    private ActorSystem actorSystem;
//    private MongoCollection<Document> thingsCollection;

    @BeforeClass
    public static void startMongoResource() {
        actorsTestConfig = ConfigFactory.load("test.conf");
        DittoTracing.init(DefaultTracingConfig.of(actorsTestConfig));
//        mongoClient = provideClientWrapper();
    }

    @Before
    public void before() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), actorsTestConfig);
        cleanPreviousSession();
//        thingsCollection = mongoClient.getDefaultDatabase().getCollection("PersistenceConstants.THINGS_COLLECTION_NAME");
    }

    private void cleanPreviousSession() {
        final var mqttClient = getMqttClient(CLIENT_ID_DITTO);
        mqttClient.connectWith().cleanStart(true).send();
        mqttClient.disconnect();
    }

    /*private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + MONGO_RESOURCE.getBindIp() + ":" + MONGO_RESOURCE.getPort() + "/testSearchDB")
                .build();
    }*/

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

    /*@AfterClass
    public static void stopMongoResource() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }*/

    @Test
    public void testSingleTopic() {
        new TestKit(actorSystem) {{
            ConnectionId connectionId = ConnectionId.of("test");
            final ActorRef underTest = actorSystem.actorOf(MqttClientActor.props(
                    getConnection(connectionId, new String[] { "data" }),
                    getRef(),
                    getRef(),
                    DittoHeaders.empty(),
                    ConfigFactory.empty()));

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.CONNECTED));

            sleep(3);

            final var mqttClient = getMqttClient(name.getMethodName());

            mqttClient.connect();
            publishMergeThingMessage(mqttClient, "data", "key", "test");
            mqttClient.disconnect();

            expectMergeThingMessage(this, "key", "test");
            expectNoMessage(Duration.ofSeconds(3));

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.DISCONNECTED));

            ensureAllMessagesAcknowledged();

//            insertTestThings();

//            underTest.tell(queryThings(200, null), getRef());

//            final QueryThingsResponse response = expectMsgClass(QueryThingsResponse.class);

//            assertThat(response.getSearchResult().getItems()).isEqualTo(expectedIds(4, 2, 0, 1, 3));
        }};
    }

    @Test
    public void testMultipleTopics() {
        new TestKit(actorSystem) {{
            ConnectionId connectionId = ConnectionId.of("test");
            final ActorRef underTest = actorSystem.actorOf(MqttClientActor.props(
                    getConnection(connectionId, new String[] { "data", "data2" }),
                    getRef(),
                    getRef(),
                    DittoHeaders.empty(),
                    ConfigFactory.empty()));

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.CONNECTED));

            sleep(3);

            final var mqttClient = getMqttClient(name.getMethodName());

            mqttClient.connect();
            publishMergeThingMessage(mqttClient, "data", "key", "test");
            publishMergeThingMessage(mqttClient, "data2", "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessage(this, "key", "test");
            expectMergeThingMessage(this, "key2", "test2");
            expectNoMessage(Duration.ofSeconds(3));

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.DISCONNECTED));

            ensureAllMessagesAcknowledged();
        }};
    }

    @Test
    public void testMultipleSources() {
        new TestKit(actorSystem) {{
            ConnectionId connectionId = ConnectionId.of("test");
            final ActorRef underTest = actorSystem.actorOf(MqttClientActor.props(
                    getConnection(connectionId, new String[] { "data" }, new String[] { "data2" }),
                    getRef(),
                    getRef(),
                    DittoHeaders.empty(),
                    ConfigFactory.empty()));

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.CONNECTED));

            sleep(3);

            final var mqttClient = getMqttClient(name.getMethodName());

            mqttClient.connect();
            publishMergeThingMessage(mqttClient, "data", "key", "test");
            publishMergeThingMessage(mqttClient, "data2", "key2", "test2");
            mqttClient.disconnect();

            expectMergeThingMessages(this,
                    Map.of("key", "test",
                    "key2", "test2"));
            expectNoMessage(Duration.ofSeconds(3));

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(Duration.ofSeconds(30), new Status.Success(BaseClientState.DISCONNECTED));

            // https://github.com/eclipse-ditto/ditto/issues/1768
//            ensureAllMessagesAcknowledged();
        }};}

    @Test
    @Ignore("https://github.com/eclipse-ditto/ditto/issues/1767")
    public void testFromPersistentSession() {}

    private static Connection getConnection(ConnectionId connectionId, String[]... sourcesTopics) {
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT_5, ConnectivityStatus.CLOSED, "tcp://localhost:1883")
                .specificConfig(Map.of(
                        "clientId", CLIENT_ID_DITTO,
                        "cleanSession", "false",
                        "separatePublisherClient", "false"))
                .setSources(Arrays.stream(sourcesTopics)
                        .map(topics -> ConnectivityModelFactory.newSourceBuilder()
                                .authorizationContext(AUTHORIZATION_CONTEXT)
                                .qos(MqttQos.EXACTLY_ONCE.getCode())
                                .addresses(Sets.newHashSet(topics))
                                .build())
                        .toList())
                .build();
    }

    private static void publishMergeThingMessage(Mqtt5BlockingClient mqttClient, String topic, String key, String value) {
        mqttClient.publish(
                Mqtt5Publish.builder()
                        .topic(topic)
                        .payload(String.format("""
                                {
                                    "topic": "test/thing-01/things/twin/commands/merge",
                                    "path": "/attributes/%s",
                                    "headers": {
                                        "content-type": "application/merge-patch+json",
                                        "requested-acks": []
                                    },
                                    "value": "%s"
                                }
                                """, key, value).getBytes(StandardCharsets.UTF_8))
                        .qos(MqttQos.EXACTLY_ONCE)
                        .contentType("application/vnd.eclipse.ditto+json")
                        .build()
        );
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
        }
    }

    private static Mqtt5BlockingClient getMqttClient(String clientId) {
        return MqttClient.builder()
                .useMqttVersion5()
                .serverHost("localhost")
                .serverPort(1883)
                .identifier(clientId)
                .buildBlocking();
    }

    private void expectMergeThingMessage(TestKit testKit, String key, String value) {
        final var command = testKit.expectMsgClass(Duration.ofSeconds(30), MergeThing.class);
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

    private void expectMergeThingMessages(TestKit testKit, Map<String, String> updates) {
        List<Object> messages = testKit.receiveN(2, Duration.ofSeconds(30));
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
        final var mqttClient = getMqttClient(CLIENT_ID_DITTO).toAsync();

        final var unacknowledgedPublishes = new ArrayList<Mqtt5Publish>();
        mqttClient.publishes(MqttGlobalPublishFilter.ALL, unacknowledgedPublishes::add);

        mqttClient.connectWith()
                .cleanStart(false)
                // expiry immediately after disconnect to not spoil next tests
                .sessionExpiryInterval(0)
                .send()
                .thenApply(ignored -> {
                    sleep(3);
                    return new CompletableFuture<Void>();
                })
                .thenApply(ignored -> mqttClient.disconnect())
                .join();

        assertThat(unacknowledgedPublishes).isEmpty();
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
