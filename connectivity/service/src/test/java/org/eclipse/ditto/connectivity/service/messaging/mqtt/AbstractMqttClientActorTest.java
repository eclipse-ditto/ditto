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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.disableLogging;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public abstract class AbstractMqttClientActorTest<M> extends AbstractBaseClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMqttClientActorTest.class);
    protected static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    protected static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final Target TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address("target")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .qos(1)
            .topics(Topic.TWIN_EVENTS)
            .build();
    private static final String SOURCE_ADDRESS = "source";
    private static final Source MQTT_SOURCE = ConnectivityModelFactory.newSourceBuilder()
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .index(1)
            .consumerCount(1)
            .address(SOURCE_ADDRESS)
            .qos(1)
            .build();

    private static final HeaderMapping MQTT_HEADER_MAPPING = ConnectivityModelFactory.newHeaderMapping(Map.of(
            MqttHeader.MQTT_TOPIC.getName(), getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName()),
            MqttHeader.MQTT_QOS.getName(), getHeaderPlaceholder(MqttHeader.MQTT_QOS.getName()),
            MqttHeader.MQTT_RETAIN.getName(), getHeaderPlaceholder(MqttHeader.MQTT_RETAIN.getName()),
            "custom.topic", getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName()),
            "custom.qos", getHeaderPlaceholder(MqttHeader.MQTT_QOS.getName()),
            "custom.retain", getHeaderPlaceholder(MqttHeader.MQTT_RETAIN.getName())
    ));

    private static String getHeaderPlaceholder(final String headerName) {
        return "{{ header:" + headerName + "}}";
    }


    protected static final ConnectionType connectionType = ConnectionType.MQTT;

    protected ActorSystem actorSystem;
    protected TestProbe mockConnectionActor;

    protected ConnectionId connectionId;
    private String serverHost;
    protected Connection connection;

    protected abstract TestConstants.FreePort getFreePort();

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Before
    public void initializeConnection() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        mockConnectionActor = TestProbe.apply("connectionActor", actorSystem);
        connectionId = TestConstants.createRandomConnectionId();
        serverHost = "tcp://localhost:" + getFreePort().getPort();
        connection = ConnectivityModelFactory.newConnectionBuilder(connectionId, connectionType,
                ConnectivityStatus.CLOSED, serverHost)
                .sources(singletonList(MQTT_SOURCE))
                .targets(singletonList(TARGET))
                .failoverEnabled(true)
                .build();
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        return isSecure ? setScheme(connection, "ssl") : connection;
    }

    private Connection getConnectionWithoutSeparatePublisherClient(final boolean isSecure) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("separatePublisherClient", "false");
        return getConnection(isSecure).toBuilder()
                .specificConfig(specificConfig)
                .build();
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    @Test
    public void testConnect() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnectionWithoutSeparatePublisherClient(false));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            expectDisconnectCalled();
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef mqttClientActor = watch(actorSystem.actorOf(props));

            mqttClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
            expectDisconnectCalled();

            // client actor should be stopped after testing
            fishForMessage(FiniteDuration.apply(5L, TimeUnit.SECONDS), "client actor should stop after test",
                    msg -> msg instanceof Terminated && ((Terminated) msg).getActor().equals(mqttClientActor));

            expectDisconnectCalled();
        }};
    }

    @Test
    public void testTestConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props = createFailingClientActor(getRef());
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());

            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);

            expectDisconnectCalled();
        }};
    }

    @Test
    public void testRetrieveConnectionMetrics() {
        new TestKit(actorSystem) {{
            final Source mqttSource = ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .index(2)
                    .consumerCount(1)
                    .address("topic1")
                    .address("topic2")
                    .qos(1)
                    .build();

            final Connection connectionWithAdditionalSources = connection.toBuilder()
                    .sources(singletonList(mqttSource)).build();
            final String modifyThing = TestConstants.modifyThing();

            final Props props = createClientActorWithMessages(connectionWithAdditionalSources, getRef(),
                    singletonList(mqttMessage(SOURCE_ADDRESS, modifyThing)));
            final ActorRef underTest = actorSystem.actorOf(props);

            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), controlProbe.ref());
            LOGGER.info("Waiting for connected...");
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            expectMsgClass(ModifyThing.class);

            underTest.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

            final RetrieveConnectionMetricsResponse metricsResponse =
                    expectMsgClass(RetrieveConnectionMetricsResponse.class);

            LOGGER.info("metrics: {}", metricsResponse);
        }};
    }

    @Test
    public void testConsumeFromTopic() {
        final ModifyThing modifyThing = testConsumeModifyThing(connection, SOURCE_ADDRESS)
                .expectMsgClass(ModifyThing.class);

        // verify that mqtt properties are not mapped by default
        assertThat(modifyThing.getDittoHeaders()).doesNotContainKeys(
                MqttHeader.getHeaderNames().toArray(String[]::new));
    }

    @Test
    public void testConsumeFromTopicWithSourceHeaderMapping() {
        final Connection connectionWithHeaderMapping = connection.toBuilder()
                .setSources(connection.getSources().stream()
                        .map(ConnectivityModelFactory::newSourceBuilder)
                        .map(sb -> sb.headerMapping(MQTT_HEADER_MAPPING))
                        .map(SourceBuilder::build)
                        .collect(Collectors.toList())).build();

        final ModifyThing modifyThing = testConsumeModifyThing(connectionWithHeaderMapping, SOURCE_ADDRESS)
                .expectMsgClass(ModifyThing.class);
        assertThat(modifyThing.getDittoHeaders()).containsKeys(MqttHeader.getHeaderNames().toArray(String[]::new));
        assertThat(modifyThing.getDittoHeaders()).containsEntry(MqttHeader.MQTT_TOPIC.getName(), SOURCE_ADDRESS);
        assertThat(modifyThing.getDittoHeaders()).containsEntry(MqttHeader.MQTT_QOS.getName(), "0");
        assertThat(modifyThing.getDittoHeaders()).containsEntry(MqttHeader.MQTT_RETAIN.getName(), "false");
        assertThat(modifyThing.getDittoHeaders()).containsEntry("custom.topic", SOURCE_ADDRESS);
        assertThat(modifyThing.getDittoHeaders()).containsEntry("custom.qos", "0");
        assertThat(modifyThing.getDittoHeaders()).containsEntry("custom.retain", "false");
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcement() {
        final Source mqttSource = newFilteredMqttSource(
                "eclipse/{{ thing:namespace }}/{{ thing:name }}",
                "eclipse/+/+");
        final Connection connectionWithEnforcement =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, connectionType,
                        ConnectivityStatus.OPEN,
                        serverHost)
                        .sources(singletonList(mqttSource))
                        .build();
        testConsumeModifyThing(connectionWithEnforcement, "eclipse/ditto/thing")
                .expectMsgClass(ModifyThing.class);
    }

    @Test
    public void testConsumeFromTopicWithIdEnforcementExpectErrorResponse() {
        disableLogging(actorSystem);

        final Source mqttSource =
                newFilteredMqttSource("eclipse/{{ thing:namespace }}/{{ thing:name }}", "eclipse/+/+");

        final Connection connectionWithEnforcement =
                ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.MQTT,
                        ConnectivityStatus.OPEN, serverHost)
                        .sources(singletonList(mqttSource))
                        .build();

        final M message = testConsumeModifyThing(connectionWithEnforcement, "eclipse/invalid/address")
                .expectMsgClass(getMessageClass());

        assertThat(extractPayload(message)).contains(ConnectionSignalIdEnforcementFailedException.ERROR_CODE);
    }

    private TestKit testConsumeModifyThing(final Connection connection, final String publishTopic) {
        return new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = createClientActorWithMessages(connection, getRef(),
                    singletonList(mqttMessage(publishTopic, TestConstants.modifyThing())));
            final ActorRef mqttClientActor = actorSystem.actorOf(props);

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);
        }};
    }

    private static Source newFilteredMqttSource(final String filter, final String... sources) {
        return ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .consumerCount(1)
                .addresses(TestConstants.asSet(sources))
                .enforcement(ConnectivityModelFactory.newSourceAddressEnforcement(filter))
                .replyTarget(ReplyTarget.newBuilder()
                        .address("{{ header:reply-to }}")
                        .build())
                .qos(1)
                .build();
    }

    @Test
    public void testConsumeMultipleSources() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);

            final List<String> irrelevantTopics = Arrays.asList("irrelevant", "topics");
            final String[] subscriptions = {"A1", "A1", "A1", "B1", "B1", "B2", "B2", "C1", "C2", "C3"};
            final List<M> mockMessages =
                    Stream.concat(irrelevantTopics.stream(), Arrays.stream(subscriptions))
                            .map(topic -> mqttMessage(topic, TestConstants.modifyThing(UUID.randomUUID().toString())))
                            .collect(Collectors.toList());

            final Connection multipleSources =
                    ConnectivityModelFactory.newConnectionBuilder(connectionId, connectionType,
                            ConnectivityStatus.OPEN, serverHost)
                            .sources(Arrays.asList(
                                    newMqttSource(3, 1, "A1"),
                                    newMqttSource(2, 2, "B1", "B2"),
                                    newMqttSource(1, 3, "C1", "C2", "C3"))
                            )
                            .build();

            final ConnectionId connectionId = TestConstants.createRandomConnectionId();
            final Props props = createClientActorWithMessages(multipleSources, getRef(), mockMessages);
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            final List<String> receivedTopics = new LinkedList<>();
            IntStream.range(0, subscriptions.length).forEach(i -> {
                LOGGER.info("Consuming message {}", i);
                final ModifyThing modifyThing = expectMsgClass(ModifyThing.class);
                final String topic = modifyThing.getDittoHeaders().get("mqtt.topic");
                LOGGER.info("Got message with topic {}", topic);
                receivedTopics.add(topic);
            });

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);

            assertThat(receivedTopics).containsExactlyInAnyOrder(subscriptions);
        }};
    }

    private static Source newMqttSource(final int consumerCount, final int index, final String... sources) {
        return ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .index(index)
                .consumerCount(consumerCount)
                .addresses(TestConstants.asSet(sources))
                .headerMapping(ConnectivityModelFactory.newHeaderMapping(Map.of(MqttHeader.MQTT_TOPIC.getName(),
                        "{{ header:" + MqttHeader.MQTT_TOPIC.getName() + " }}")))
                .qos(1)
                .build();
    }

    @Test
    public void testReconnectAndConsumeFromTopic() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props =
                    createClientActorWithMessages(connection, getRef(), singletonList(mqttMessage(SOURCE_ADDRESS,
                            TestConstants.modifyThing())));
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
    public void testPublishToTopic() {
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent<?> thingModifiedEvent =
                    TestConstants.thingModified(TARGET.getAuthorizationContext().getAuthorizationSubjects());
            final String expectedJson = TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent);

            LOGGER.info("Sending thing modified message: {}", thingModifiedEvent);
            underTest.tell(thingModifiedEvent, getRef());

            final M receivedMessage = expectMsgClass(getMessageClass());
            assertThat(extractTopic(receivedMessage)).isEqualTo(TARGET.getAddress());
            assertThat(extractPayload(receivedMessage)).isEqualTo(expectedJson);

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testPublishToReplyTarget() {
        connection = connection.toBuilder()
                .setSources(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT)
                .build();
        new TestKit(actorSystem) {{
            final TestProbe controlProbe = TestProbe.apply(actorSystem);
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(CONNECTED_SUCCESS);

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .replyTarget(0)
                    .expectedResponseTypes(ResponseType.values())
                    .build();
            final DeleteThingResponse deleteThingResponse =
                    DeleteThingResponse.of(ThingId.of("thing", "id"), dittoHeaders);

            LOGGER.info("Sending DeleteThingResponse: {}", deleteThingResponse);

            underTest.tell(deleteThingResponse, getRef());

            final M receivedMessage = expectMsgClass(getMessageClass());
            assertThat(extractTopic(receivedMessage)).isEqualTo("replyTarget/thing:id");

            underTest.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), controlProbe.ref());
            controlProbe.expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    protected abstract Props createFailingClientActor(final ActorRef testProbe);

    protected abstract Props createClientActorWithMessages(
            final Connection connection, final ActorRef testProbe,
            final List<M> messages);

    protected abstract M mqttMessage(final String topic, final String payload);

    @Nullable
    protected abstract String extractPayload(M message);

    @Nullable
    protected abstract String extractTopic(M message);

    protected abstract Class<M> getMessageClass();

    protected abstract void expectDisconnectCalled();
}
