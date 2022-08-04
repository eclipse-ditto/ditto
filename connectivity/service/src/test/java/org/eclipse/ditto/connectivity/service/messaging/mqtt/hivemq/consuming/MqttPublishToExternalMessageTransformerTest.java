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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.UserProperty;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

/**
 * Unit test for {@link MqttPublishToExternalMessageTransformer}.
 */
public final class MqttPublishToExternalMessageTransformerTest {

    private static final String SOURCE_ADDRESS = "source/my-connection-name";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationSubject.newInstance("myIssuer:mySubject")
    );
    private static final HeaderMapping HEADER_MAPPING = ConnectivityModelFactory.newHeaderMapping(Map.of(
            "correlation-id", "{{header:correlation-id}}",
            "content-type", "{{header:content-type}}",
            "reply-to", "{{header:reply-to}}"
    ));
    private static final PayloadMapping PAYLOAD_MAPPING = ConnectivityModelFactory.newPayloadMapping(
            Stream.generate(() -> "Ditto")
                    .limit(5)
                    .collect(Collectors.toList())
    );
    private static final Source SOURCE_WITHOUT_ENFORCEMENT = ConnectivityModelFactory.newSourceBuilder()
            .address(SOURCE_ADDRESS)
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .headerMapping(HEADER_MAPPING)
            .payloadMapping(PAYLOAD_MAPPING)
            .build();
    private static final MqttTopic MQTT_TOPIC = MqttTopic.of("source/my-connection");
    private static final MqttQos MQTT_QOS = MqttQos.AT_LEAST_ONCE;
    private static final boolean RETAIN = true;
    private static final String CORRELATION_ID = String.valueOf(UUID.randomUUID());
    private static final MqttTopic RESPONSE_TOPIC = MqttTopic.of("my-response-topic");
    private static final String CONTENT_TYPE = "application/json";
    private static final List<UserProperty> USER_PROPERTIES = List.of(
            new UserProperty("foo", "bar"),
            new UserProperty("bar", "baz"),
            new UserProperty("baz", "foo")
    );
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
            """);

    @Test
    public void newInstanceWithNullSourceAddressThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishToExternalMessageTransformer.newInstance(null, Mockito.mock(Source.class)))
                .withMessage("The sourceAddress must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithBlankSourceAddressThrowsException() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> MqttPublishToExternalMessageTransformer.newInstance(" ", Mockito.mock(Source.class)))
                .withMessage("The sourceAddress must not be blank.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullConnectionSourceThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS, null))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNonNullArgumentsReturnsNotNull() {
        assertThat(MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS,
                Mockito.mock(Source.class))).isNotNull();
    }

    @Test
    public void transformWithNullGenericMqttPublishThrowsException() {
        final var underTest =
                MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS, Mockito.mock(Source.class));

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.transform(null))
                .withMessage("The genericMqttPublish must not be null!")
                .withNoCause();
    }

    @Test
    public void transformMqtt5PublishWithoutSourceEnforcementReturnsExpectedTransformationSuccess() {
        final var underTest =
                MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS, SOURCE_WITHOUT_ENFORCEMENT);

        final var transformationResult = underTest.transform(GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(RETAIN)
                .correlationData(ByteBufferUtils.fromUtf8String(CORRELATION_ID))
                .responseTopic(RESPONSE_TOPIC)
                .contentType(CONTENT_TYPE)
                .userProperties(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                        .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                        .collect(Collectors.toList())))
                .payload(PAYLOAD)
                .build()));

        assertThat(transformationResult.getSuccessValueOrThrow())
                .isEqualTo(ExternalMessageFactory.newExternalMessageBuilder(Stream.concat(
                                Stream.of(
                                        Map.entry(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString()),
                                        Map.entry(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode())),
                                        Map.entry(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(RETAIN)),
                                        Map.entry(DittoHeaderDefinition.CORRELATION_ID.getKey(), CORRELATION_ID),
                                        Map.entry(DittoHeaderDefinition.REPLY_TO.getKey(), RESPONSE_TOPIC.toString()),
                                        Map.entry(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE)
                                ),
                                USER_PROPERTIES.stream().map(userProp -> Map.entry(userProp.name(), userProp.value()))
                        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                        .withTextAndBytes(ByteBufferUtils.toUtf8String(PAYLOAD), PAYLOAD)
                        .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                        .withSourceAddress(SOURCE_ADDRESS)
                        .withHeaderMapping(HEADER_MAPPING)
                        .withPayloadMapping(PAYLOAD_MAPPING)
                        .build());
    }

    @Test
    public void transformMqtt5PublishWithTopicEnforcementOnlyReturnsExpectedTransformationSuccess() {
        final var enforcement = ConnectivityModelFactory.newSourceAddressEnforcement("things/+/{{ thing:id }}/#");
        final var enforcementFilterFactory = EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                ConnectivityPlaceholders.newSourceAddressPlaceholder());
        final var underTest = MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS,
                ConnectivityModelFactory.newSourceBuilder(SOURCE_WITHOUT_ENFORCEMENT)
                        .enforcement(enforcement)
                        .build());

        final var transformationResult = underTest.transform(GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(RETAIN)
                .correlationData(ByteBufferUtils.fromUtf8String(CORRELATION_ID))
                .responseTopic(RESPONSE_TOPIC)
                .contentType(CONTENT_TYPE)
                .userProperties(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                        .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                        .collect(Collectors.toList())))
                .payload(PAYLOAD)
                .build()));

        assertThat(transformationResult.getSuccessValueOrThrow())
                .isEqualTo(ExternalMessageFactory.newExternalMessageBuilder(Stream.concat(
                                Stream.of(
                                        Map.entry(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString()),
                                        Map.entry(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode())),
                                        Map.entry(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(RETAIN)),
                                        Map.entry(DittoHeaderDefinition.CORRELATION_ID.getKey(), CORRELATION_ID),
                                        Map.entry(DittoHeaderDefinition.REPLY_TO.getKey(), RESPONSE_TOPIC.toString()),
                                        Map.entry(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE)
                                ),
                                USER_PROPERTIES.stream().map(userProp -> Map.entry(userProp.name(), userProp.value()))
                        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                        .withTextAndBytes(ByteBufferUtils.toUtf8String(PAYLOAD), PAYLOAD)
                        .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                        .withEnforcement(enforcementFilterFactory.getFilter(MQTT_TOPIC.toString()))
                        .withSourceAddress(SOURCE_ADDRESS)
                        .withHeaderMapping(HEADER_MAPPING)
                        .withPayloadMapping(PAYLOAD_MAPPING)
                        .build());
    }

    @Test
    public void transformMqtt5PublishWithHeaderEnforcementOnlyReturnsExpectedTransformationSuccess() {
        final var enforcement =
                ConnectivityModelFactory.newEnforcement("{{ header:mqtt.topic }}", MQTT_TOPIC.toString());
        final var headers = Stream.concat(
                Stream.of(
                        Map.entry(MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString()),
                        Map.entry(MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode())),
                        Map.entry(MqttHeader.MQTT_RETAIN.getName(), String.valueOf(RETAIN)),
                        Map.entry(DittoHeaderDefinition.CORRELATION_ID.getKey(), CORRELATION_ID),
                        Map.entry(DittoHeaderDefinition.REPLY_TO.getKey(), RESPONSE_TOPIC.toString()),
                        Map.entry(DittoHeaderDefinition.CONTENT_TYPE.getKey(), CONTENT_TYPE)
                ),
                USER_PROPERTIES.stream().map(userProp -> Map.entry(userProp.name(), userProp.value()))
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        final var enforcementFilterFactory = EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                PlaceholderFactory.newHeadersPlaceholder());
        final var underTest = MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS,
                ConnectivityModelFactory.newSourceBuilder(SOURCE_WITHOUT_ENFORCEMENT)
                        .enforcement(enforcement)
                        .build());

        final var transformationResult = underTest.transform(GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(RETAIN)
                .correlationData(ByteBufferUtils.fromUtf8String(CORRELATION_ID))
                .responseTopic(RESPONSE_TOPIC)
                .contentType(CONTENT_TYPE)
                .userProperties(Mqtt5UserProperties.of(USER_PROPERTIES.stream()
                        .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                        .collect(Collectors.toList())))
                .payload(PAYLOAD)
                .build()));

        assertThat(transformationResult.getSuccessValueOrThrow())
                .isEqualTo(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withTextAndBytes(ByteBufferUtils.toUtf8String(PAYLOAD), PAYLOAD)
                        .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                        .withEnforcement(enforcementFilterFactory.getFilter(headers))
                        .withSourceAddress(SOURCE_ADDRESS)
                        .withHeaderMapping(HEADER_MAPPING)
                        .withPayloadMapping(PAYLOAD_MAPPING)
                        .build());
    }

    @Test
    public void transformMqtt3PublishWithTopicEnforcementOnlyReturnsExpectedTransformationSuccess() {
        final var enforcement = ConnectivityModelFactory.newSourceAddressEnforcement("blub");
        final var enforcementFilterFactory = EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                ConnectivityPlaceholders.newSourceAddressPlaceholder());
        final var underTest = MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS,
                ConnectivityModelFactory.newSourceBuilder(SOURCE_WITHOUT_ENFORCEMENT)
                        .enforcement(enforcement)
                        .build());

        final var transformationResult = underTest.transform(GenericMqttPublish.ofMqtt3Publish(Mqtt3Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(RETAIN)
                .payload(PAYLOAD)
                .build()));

        assertThat(transformationResult.getSuccessValueOrThrow())
                .isEqualTo(ExternalMessageFactory.newExternalMessageBuilder(Map.of(
                                MqttHeader.MQTT_TOPIC.getName(), MQTT_TOPIC.toString(),
                                MqttHeader.MQTT_QOS.getName(), String.valueOf(MQTT_QOS.getCode()),
                                MqttHeader.MQTT_RETAIN.getName(), String.valueOf(RETAIN)
                        ))
                        .withTextAndBytes(ByteBufferUtils.toUtf8String(PAYLOAD), PAYLOAD)
                        .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                        .withEnforcement(enforcementFilterFactory.getFilter(MQTT_TOPIC.toString()))
                        .withSourceAddress(SOURCE_ADDRESS)
                        .withHeaderMapping(HEADER_MAPPING)
                        .withPayloadMapping(PAYLOAD_MAPPING)
                        .build());
    }

    @Test
    public void transformMqtt5PublishWithThrownDittoRuntimeExceptionReturnsTransformationFailure() {
        final var input = "{{ header:mqtt.fopic }}";
        final var enforcement = ConnectivityModelFactory.newEnforcement(input, MQTT_TOPIC.toString());
        final var underTest = MqttPublishToExternalMessageTransformer.newInstance(SOURCE_ADDRESS,
                ConnectivityModelFactory.newSourceBuilder(SOURCE_WITHOUT_ENFORCEMENT)
                        .enforcement(enforcement)
                        .build());

        final var transformationResult = underTest.transform(GenericMqttPublish.ofMqtt5Publish(Mqtt5Publish.builder()
                .topic(MQTT_TOPIC)
                .qos(MQTT_QOS)
                .retain(RETAIN)
                .payload(PAYLOAD)
                .build()));

        assertThat(transformationResult.isFailure()).as("transformation failed").isTrue();
        assertThat(transformationResult.getErrorOrThrow())
                .as("expected transformation failure")
                .isInstanceOf(MqttPublishTransformationException.class)
                .hasMessageContaining(input)
                .hasCauseInstanceOf(UnresolvedPlaceholderException.class);
    }

}
