/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.assertj.core.data.Offset;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.ExternalMessageWithSender;
import org.eclipse.ditto.connectivity.service.messaging.ResponseCollectorActor;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Unit test for {@link MqttConsumerActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttConsumerActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    private static final Set<String> SOURCE_ADDRESSES = Set.of("source/telemetry", "source/status");
    private static final String SOURCE_ADDRESSES_JOINED = String.join(";", SOURCE_ADDRESSES);
    private static final Mqtt5Publish MQTT_5_PUBLISH = Mqtt5Publish.builder()
            .topic(MqttTopic.of("source/status"))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .payload(ByteBufferUtils.fromUtf8String("""
                    {
                      "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
                      "headers": {
                        "correlation-id": "<command-correlation-id>"
                      },
                      "path": "/attributes",
                      "value": {
                        "location": {
                          "latitude": 44.673856,
                          "longitude": 8.261719
                        }
                      }
                    }\
                    """))
            .build();

    @Rule
    public final TestName testName = new TestName();

    @Mock private Connection connection;
    @Mock private Sink<Object, NotUsed> inboundMappingSink;
    @Mock private org.eclipse.ditto.connectivity.model.Source connectionSource;
    @Mock private ConnectivityStatusResolver connectivityStatusResolver;

    @Before
    public void before() {
        Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);
        Mockito.when(connection.getConnectionType()).thenReturn(ConnectionType.MQTT_5);

        Mockito.when(connectionSource.getAddresses()).thenReturn(SOURCE_ADDRESSES);
    }

    @Test
    public void propsProcessingWithNullConnectionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(null,
                        inboundMappingSink,
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Mockito.mock(akka.stream.javadsl.Source.class)))
                .withMessage("The connection must not be null!")
                .withNoCause();
    }

    @Test
    public void propsProcessingWithNullInboundMappingSinkThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(connection,
                        null,
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Mockito.mock(akka.stream.javadsl.Source.class)))
                .withMessage("The inboundMappingSink must not be null!")
                .withNoCause();
    }

    @Test
    public void propsProcessingWithNullConnectionSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        null,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Mockito.mock(akka.stream.javadsl.Source.class)))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void propsProcessingWithNullConnectivityStatusResolverThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        connectionSource,
                        null,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Mockito.mock(akka.stream.javadsl.Source.class)))
                .withMessage("The connectivityStatusResolver must not be null!")
                .withNoCause();
    }

    @Test
    public void propsProcessingWithNullConnectivityConfigThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        connectionSource,
                        connectivityStatusResolver,
                        null,
                        Mockito.mock(akka.stream.javadsl.Source.class)))
                .withMessage("The connectivityConfig must not be null!")
                .withNoCause();
    }

    @Test
    public void propsProcessingWithNullMqttPublishSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        null))
                .withMessage("The mqttPublishSource must not be null!")
                .withNoCause();
    }

    @Test
    public void sendRetrieveAddressStatusToMqttConsumerActorReturnsCurrentSourceStatus() {
        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.onComplete(doneTry -> {}),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.repeat(GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH))),
                testName.getMethodName()
        );

        final var testKit = ACTOR_SYSTEM_RESOURCE.newTestKit();

        underTest.tell(RetrieveAddressStatus.getInstance(), testKit.getRef());

        final var resourceStatus = testKit.expectMsgClass(ResourceStatus.class);
        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat((CharSequence) resourceStatus.getResourceType())
                    .as("resource type")
                    .isEqualTo(ResourceStatus.ResourceType.SOURCE);
            softly.assertThat((CharSequence) resourceStatus.getStatus())
                    .as("connectivity status")
                    .isEqualTo(ConnectivityStatus.OPEN);
            softly.assertThat(resourceStatus.getAddress()).as("address").hasValue(SOURCE_ADDRESSES_JOINED);
            softly.assertThat(resourceStatus.getRecoveryStatus()).as("recovery status").isEmpty();
            softly.assertThat(resourceStatus.getStatusDetails()).as("status details").hasValue("Consumer started.");
        }

        ACTOR_SYSTEM_RESOURCE.stopActor(underTest);
    }

    @Test
    public void sendGracefulStopShutsDownProcessingMqttConsumerActor() {
        final var underTestWatcher = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var underTest = underTestWatcher.watch(ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.onComplete(doneTry -> {}),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.repeat(GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH))),
                testName.getMethodName()
        ));

        underTest.tell(GracefulStop.INSTANCE, ActorRef.noSender());

        underTestWatcher.expectTerminated(underTest);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void mqttPublishSourceGetsThrottledIfThrottlingIsEnabled() {
        final var throttlingConfig = Mockito.mock(ThrottlingConfig.class);
        Mockito.when(throttlingConfig.isEnabled()).thenReturn(true);
        Mockito.when(throttlingConfig.getLimit()).thenReturn(5);
        Mockito.when(throttlingConfig.getInterval()).thenReturn(Duration.ofMillis(500));
        final var connectivityConfig = getConnectivityConfigWithCustomThrottlingConfig(throttlingConfig);

        final var mqttPublishSource = Mockito.mock(Source.class);
        Mockito.when(mqttPublishSource.throttle(Mockito.anyInt(), Mockito.any(Duration.class)))
                .thenReturn(Source.empty());

        ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.onComplete(doneTry -> {}),
                        connectionSource,
                        connectivityStatusResolver,
                        connectivityConfig,
                        mqttPublishSource),
                testName.getMethodName()
        );

        Mockito.verify(mqttPublishSource, Mockito.after(500L))
                .throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval());
    }

    private static ConnectivityConfig getConnectivityConfigWithCustomThrottlingConfig(
            final ThrottlingConfig throttlingConfig
    ) {
        final var mqttConfig = Mockito.mock(MqttConfig.class);
        Mockito.when(mqttConfig.getConsumerThrottlingConfig()).thenReturn(throttlingConfig);
        return getConnectivityConfigWithCustomMqttConfig(mqttConfig);
    }

    private static ConnectivityConfig getConnectivityConfigWithCustomMqttConfig(final MqttConfig mqttConfig) {
        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        final var connectivityConfig = Mockito.mock(ConnectivityConfig.class);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);
        Mockito.when(connectivityConfig.getMonitoringConfig()).thenReturn(TestConstants.MONITORING_CONFIG);
        Mockito.when(connectivityConfig.getAcknowledgementConfig())
                .thenReturn(TestConstants.CONNECTIVITY_CONFIG.getAcknowledgementConfig());
        return connectivityConfig;
    }

    @Test
    public void mqttPublishSourceThrottlingWorksAsExpected() throws InterruptedException {
        final var processedMessagesCount = new LongAdder();
        final var inboundMappingSink = Sink.foreach(msg -> processedMessagesCount.increment());

        final var throttlingLimitPerInterval = 5;
        final var throttlingInterval = Duration.ofSeconds(1L);
        final var throttlingConfig = Mockito.mock(ThrottlingConfig.class);
        Mockito.when(throttlingConfig.isEnabled()).thenReturn(true);
        Mockito.when(throttlingConfig.getLimit()).thenReturn(throttlingLimitPerInterval);
        Mockito.when(throttlingConfig.getInterval()).thenReturn(throttlingInterval);
        final var connectivityConfig = getConnectivityConfigWithCustomThrottlingConfig(throttlingConfig);

        final var runtimeIntervalAmount = 2;

        ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        connectionSource,
                        connectivityStatusResolver,
                        connectivityConfig,
                        Source.repeat(GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH))),
                testName.getMethodName()
        );

        // Wait some time
        Thread.sleep(throttlingInterval.toMillis() * runtimeIntervalAmount);

        // Assert that the expected amount of messages was processed during the waited time
        assertThat(processedMessagesCount.sum())
                .isCloseTo(throttlingLimitPerInterval * runtimeIntervalAmount, Offset.offset(1L));
    }

    @Test
    public void mqttPublishesAreDroppedIfConsumerActorOperatesInDryRunMode() throws InterruptedException {
        final var receivedMqttPublishMessages = new ArrayList<>();

        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsDryRun(connection,
                        Sink.foreach(receivedMqttPublishMessages::add),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.repeat(Mockito.mock(GenericMqttPublish.class)).take(100)),
                testName.getMethodName()
        );

        // Wait some tome to make sure there was enough time to potentially process messages (which should not happen!)
        Thread.sleep(1_000L);

        // 'inbound mapping sink' should receive nothing because should have been dropped.
        assertThat(receivedMqttPublishMessages).isEmpty();
    }

    @Test
    public void mqttPublishesAreMappedToExternalMessagesAndSentToInboundMappingSink() throws InterruptedException {
        final var genericMqttPublish = GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH);
        final var mqttPublishTransformer =
                MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESSES_JOINED, connectionSource);
        final var transformationResult = mqttPublishTransformer.transform(genericMqttPublish);
        final var amountMqttPublishes = 100;
        final var countDownLatch = new CountDownLatch(amountMqttPublishes);
        final var externalMessages = new HashSet<ExternalMessage>(amountMqttPublishes);
        final var inboundMappingSink = Sink.foreach(o -> {
            if (o instanceof ExternalMessageWithSender externalMessageWithSender) {
                externalMessages.add(externalMessageWithSender.externalMessage());
            }
            countDownLatch.countDown();
        });

        ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        inboundMappingSink,
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.repeat(genericMqttPublish).take(amountMqttPublishes)),
                testName.getMethodName()
        );

        assertThat(countDownLatch.await(3_000L, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(externalMessages).containsExactly(
                ExternalMessageFactory.newExternalMessageBuilder(transformationResult.getSuccessValueOrThrow())
                        .withSource(connectionSource) // Imitates BaseConsumerActor.addSourceAndReplyTarget().
                        .build()
        );
    }

    @Test
    public void successfullyAcknowledgeMqttPublish() {
        final var mqtt5Publish = Mockito.spy(MQTT_5_PUBLISH);
        Mockito.when(mqtt5Publish.isRetain()).thenReturn(false);

        final var inboundMappingSinkElementReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var onCompleteMessage = new Object();

        //When
        ACTOR_SYSTEM_RESOURCE.newActor(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.actorRef(inboundMappingSinkElementReceiver.getRef(), onCompleteMessage),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.single(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish))),
                testName.getMethodName()
        );

        //Then
        final var externalMessageWithSender =
                inboundMappingSinkElementReceiver.expectMsgClass(ExternalMessageWithSender.class);

        final var responseCollector = externalMessageWithSender.sender();
        responseCollector.tell(ResponseCollectorActor.setCount(1), ActorRef.noSender());
        responseCollector.tell(
                Acknowledgement.of(DittoAcknowledgementLabel.TWIN_PERSISTED,
                        ThingId.of("org.eclipse.ditto", "fancy-thing"),
                        HttpStatus.NO_CONTENT,
                        DittoHeaders.newBuilder().correlationId("<command-correlation-id>").build()),
                ActorRef.noSender()
        );

        inboundMappingSinkElementReceiver.expectMsg(onCompleteMessage);
        Mockito.verify(mqtt5Publish, Mockito.timeout(1_000L)).acknowledge();
    }

    @Test
    public void reconnectConsumerClientForRedeliveryIfInboundMessageIsRejected() {
        final var mqtt5Publish = Mockito.spy(MQTT_5_PUBLISH);
        Mockito.when(mqtt5Publish.isRetain()).thenReturn(false);

        final var inboundMappingSinkElementReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var onCompleteMessage = new Object();
        final var fakeMqttClientActor = ACTOR_SYSTEM_RESOURCE.newTestKit();

        fakeMqttClientActor.childActorOf(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.actorRef(inboundMappingSinkElementReceiver.getRef(), onCompleteMessage),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.single(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish))),
                testName.getMethodName()
        );

        final var externalMessageWithSender =
                inboundMappingSinkElementReceiver.expectMsgClass(ExternalMessageWithSender.class);

        final var responseCollector = externalMessageWithSender.sender();
        responseCollector.tell(ResponseCollectorActor.setCount(1), ActorRef.noSender());
        responseCollector.tell(
                Acknowledgement.of(DittoAcknowledgementLabel.TWIN_PERSISTED,
                        ThingId.of("org.eclipse.ditto", "fancy-thing"),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        DittoHeaders.newBuilder().correlationId("<command-correlation-id>").build()),
                ActorRef.noSender()
        );

        fakeMqttClientActor.expectMsgClass(ReconnectConsumerClient.class);
        inboundMappingSinkElementReceiver.expectMsg(onCompleteMessage);
        Mockito.verify(mqtt5Publish, Mockito.never()).acknowledge();
    }

    @Test
    public void doNotRedeliverRejectedMessageIfShouldDeliverButReconnectForRedeliveryIsDisabled() {

        final var mqttConfig = Mockito.mock(MqttConfig.class);
        Mockito.when(mqttConfig.shouldReconnectForRedelivery()).thenReturn(false);
        Mockito.when(mqttConfig.getConsumerThrottlingConfig()).thenReturn(Mockito.mock(ThrottlingConfig.class));
        final var connectivityConfig = getConnectivityConfigWithCustomMqttConfig(mqttConfig);

        final var mqtt5Publish = Mockito.spy(MQTT_5_PUBLISH);
        Mockito.when(mqtt5Publish.isRetain()).thenReturn(false);

        final var inboundMappingSinkElementReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var onCompleteMessage = new Object();
        final var fakeMqttClientActor = ACTOR_SYSTEM_RESOURCE.newTestKit();

        fakeMqttClientActor.childActorOf(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.actorRef(inboundMappingSinkElementReceiver.getRef(), onCompleteMessage),
                        connectionSource,
                        connectivityStatusResolver,
                        connectivityConfig,
                        Source.single(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish))),
                testName.getMethodName()
        );

        final var externalMessageWithSender =
                inboundMappingSinkElementReceiver.expectMsgClass(ExternalMessageWithSender.class);

        final var responseCollector = externalMessageWithSender.sender();
        responseCollector.tell(ResponseCollectorActor.setCount(1), ActorRef.noSender());
        responseCollector.tell(
                Acknowledgement.of(DittoAcknowledgementLabel.TWIN_PERSISTED,
                        ThingId.of("org.eclipse.ditto", "fancy-thing"),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        DittoHeaders.newBuilder().correlationId("<command-correlation-id>").build()),
                ActorRef.noSender()
        );

        fakeMqttClientActor.expectNoMessage();
        inboundMappingSinkElementReceiver.expectMsg(onCompleteMessage);
        Mockito.verify(mqtt5Publish).acknowledge();
    }

    @Test
    public void acknowledgeRejectedIncomingMessageIfShouldNotRedeliver() {
        final var mqtt5Publish = Mockito.spy(MQTT_5_PUBLISH);
        Mockito.when(mqtt5Publish.isRetain()).thenReturn(false);

        final var inboundMappingSinkElementReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var onCompleteMessage = new Object();
        final var fakeMqttClientActor = ACTOR_SYSTEM_RESOURCE.newTestKit();

        fakeMqttClientActor.childActorOf(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.actorRef(inboundMappingSinkElementReceiver.getRef(), onCompleteMessage),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.single(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish))),
                testName.getMethodName()
        );

        final var externalMessageWithSender =
                inboundMappingSinkElementReceiver.expectMsgClass(ExternalMessageWithSender.class);

        final var responseCollector = externalMessageWithSender.sender();
        responseCollector.tell(ResponseCollectorActor.setCount(1), ActorRef.noSender());
        responseCollector.tell(
                Acknowledgement.of(DittoAcknowledgementLabel.TWIN_PERSISTED,
                        ThingId.of("org.eclipse.ditto", "fancy-thing"),
                        HttpStatus.BAD_REQUEST,
                        DittoHeaders.newBuilder().correlationId("<command-correlation-id>").build()),
                ActorRef.noSender()
        );

        fakeMqttClientActor.expectNoMessage();
        inboundMappingSinkElementReceiver.expectMsg(onCompleteMessage);
        Mockito.verify(mqtt5Publish).acknowledge();
    }

    @Test
    public void rejectIncomingMessageDueToDittoRuntimeExceptionInsteadOfAcknowledgement() {
        final var mqtt5Publish = Mockito.spy(MQTT_5_PUBLISH);
        Mockito.when(mqtt5Publish.isRetain()).thenReturn(false);

        final var inboundMappingSinkElementReceiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var onCompleteMessage = new Object();
        final var fakeMqttClientActor = ACTOR_SYSTEM_RESOURCE.newTestKit();

        fakeMqttClientActor.childActorOf(
                MqttConsumerActor.propsProcessing(connection,
                        Sink.actorRef(inboundMappingSinkElementReceiver.getRef(), onCompleteMessage),
                        connectionSource,
                        connectivityStatusResolver,
                        TestConstants.CONNECTIVITY_CONFIG,
                        Source.single(GenericMqttPublish.ofMqtt5Publish(mqtt5Publish))),
                testName.getMethodName()
        );

        final var externalMessageWithSender =
                inboundMappingSinkElementReceiver.expectMsgClass(ExternalMessageWithSender.class);

        final var responseCollector = externalMessageWithSender.sender();
        responseCollector.tell(ResponseCollectorActor.setCount(1), ActorRef.noSender());
        responseCollector.tell(
                ThingNotAccessibleException.newBuilder(ThingId.of("org.eclipse.ditto", "fancy-thing"))
                        .dittoHeaders(DittoHeaders.newBuilder().correlationId("<command-correlation-id>").build())
                        .build(),
                ActorRef.noSender()
        );

        fakeMqttClientActor.expectNoMessage();
        inboundMappingSinkElementReceiver.expectMsg(onCompleteMessage);
        Mockito.verify(mqtt5Publish).acknowledge();
    }

}
