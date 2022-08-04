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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttPublishResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttPublishingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.UserProperty;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link MqttPublisherActor}.
 */
public final class MqttPublisherActorTest extends AbstractPublisherActorTest {

    private static final MqttQos DEFAULT_QOS = MqttQos.AT_MOST_ONCE;
    private static final boolean DEFAULT_RETAIN = false;
    private static final String CUSTOM_RETAIN_HEADER = "custom.retain";
    private static final String CUSTOM_TOPIC_HEADER = "custom.topic";
    private static final String CUSTOM_QOS_HEADER = "custom.qos";
    private static final String OUTBOUND_ADDRESS = "mqtt/eclipse/ditto";
    private static final AcknowledgementLabel AUTO_ACK_LABEL = AcknowledgementLabel.of("please-verify");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private final List<GenericMqttPublish> received = new ArrayList<>();

    private GenericMqttPublishingClient genericMqttPublishingClient;

    @Before
    public void before() {
        setupMocks(null);
    }

    @Override
    protected void setupMocks(final TestProbe _probe) {
        genericMqttPublishingClient = Mockito.mock(GenericMqttPublishingClient.class);
        Mockito.when(genericMqttPublishingClient.publish(Mockito.any(GenericMqttPublish.class)))
                .thenAnswer(invocationOnMock -> {
                    final GenericMqttPublish genericMqttPublish = invocationOnMock.getArgument(0);
                    received.add(genericMqttPublish);
                    return CompletableFuture.completedFuture(GenericMqttPublishResult.success(genericMqttPublish));
                });
    }

    @Override
    protected HeaderMapping getHeaderMapping() {
        final var headerMapping = super.getHeaderMapping();
        final Map<String, String> mappingMap = new HashMap<>(headerMapping.getMapping());
        mappingMap.put(MqttHeader.MQTT_RETAIN.getName(), "{{ header:" + CUSTOM_RETAIN_HEADER + " }}");
        mappingMap.put(MqttHeader.MQTT_TOPIC.getName(), "{{ header:" + CUSTOM_TOPIC_HEADER + " }}");
        mappingMap.put(MqttHeader.MQTT_QOS.getName(), "{{ header:" + CUSTOM_QOS_HEADER + " }}");

        return ConnectivityModelFactory.newHeaderMapping(mappingMap);
    }

    @Override
    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

    @Override
    protected Props getPublisherActorProps() {
        return MqttPublisherActor.propsProcessing(TestConstants.createConnection(),
                Mockito.mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()),
                genericMqttPublishingClient);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return ConnectivityModelFactory.newTarget(target,
                getOutboundAddress(),
                MqttQos.AT_MOST_ONCE.getCode(),
                AUTO_ACK_LABEL);
    }

    @Override
    protected void verifyPublishedMessage() {
        verifyPublishedMessage(getOutboundAddress(), DEFAULT_RETAIN, DEFAULT_QOS);
    }

    private void verifyPublishedMessage(final String topic,
            final boolean expectedRetain,
            final MqttQos expectedQos,
            final UserProperty... additionalExpectedUserProperties) {

        Awaitility.await().until(() -> !received.isEmpty());
        softly.assertThat(received)
                .hasSize(1)
                .first()
                .satisfies(receivedGenericMqttPublish -> {
                    softly.assertThat(receivedGenericMqttPublish.getTopic()).as("topic").isEqualTo(MqttTopic.of(topic));
                    softly.assertThat(receivedGenericMqttPublish.getQos()).as("QoS").isEqualTo(expectedQos);
                    softly.assertThat(receivedGenericMqttPublish.isRetain()).as("retain").isEqualTo(expectedRetain);
                    softly.assertThat(receivedGenericMqttPublish.getPayload())
                            .as("payload")
                            .hasValue(ByteBufferUtils.fromUtf8String("payload"));
                    softly.assertThat(receivedGenericMqttPublish.getContentType()).as("content-type").isEmpty();
                    softly.assertThat(receivedGenericMqttPublish.getCorrelationData())
                            .as("correlation data")
                            .hasValue(ByteBufferUtils.fromUtf8String(TestConstants.CORRELATION_ID));
                    softly.assertThat(receivedGenericMqttPublish.getResponseTopic()).as("response topic").isEmpty();
                    softly.assertThat(receivedGenericMqttPublish.userProperties())
                            .as("user properties")
                            .containsExactlyInAnyOrder(
                                    Stream.concat(
                                            Stream.of(new UserProperty("device_id", DEVICE_ID),
                                                    new UserProperty("thing_id", DEVICE_ID),
                                                    new UserProperty("suffixed_thing_id", DEVICE_ID + ".some.suffix"),
                                                    new UserProperty("prefixed_thing_id", "some.prefix." + DEVICE_ID),
                                                    new UserProperty("ditto-connection-id", "hallo"),
                                                    new UserProperty("eclipse", "ditto")),
                                            Stream.of(additionalExpectedUserProperties)
                                    ).toArray(UserProperty[]::new)
                            );
                });
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() {
        Awaitility.await().until(() -> !received.isEmpty());
        softly.assertThat(received)
                .hasSize(1)
                .first()
                .satisfies(receivedGenericMqttPublish -> softly.assertThat(receivedGenericMqttPublish.getTopic())
                        .as("topic")
                        .isEqualTo(MqttTopic.of("replyTarget/thing:id"))
                );
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) {
        final var acknowledgements = ackSupplier.get();

        softly.assertThat(acknowledgements).as("acknowledgements size").hasSize(1);

        for (final var acknowledgement : acknowledgements.getSuccessfulAcknowledgements()) {
            softly.assertThat((CharSequence) acknowledgement.getLabel()).as("label").isEqualTo(AUTO_ACK_LABEL);
            softly.assertThat(acknowledgement.getHttpStatus()).as("HTTP status").isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    public void publishMessageWithCustomRetainFlag() {
        final var customRetainFlat = true;
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, underTest);

        underTest.tell(
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(CUSTOM_RETAIN_HEADER, String.valueOf(customRetainFlat))),
                        testKit.getRef()
                ),
                testKit.getRef()
        );

        verifyPublishedMessage(getOutboundAddress(),
                customRetainFlat,
                DEFAULT_QOS,
                new UserProperty(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(true)));
    }

    @Test
    public void publishMessageWithCustomQos() {
        final var customMqttQos = MqttQos.EXACTLY_ONCE;
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, underTest);

        underTest.tell(
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(CUSTOM_QOS_HEADER, String.valueOf(customMqttQos.getCode()))),
                        testKit.getRef()
                ),
                testKit.getRef()
        );

        verifyPublishedMessage(getOutboundAddress(),
                DEFAULT_RETAIN,
                customMqttQos,
                new UserProperty(MqttHeader.MQTT_QOS.getName(), String.valueOf(customMqttQos.getCode())));
    }

    @Test
    public void publishMessageWithCustomQosInTarget() {
        final var customMqttQos = MqttQos.EXACTLY_ONCE;
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, underTest);

        underTest.tell(
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(
                                ConnectivityModelFactory.newTargetBuilder(decorateTarget(createTestTarget()))
                                        .qos(customMqttQos.getCode())
                                        .build()
                        )),
                        testKit.getRef()
                ),
                testKit.getRef()
        );

        verifyPublishedMessage(getOutboundAddress(), DEFAULT_RETAIN, customMqttQos);
    }

    @Test
    public void publishMessageWithCustomTopic() {
        final var customTopic = "my/custom/topic";
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, underTest);

        underTest.tell(
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(CUSTOM_TOPIC_HEADER, customTopic)),
                        testKit.getRef()
                ),
                testKit.getRef()
        );

        verifyPublishedMessage(customTopic,
                DEFAULT_RETAIN,
                DEFAULT_QOS,
                new UserProperty(MqttHeader.MQTT_TOPIC.getName(), customTopic));
    }

    @Test
    public void sendPublishToBrokerFailureTriggersNegativeAcknowledgementIfAutoAckTargetPresent() {
        final var illegalStateException = new IllegalStateException("This is totally expected.");
        final var messageSendingFailedException = MessageSendingFailedException.newBuilder()
                .cause(illegalStateException)
                .build();
        Mockito.when(genericMqttPublishingClient.publish(Mockito.any(GenericMqttPublish.class)))
                .thenAnswer(invocationOnMock -> {
                    final GenericMqttPublish genericMqttPublish = invocationOnMock.getArgument(0);
                    received.add(genericMqttPublish);
                    return CompletableFuture.completedFuture(GenericMqttPublishResult.failure(genericMqttPublish,
                            illegalStateException));
                });
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        final var outboundSignal = OutboundSignalFactory.newMultiMappedOutboundSignal(
                List.of(getMockOutboundSignalWithAutoAck(AUTO_ACK_LABEL,
                        DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), testKit.getRef().path().toSerializationFormat()
                )),
                testKit.getRef()
        );
        publisherCreated(testKit, underTest);

        underTest.tell(outboundSignal, testKit.getRef());

        softly.assertThat(testKit.expectMsgClass(ConnectionFailure.class))
                .as("connection failure")
                .satisfies(connectionFailure -> {
                    softly.assertThat(connectionFailure.getFailure())
                            .as("failure")
                            .isEqualTo(new Status.Failure(messageSendingFailedException));
                    softly.assertThat(connectionFailure.getStatus()).as("status").isEmpty();
                });

        softly.assertThat(testKit.expectMsgClass(Acknowledgements.class))
                .as("acknowledgements")
                .satisfies(acknowledgements -> assertThat(acknowledgements)
                        .hasSize(1)
                        .first()
                        .satisfies(acknowledgement -> {
                            softly.assertThat((CharSequence) acknowledgement.getLabel())
                                    .as("label")
                                    .isEqualTo(AUTO_ACK_LABEL);
                            softly.assertThat((CharSequence) acknowledgement.getEntityId())
                                    .as("entity ID")
                                    .isEqualTo(ThingId.of(DEVICE_ID));
                            softly.assertThat(acknowledgement.getHttpStatus())
                                    .as("HTTP status")
                                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            softly.assertThat(acknowledgement.getDittoHeaders())
                                    .as("DittoHeaders")
                                    .containsEntry(DittoHeaderDefinition.CORRELATION_ID.getKey(), "cid")
                                    .containsEntry("device_id", DEVICE_ID)
                                    .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), "false");
                            softly.assertThat(acknowledgement.getEntity(JsonSchemaVersion.LATEST)
                                            .map(JsonValue::toString))
                                    .as("entity")
                                    .hasValueSatisfying(entityJsonString -> softly.assertThat(entityJsonString)
                                            .contains(illegalStateException.getMessage()));
                        }));
    }

    @Test
    public void publishOutboundSignalWithInvalidMqttTopicInHeadersYieldsNegativeAcknowledgement() {
        final var invalidMqttTopic = "target/#";
        final var testKit = new TestKit(actorSystem);
        final var underTest = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, underTest);

        final var outboundSignal = OutboundSignalFactory.newMultiMappedOutboundSignal(
                List.of(getMockOutboundSignalWithAutoAck(AUTO_ACK_LABEL,
                        "custom.topic", invalidMqttTopic,
                        DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), testKit.getRef().path().toSerializationFormat()
                )),
                testKit.getRef()
        );
        underTest.tell(outboundSignal, testKit.getRef());

        softly.assertThat(testKit.expectMsgClass(Acknowledgements.class))
                .satisfies(acknowledgements -> softly.assertThat(acknowledgements)
                        .as("acknowledgements")
                        .hasSize(1)
                        .first()
                        .satisfies(acknowledgement -> {
                            softly.assertThat((CharSequence) acknowledgement.getLabel())
                                    .as("label")
                                    .isEqualTo(AUTO_ACK_LABEL);
                            softly.assertThat((CharSequence) acknowledgement.getEntityId())
                                    .as("entity ID")
                                    .isEqualTo(ThingId.of(DEVICE_ID));
                            softly.assertThat(acknowledgement.getHttpStatus())
                                    .as("HTTP status")
                                    .isEqualTo(HttpStatus.BAD_REQUEST);
                            softly.assertThat(acknowledgement.getDittoHeaders())
                                    .as("DittoHeaders")
                                    .containsEntry(DittoHeaderDefinition.CORRELATION_ID.getKey(), "cid")
                                    .containsEntry("device_id", DEVICE_ID)
                                    .containsEntry(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), "false");
                            softly.assertThat(acknowledgement.getEntity(JsonSchemaVersion.LATEST))
                                    .as("entity")
                                    .hasValue(JsonObject.newBuilder()
                                            .set("error", "connectivity:message.sending.failed")
                                            .set("message", """
                                                    Failed to send message: Failed to transform ExternalMessage to \
                                                    GenericMqttPublish: Topic [target/#] must not contain multi level \
                                                    wildcard (#), found at index 7.\
                                                    """)
                                            .set("description", """
                                                    Sending the message to an external system failed, please check if \
                                                    your connection is configured properly and the target system is \
                                                    available and consuming messages.\
                                                    """)
                                            .build());
                        }));
    }

    @Test
    public void allOutgoingSignalsAreDroppedIfOperatingInDryRunMode() {
        final var testKit = new TestKit(actorSystem);
        final var outboundSignals = List.of(
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignalWithAutoAck(AUTO_ACK_LABEL)),
                        testKit.getRef()
                ),
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(CUSTOM_TOPIC_HEADER, "my/custom/topic")),
                        testKit.getRef()
                ),
                OutboundSignalFactory.newMultiMappedOutboundSignal(
                        List.of(getMockOutboundSignal(CUSTOM_QOS_HEADER,
                                String.valueOf(MqttQos.EXACTLY_ONCE.getCode()))),
                        testKit.getRef()
                )
        );
        final var underTest = testKit.childActorOf(MqttPublisherActor.propsDryRun(TestConstants.createConnection(),
                Mockito.mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()),
                genericMqttPublishingClient));

        outboundSignals.forEach(outboundSignal -> underTest.tell(outboundSignal, testKit.getRef()));

        testKit.expectNoMessage();
        Mockito.verifyNoInteractions(genericMqttPublishingClient);
    }

}
