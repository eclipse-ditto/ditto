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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttPublish}.
 */
@RunWith(Enclosed.class)
public final class GenericMqttPublishTest {

    private static final MqttTopic MQTT_TOPIC = MqttTopic.of("/source/my-connection");
    private static final MqttQos MQTT_QOS = MqttQos.AT_LEAST_ONCE;
    private static final boolean RETAIN = true;
    private static final ByteBuffer PAYLOAD = ByteBufferUtils.fromUtf8String("""
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
            }
            """);
    private static final String CONTENT_TYPE = "application/json";
    private static final MqttTopic RESPONSE_TOPIC = MqttTopic.of("source/status");
    private static final Set<UserProperty> USER_PROPERTIES = Set.of(new UserProperty("foo", "bar"),
            new UserProperty("bar", "baz"),
            new UserProperty("baz", "foo"));

    @RunWith(MockitoJUnitRunner.class)
    public static final class GenericMqtt3PublishTest {

        private Mqtt3Publish mqtt3Publish;

        @Before
        public void before() {
            mqtt3Publish = Mqtt3Publish.builder()
                    .topic(MQTT_TOPIC)
                    .qos(MQTT_QOS)
                    .retain(RETAIN)
                    .payload(PAYLOAD)
                    .build();
        }

        @Test
        public void testHashCodeAndEquals() throws ClassNotFoundException {
            EqualsVerifier.forClass(Class.forName("""
                            org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.\
                            GenericMqttPublish$GenericMqtt3Publish\
                            """))
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void ofMqtt3PublishWithNullThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttPublish.ofMqtt3Publish(null))
                    .withMessage("The mqtt3Publish must not be null!")
                    .withNoCause();
        }

        @Test
        public void ofMqtt3PublishReturnsNotNull() {
            assertThat(GenericMqttPublish.ofMqtt3Publish(mqtt3Publish)).isNotNull();
        }

        @Test
        public void getTopicReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getTopic()).isEqualTo(MQTT_TOPIC);
        }

        @Test
        public void getQosReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getQos()).isEqualTo(MQTT_QOS);
        }

        @Test
        public void isRetainReturnsTrue() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.isRetain()).isTrue();
        }

        @Test
        public void getCorrelationDataReturnsEmptyOptional() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getCorrelationData()).isEmpty();
        }

        @Test
        public void getResponseTopicReturnsEmptyOptional() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getResponseTopic()).isEmpty();
        }

        @Test
        public void getContentTypeReturnsEmptyOptional() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getContentType()).isEmpty();
        }

        @Test
        public void userPropertiesReturnsEmptyStream() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.userProperties()).isEmpty();
        }

        @Test
        public void getPayloadReturnsOptionalContainingExpectedValue() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getPayload()).contains(PAYLOAD);
        }

        @Test
        public void getPayloadAsHumanReadableReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(Mqtt3Publish.builder()
                    .topic(MQTT_TOPIC)
                    .payload(PAYLOAD)
                    .build());

            assertThat(underTest.getPayloadAsHumanReadable()).hasValue(ByteBufferUtils.toUtf8String(PAYLOAD));
        }

        @Test
        public void acknowledgeIsDelegatedToMqtt3Publish() throws MqttPublishAcknowledgementException {
            final var mqtt3Publish = Mockito.mock(Mqtt3Publish.class);
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            underTest.acknowledge();

            Mockito.verify(mqtt3Publish).acknowledge();
        }

        @Test
        public void acknowledgeWithDisabledManualAcknowledgementThrowsExpectedException() {
            final var mqtt3Publish = Mockito.mock(Mqtt3Publish.class);
            final var unsupportedOperationException =
                    new UnsupportedOperationException("Manual acknowledgement disabled.");
            Mockito.doThrow(unsupportedOperationException).when(mqtt3Publish).acknowledge();
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThatExceptionOfType(ManualAcknowledgementDisabledException.class)
                    .isThrownBy(underTest::acknowledge)
                    .withMessageEndingWith(unsupportedOperationException.getMessage())
                    .withCause(unsupportedOperationException);
        }

        @Test
        public void acknowledgeWhenAlreadyAcknowledgedThrowsExpectedException() {
            final var mqtt3Publish = Mockito.mock(Mqtt3Publish.class);
            final var illegalStateException = new IllegalStateException("Already acknowledged.");
            Mockito.doThrow(illegalStateException).when(mqtt3Publish).acknowledge();
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThatExceptionOfType(MessageAlreadyAcknowledgedException.class)
                    .isThrownBy(underTest::acknowledge)
                    .withMessageEndingWith(illegalStateException.getMessage())
                    .withCause(illegalStateException);
        }

        @Test
        public void isMqttVersionReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(Mqtt3Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.isMqttVersion(MqttVersion.MQTT_3_1_1)).isTrue();
            assertThat(underTest.isMqttVersion(MqttVersion.MQTT_5_0)).isFalse();
        }

        @Test
        public void getAsMqtt3PublishReturnsPublishProvidedAtObjectConstruction() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getAsMqtt3Publish()).isEqualTo(mqtt3Publish);
        }

        @Test
        public void getAsMqtt5PublishReturnsExpectedMqtt5Publish() {
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.getAsMqtt5Publish())
                    .isEqualTo(Mqtt5Publish.builder()
                            .topic(mqtt3Publish.getTopic())
                            .qos(mqtt3Publish.getQos())
                            .retain(mqtt3Publish.isRetain())
                            .payload(mqtt3Publish.getPayloadAsBytes())
                            .build());
        }

        @Test
        public void toStringReturnsExpected() {
            final var stringRepresentation = "Do not read this!";
            final var mqtt3Publish = Mockito.mock(Mqtt3Publish.class);
            Mockito.when(mqtt3Publish.toString()).thenReturn(stringRepresentation);
            final var underTest = GenericMqttPublish.ofMqtt3Publish(mqtt3Publish);

            assertThat(underTest.toString()).hasToString(stringRepresentation);
        }

    }

    public static final class GenericMqtt5PublishTest {

        private static final Mqtt5Publish MQTT_5_PUBLISH = Mqtt5Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(true)
                .payload(PAYLOAD)
                .contentType(CONTENT_TYPE)
                .userProperties(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                        .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                        .collect(Collectors.toList())))
                .build();

        @Rule
        public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

        @Test
        public void testHashCodeAndEquals() throws ClassNotFoundException {
            EqualsVerifier.forClass(Class.forName("""
                            org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.\
                            GenericMqttPublish$GenericMqtt5Publish\
                            """))
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void ofMqtt5PublishWithNullThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttPublish.ofMqtt5Publish(null))
                    .withMessage("The mqtt5Publish must not be null!")
                    .withNoCause();
        }

        @Test
        public void getTopicReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.getTopic()).isEqualTo(MQTT_TOPIC);
        }

        @Test
        public void getQosReturnsExpected() {
            final var underTest =
                    GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).qos(MQTT_QOS).build());

            assertThat(underTest.getQos()).isEqualTo(MQTT_QOS);
        }

        @Test
        public void isRetainReturnsTrueIfSet() {
            final var underTest =
                    GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).retain(true).build());

            assertThat(underTest.isRetain()).isTrue();
        }

        @Test
        public void isRetainReturnsFalseIfSet() {
            final var underTest =
                    GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).retain(false).build());

            assertThat(underTest.isRetain()).isFalse();
        }

        @Test
        public void isRetainReturnsFalseIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.isRetain()).isFalse();
        }

        @Test
        public void getCorrelationDataReturnsEmptyOptionalIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.getCorrelationData()).isEmpty();
        }

        @Test
        public void getCorrelationDataReturnsOptionalContainingExpectedValueIfPresent() {
            final var correlationData =
                    ByteBufferUtils.fromUtf8String(String.valueOf(testNameCorrelationId.getCorrelationId()));
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .correlationData(correlationData)
                    .build());

            assertThat(underTest.getCorrelationData()).contains(correlationData);
        }

        @Test
        public void getResponseTopicReturnsOptionalContainingExpectedValueIfPresent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .responseTopic(RESPONSE_TOPIC)
                    .build());

            assertThat(underTest.getResponseTopic()).contains(RESPONSE_TOPIC);
        }

        @Test
        public void getResponseTopicReturnsEmptyOptionalIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.getResponseTopic()).isEmpty();
        }

        @Test
        public void getContentTypeReturnsOptionalContainingExpectedValueIfPresent() {
            final var contentType = "application/json";
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .contentType(contentType)
                    .build());

            assertThat(underTest.getContentType()).contains(contentType);
        }

        @Test
        public void getContentTypeReturnsOptionalContainingTextPlainIfIndicatedByPayload() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                    .build());

            assertThat(underTest.getContentType()).contains("text/plain; charset=UTF-8");
        }

        @Test
        public void getContentTypeReturnsOptionalContainingApplicationOctetStreamIfIndicatedByPayload() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UNSPECIFIED)
                    .build());

            assertThat(underTest.getContentType()).contains("application/octet-stream");
        }

        @Test
        public void getContentTypeReturnsEmptyOptionalIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.getContentType()).isEmpty();
        }

        @Test
        public void userPropertiesReturnsExpectedStreamIfPresent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .userProperties(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                            .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                            .collect(Collectors.toList())))
                    .build());

            assertThat(underTest.userProperties()).containsExactlyElementsOf(USER_PROPERTIES);
        }

        @Test
        public void userPropertiesReturnsEmptyStreamIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .build());

            assertThat(underTest.userProperties()).isEmpty();
        }

        @Test
        public void getPayloadReturnsOptionalContainingExpectedValueIfPresent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .payload(PAYLOAD)
                    .build());

            assertThat(underTest.getPayload()).contains(PAYLOAD);
        }

        @Test
        public void getPayloadReturnsEmptyOptionalIfAbsent() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.getPayload()).isEmpty();
        }

        @Test
        public void getPayloadAsHumanReadableReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                    .topic(MQTT_TOPIC)
                    .payload(PAYLOAD)
                    .contentType(CONTENT_TYPE)
                    .build());

            assertThat(underTest.getPayloadAsHumanReadable()).hasValue(ByteBufferUtils.toUtf8String(PAYLOAD));
        }

        @Test
        public void acknowledgeIsDelegatedToMqtt5Publish() throws MqttPublishAcknowledgementException {
            final var mqtt5Publish = Mockito.mock(Mqtt5Publish.class);
            final var underTest = GenericMqttPublish.ofMqtt5Publish(mqtt5Publish);

            underTest.acknowledge();

            Mockito.verify(mqtt5Publish).acknowledge();
        }

        @Test
        public void acknowledgeWithDisabledManualAcknowledgementThrowsExpectedException() {
            final var mqtt5Publish = Mockito.mock(Mqtt5Publish.class);
            final var unsupportedOperationException =
                    new UnsupportedOperationException("Manual acknowledgement disabled.");
            Mockito.doThrow(unsupportedOperationException).when(mqtt5Publish).acknowledge();
            final var underTest = GenericMqttPublish.ofMqtt5Publish(mqtt5Publish);

            assertThatExceptionOfType(ManualAcknowledgementDisabledException.class)
                    .isThrownBy(underTest::acknowledge)
                    .withMessageEndingWith(unsupportedOperationException.getMessage())
                    .withCause(unsupportedOperationException);
        }

        @Test
        public void acknowledgeWhenAlreadyAcknowledgedThrowsExpectedException() {
            final var mqtt5Publish = Mockito.mock(Mqtt5Publish.class);
            final var illegalStateException = new IllegalStateException("Already acknowledged.");
            Mockito.doThrow(illegalStateException).when(mqtt5Publish).acknowledge();
            final var underTest = GenericMqttPublish.ofMqtt5Publish(mqtt5Publish);

            assertThatExceptionOfType(MessageAlreadyAcknowledgedException.class)
                    .isThrownBy(underTest::acknowledge)
                    .withMessageEndingWith(illegalStateException.getMessage())
                    .withCause(illegalStateException);
        }

        @Test
        public void isMqttVersionReturnsExpected() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder().topic(MQTT_TOPIC).build());

            assertThat(underTest.isMqttVersion(MqttVersion.MQTT_3_1_1)).isFalse();
            assertThat(underTest.isMqttVersion(MqttVersion.MQTT_5_0)).isTrue();
        }

        @Test
        public void getAsMqtt3PublishReturnsPublishProvidedAtObjectConstruction() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH);

            assertThat(underTest.getAsMqtt3Publish())
                    .isEqualTo(Mqtt3Publish.builder()
                            .topic(MQTT_5_PUBLISH.getTopic())
                            .qos(MQTT_5_PUBLISH.getQos())
                            .retain(MQTT_5_PUBLISH.isRetain())
                            .payload(MQTT_5_PUBLISH.getPayloadAsBytes())
                            .build());
        }

        @Test
        public void getAsMqtt5PublishReturnsExpectedMqtt5Publish() {
            final var underTest = GenericMqttPublish.ofMqtt5Publish(MQTT_5_PUBLISH);

            assertThat(underTest.getAsMqtt5Publish()).isEqualTo(MQTT_5_PUBLISH);
        }

        @Test
        public void toStringReturnsExpected() {
            final var stringRepresentation = "Do not read this!";
            final var mqtt5Publish = Mockito.mock(Mqtt5Publish.class);
            Mockito.when(mqtt5Publish.toString()).thenReturn(stringRepresentation);
            final var underTest = GenericMqttPublish.ofMqtt5Publish(mqtt5Publish);

            assertThat(underTest.toString()).hasToString(stringRepresentation);
        }

    }

    public static final class FromScratchGenericMqttPublishTest {

        @Rule
        public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

        @Rule
        public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

        private ByteBuffer correlationData;
        private GenericMqttPublish underTest;

        @Before
        public void before() {
            correlationData = ByteBuffer.wrap(
                    String.valueOf(testNameCorrelationId.getCorrelationId()).getBytes(StandardCharsets.UTF_8)
            );
            underTest = GenericMqttPublish.builder(MQTT_TOPIC, MQTT_QOS)
                    .retain(true)
                    .payload(PAYLOAD)
                    .contentType(CONTENT_TYPE)
                    .correlationData(correlationData)
                    .responseTopic(RESPONSE_TOPIC)
                    .userProperties(USER_PROPERTIES)
                    .build();
        }

        @Test
        public void testHashCodeAndEquals() throws ClassNotFoundException {
            EqualsVerifier.forClass(Class.forName("""
                            org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.\
                            GenericMqttPublish$FromScratchGenericMqttPublish\
                            """))
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void instanceByBuilderContainsExpectedProperties() {
            softly.assertThat(underTest.getTopic()).as("topic").isEqualTo(MQTT_TOPIC);
            softly.assertThat(underTest.getQos()).as("QoS").isEqualTo(MQTT_QOS);
            softly.assertThat(underTest.isRetain()).as("retain").isTrue();
            softly.assertThat(underTest.getPayload()).as("payload").hasValue(PAYLOAD);
            softly.assertThat(underTest.getContentType()).as("content type").hasValue(CONTENT_TYPE);
            softly.assertThat(underTest.getCorrelationData()).as("correlation data").hasValue(correlationData);
            softly.assertThat(underTest.getResponseTopic()).as("response topic").hasValue(RESPONSE_TOPIC);
            softly.assertThat(underTest.userProperties()).as("user properties").hasSameElementsAs(USER_PROPERTIES);
        }

        @Test
        public void acknowledgeThrowsExpectedException() {
            assertThatExceptionOfType(AcknowledgementUnsupportedException.class)
                    .isThrownBy(underTest::acknowledge)
                    .withMessage("A GenericMqttPublish that was built from scratch cannot be acknowledged.")
                    .withNoCause();
        }

        @Test
        public void isMqttVersionReturnsAlwaysFalse() {
            for (final var mqttVersion : MqttVersion.values()) {
                softly.assertThat(underTest.isMqttVersion(mqttVersion)).as("is not %s", mqttVersion).isFalse();
            }
        }

        @Test
        public void getPayloadAsHumanReadableReturnsExpected() {
            final var underTest =
                    GenericMqttPublish.builder(MQTT_TOPIC, MQTT_QOS).payload(PAYLOAD).contentType(CONTENT_TYPE).build();

            assertThat(underTest.getPayloadAsHumanReadable()).hasValue(ByteBufferUtils.toUtf8String(PAYLOAD));
        }

        @Test
        public void getAsMqtt3PublishReturnsExpected() {
            final var mqtt3Publish = underTest.getAsMqtt3Publish();

            softly.assertThat(mqtt3Publish.getTopic()).as("topic").isEqualTo(underTest.getTopic());
            softly.assertThat(mqtt3Publish.getQos()).as("QoS").isEqualTo(underTest.getQos());
            softly.assertThat(mqtt3Publish.isRetain()).as("retain").isEqualTo(underTest.isRetain());
            softly.assertThat(mqtt3Publish.getPayload()).as("payload").isEqualTo(underTest.getPayload());
        }

        @Test
        public void getAsMqtt5PublishReturnsExpected() {
            final var mqtt5Publish = underTest.getAsMqtt5Publish();

            softly.assertThat(mqtt5Publish.getTopic()).as("topic").isEqualTo(underTest.getTopic());
            softly.assertThat(mqtt5Publish.getQos()).as("QoS").isEqualTo(underTest.getQos());
            softly.assertThat(mqtt5Publish.isRetain()).as("retain").isEqualTo(underTest.isRetain());
            softly.assertThat(mqtt5Publish.getPayload()).as("payload").isEqualTo(underTest.getPayload());
            softly.assertThat(mqtt5Publish.getContentType())
                    .as("content type")
                    .hasValue(MqttUtf8String.of(CONTENT_TYPE));
            softly.assertThat(mqtt5Publish.getCorrelationData())
                    .as("correlation data")
                    .isEqualTo(underTest.getCorrelationData());
            softly.assertThat(mqtt5Publish.getResponseTopic())
                    .as("response topic")
                    .isEqualTo(underTest.getResponseTopic());
            softly.assertThat(mqtt5Publish.getUserProperties())
                    .as("user properties")
                    .isEqualTo(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                            .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                            .collect(Collectors.toList())));
        }

    }

}