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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationSuccess;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.UserProperty;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

/**
 * Unit test for {@link ExternalMessageToMqttPublishTransformer}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ExternalMessageToMqttPublishTransformerTest {

    private static final MqttTopic MQTT_TOPIC = MqttTopic.of("target/status");
    private static final MqttTopic MQTT_TOPIC_FALL_BACK = MqttTopic.of("target/fall-back");
    private static final MqttQos MQTT_QOS = MqttQos.AT_LEAST_ONCE;
    private static final MqttQos MQTT_QOS_FALL_BACK = MqttQos.EXACTLY_ONCE;
    private static final Boolean RETAIN = true;
    private static final Boolean RETAIN_FALL_BACK = false;
    private static final ContentType CONTENT_TYPE = ContentType.APPLICATION_JSON;
    private static final MqttTopic REPLY_TO_TOPIC = MqttTopic.of("replies");
    private static final Set<UserProperty> USER_PROPERTIES =
            Set.of(new UserProperty(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString()),
                    new UserProperty(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode())),
                    new UserProperty(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(RETAIN)),
                    new UserProperty("foo", "bar"),
                    new UserProperty("bar", "baz"),
                    new UserProperty("baz", "foo"));
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
            }\
            """).asReadOnlyBuffer();
    private static MqttPublishTarget mqttPublishTarget;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private ExternalMessage externalMessage;

    @BeforeClass
    public static void beforeClass() {
        final var genericTarget = Mockito.mock(GenericTarget.class);
        Mockito.when(genericTarget.getAddress()).thenReturn(MQTT_TOPIC_FALL_BACK.toString());
        Mockito.when(genericTarget.getQos()).thenReturn(Optional.of(MQTT_QOS_FALL_BACK.getCode()));

        mqttPublishTarget = MqttPublishTarget.tryNewInstance(genericTarget).get();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ExternalMessageToMqttPublishTransformer.class, areImmutable());
    }

    @Test
    public void transformWithNullExternalMessageThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> ExternalMessageToMqttPublishTransformer.transform(null, mqttPublishTarget))
                .withMessage("The externalMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void transformWithNullMqttPublishTargetThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> ExternalMessageToMqttPublishTransformer.transform(externalMessage, null))
                .withMessage("The mqttPublishTarget must not be null!")
                .withNoCause();
    }

    @Test
    public void transformFullyFledgedExternalMessageReturnsExpectedTransformationSuccessResult() {
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var genericMqttPublish = GenericMqttPublish.builder(MQTT_TOPIC, MQTT_QOS)
                .retain(RETAIN)
                .payload(PAYLOAD)
                .correlationData(ByteBufferUtils.fromUtf8String(correlationId.toString()))
                .contentType(CONTENT_TYPE.getValue())
                .responseTopic(REPLY_TO_TOPIC)
                .userProperties(USER_PROPERTIES)
                .build();
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString())
                        .putHeader(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode()))
                        .putHeader(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(genericMqttPublish.isRetain()))
                        .correlationId(correlationId)
                        .putHeader(ExternalMessage.REPLY_TO_HEADER, REPLY_TO_TOPIC.toString())
                        .contentType(CONTENT_TYPE)
                        .putHeaders(USER_PROPERTIES.stream()
                                .collect(Collectors.toMap(UserProperty::name, UserProperty::value)))
                        .build());
        Mockito.when(externalMessage.getBytePayload()).thenReturn(genericMqttPublish.getPayload());

        assertThat(ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget))
                .isEqualTo(TransformationSuccess.of(externalMessage, genericMqttPublish));
    }

    @Test
    public void transformExternalMessageWithoutMqttTopicInHeadersFallsBackToTopicOfMqttPublishTarget() {
        Mockito.when(externalMessage.getHeaders()).thenReturn(DittoHeaders.empty());

        assertThat(ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget))
                .isEqualTo(TransformationSuccess.of(externalMessage,
                        GenericMqttPublish.builder(MQTT_TOPIC_FALL_BACK, MQTT_QOS_FALL_BACK).build()));
    }

    @Test
    public void transformExternalMessageWithInvalidMqttTopicInHeadersYieldsTransformationFailure() {
        final var invalidMqttTopic = "target/#";
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_TOPIC.getName(), invalidMqttTopic)
                        .build());
        Mockito.when(externalMessage.getInternalHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        final var transformationResult =
                ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget);

        assertThat(transformationResult.getErrorOrThrow())
                .isInstanceOf(MqttPublishTransformationException.class)
                .hasMessage("""
                                Failed to transform ExternalMessage to GenericMqttPublish: Topic [%s] must not contain multi \
                                level wildcard (#), found at index %d.\
                                """,
                        invalidMqttTopic,
                        invalidMqttTopic.length() - 1
                )
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void transformExternalMessageWithoutMqttQosInHeadersFallsBackToQosOfMqttPublishTarget() {
        Mockito.when(externalMessage.getHeaders()).thenReturn(DittoHeaders.empty());

        assertThat(ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget))
                .isEqualTo(TransformationSuccess.of(externalMessage,
                        GenericMqttPublish.builder(MQTT_TOPIC_FALL_BACK, MQTT_QOS_FALL_BACK).build()));
    }

    @Test
    public void transformExternalMessageWithInvalidMqttQosCodeInHeadersYieldsTransformationFailure() {
        final var invalidMqttQosCode = String.valueOf(42);
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_QOS.getName(), invalidMqttQosCode)
                        .build());
        Mockito.when(externalMessage.getInternalHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        final var transformationResult =
                ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget);

        assertThat(transformationResult.getErrorOrThrow())
                .isInstanceOf(MqttPublishTransformationException.class)
                .hasMessage("""
                        Failed to transform ExternalMessage to GenericMqttPublish: \
                        Invalid value for header <%s>: <%s> is not a valid MQTT QoS code.\
                        """,
                        MqttHeader.MQTT_QOS.getName(),
                        invalidMqttQosCode
                )
                .hasCauseInstanceOf(InvalidHeaderValueException.class);
    }

    @Test
    public void transformExternalMessageWithNonIntegerMqttQosCodeInHeadersYieldsTransformationFailure() {
        final var invalidMqttQosCode = "zoiglfrex";
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_QOS.getName(), invalidMqttQosCode)
                        .build());
        Mockito.when(externalMessage.getInternalHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        final var transformationResult =
                ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget);

        assertThat(transformationResult.getErrorOrThrow())
                .isInstanceOf(MqttPublishTransformationException.class)
                .hasMessage("""
                                Failed to transform ExternalMessage to GenericMqttPublish: \
                                Invalid value for header <%s>: \
                                <%s> is not a valid MQTT QoS code.\
                                """,
                        MqttHeader.MQTT_QOS.getName(),
                        invalidMqttQosCode)
                .hasCauseInstanceOf(InvalidHeaderValueException.class);
    }

    @Test
    public void transformExternalMessageWithoutRetainValueInHeadersFallsBackToFalse() {
        Mockito.when(externalMessage.getHeaders()).thenReturn(DittoHeaders.empty());

        assertThat(ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget))
                .isEqualTo(TransformationSuccess.of(externalMessage,
                        GenericMqttPublish.builder(MQTT_TOPIC_FALL_BACK, MQTT_QOS_FALL_BACK)
                                .retain(false)
                                .build()));
    }

    @Test
    public void transformExternalMessageWithInvalidRetainValueYieldsTransformationFailure() {
        final var invalidRetainValue = "zoiglfrex";
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_RETAIN.getName(), invalidRetainValue)
                        .build());

        Mockito.when(externalMessage.getInternalHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        final var transformationResult =
                ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget);

        assertThat(transformationResult.getErrorOrThrow())
                .isInstanceOf(MqttPublishTransformationException.class)
                .hasMessage("""
                                Failed to transform ExternalMessage to GenericMqttPublish: \
                                Invalid value for header <%s>: \
                                <%s> is not a boolean.\
                                """,
                        MqttHeader.MQTT_RETAIN.getName(),
                        invalidRetainValue)
                .hasCauseInstanceOf(InvalidHeaderValueException.class);
    }

    @Test
    public void transformFullyFledgedExternalMessageWithBlankHeaderReturnsExpectedTransformationSuccessResult() {
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var genericMqttPublish = GenericMqttPublish.builder(MQTT_TOPIC, MQTT_QOS)
                .retain(RETAIN)
                .payload(PAYLOAD)
                .correlationData(ByteBufferUtils.fromUtf8String(correlationId.toString()))
                .contentType(CONTENT_TYPE.getValue())
                .responseTopic(REPLY_TO_TOPIC)
                .userProperties(USER_PROPERTIES)
                .build();
        Mockito.when(externalMessage.getHeaders())
                .thenReturn(DittoHeaders.newBuilder()
                        .putHeader(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString())
                        .putHeader(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode()))
                        .putHeader(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(genericMqttPublish.isRetain()))
                        .putHeader("ablankheader", "")
                        .correlationId(correlationId)
                        .putHeader(ExternalMessage.REPLY_TO_HEADER, REPLY_TO_TOPIC.toString())
                        .contentType(CONTENT_TYPE)
                        .putHeaders(USER_PROPERTIES.stream()
                                .collect(Collectors.toMap(UserProperty::name, UserProperty::value)))
                        .build());
        Mockito.when(externalMessage.getBytePayload()).thenReturn(genericMqttPublish.getPayload());

        assertThat(ExternalMessageToMqttPublishTransformer.transform(externalMessage, mqttPublishTarget))
                .isEqualTo(TransformationSuccess.of(externalMessage, genericMqttPublish));
    }
}