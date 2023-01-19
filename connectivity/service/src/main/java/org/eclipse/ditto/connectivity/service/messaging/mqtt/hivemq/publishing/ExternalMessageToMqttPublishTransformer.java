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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.InvalidMqttQosCodeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationSuccess;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.UserProperty;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

/**
 * Transforms an {@link org.eclipse.ditto.connectivity.api.ExternalMessage} to a {@link GenericMqttPublish}.
 */
@Immutable
final class ExternalMessageToMqttPublishTransformer {

    /*
     * Actually it would be correct to also include MqttHeader.MQTT_TOPIC,
     * MqttHeader.MQTT_QOS and MqttHeader.MQTT_RETAIN in the set of the known
     * MQTT header names because they are already dedicated properties in the
     * MQTT Publish.
     * However, as the named headers were included in user properties
     * up to the present, there might be users who rely on their presence.
     * Excluding the headers from user properties would then break
     * functionality.
     */
    private static final Set<String> KNOWN_MQTT_HEADER_NAMES = Set.of(DittoHeaderDefinition.CORRELATION_ID.getKey(),
            ExternalMessage.REPLY_TO_HEADER,
            ExternalMessage.CONTENT_TYPE_HEADER);

    private ExternalMessageToMqttPublishTransformer() {
        throw new AssertionError();
    }

    /**
     * Transforms the specified {@code ExternalMessage} argument into a {@code GenericMqttPublish}.
     *
     * @param externalMessage the ExternalMessage to be transformed.
     * @param mqttPublishTarget provides fall-back values for MQTT topic and MQTT QoS.
     * @return the result of the transformation.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TransformationResult<ExternalMessage, GenericMqttPublish> transform(
            final ExternalMessage externalMessage,
            final MqttPublishTarget mqttPublishTarget
    ) {
        ConditionChecker.checkNotNull(externalMessage, "externalMessage");
        ConditionChecker.checkNotNull(mqttPublishTarget, "mqttPublishTarget");

        try {
            return TransformationSuccess.of(externalMessage, getGenericMqttPublish(externalMessage, mqttPublishTarget));
        } catch (final Exception e) {
            return TransformationFailure.of(
                    externalMessage,
                    new MqttPublishTransformationException(
                            MessageFormat.format("Failed to transform {0} to {1}: {2}",
                                    ExternalMessage.class.getSimpleName(),
                                    GenericMqttPublish.class.getSimpleName(),
                                    e.getMessage()),
                            e,
                            externalMessage.getInternalHeaders()
                    )
            );
        }
    }

    private static GenericMqttPublish getGenericMqttPublish(final ExternalMessage externalMessage,
            final MqttPublishTarget mqttPublishTarget) {

        final var externalMessageHeaders = externalMessage.getHeaders();
        final var charset = getCharsetOrUtf8(externalMessage);
        final var mqttTopic = getMqttTopic(externalMessageHeaders, mqttPublishTarget);
        final var mqttQos = getMqttQosOrThrow(externalMessageHeaders, mqttPublishTarget);
        final var retain = isRetainOrThrow(externalMessageHeaders);

        return GenericMqttPublish.builder(mqttTopic, mqttQos)
                .retain(retain)
                .payload(getPayloadOrNull(externalMessage, charset))
                .correlationData(getCorrelationDataOrNull(externalMessageHeaders, charset))
                .responseTopic(getResponseTopicOrNull(externalMessageHeaders))
                .contentType(getContentTypeOrNull(externalMessageHeaders))
                .userProperties(getUserPropertiesOrEmptySet(externalMessageHeaders.entrySet(),
                        mqttTopic,
                        mqttQos,
                        retain))
                .build();
    }

    private static Charset getCharsetOrUtf8(final ExternalMessage externalMessage) {
        return externalMessage.findContentType().map(CharsetDeterminer.getInstance()).orElse(StandardCharsets.UTF_8);
    }

    private static MqttTopic getMqttTopic(final Map<String, String> externalMessageHeaders,
            final MqttPublishTarget mqttPublishTarget) {

        final MqttTopic result;
        @Nullable final var mqttTopicHeaderValue = externalMessageHeaders.get(MqttHeader.MQTT_TOPIC.getName());
        if (null == mqttTopicHeaderValue) {
            result = mqttPublishTarget.getTopic();
        } else {
            result = MqttTopic.of(mqttTopicHeaderValue);
        }
        return result;
    }

    private static MqttQos getMqttQosOrThrow(final Map<String, String> externalMessageHeaders,
            final MqttPublishTarget mqttPublishTarget) {

        final MqttQos result;
        @Nullable final var mqttQosHeaderValue = externalMessageHeaders.get(MqttHeader.MQTT_QOS.getName());
        if (null == mqttQosHeaderValue) {
            result = mqttPublishTarget.getQos();
        } else {
            result = getMqttQosFromCodeOrThrow(parseMqttQosCodeFromHeaderValueOrThrow(mqttQosHeaderValue));
        }
        return result;
    }

    private static int parseMqttQosCodeFromHeaderValueOrThrow(final String mqttQosHeaderValue) {
        try {
            return Integer.parseInt(mqttQosHeaderValue);
        } catch (final NumberFormatException e) {
            throw new InvalidHeaderValueException(MqttHeader.MQTT_QOS.getName(),
                    new InvalidMqttQosCodeException(mqttQosHeaderValue, e));
        }
    }

    private static MqttQos getMqttQosFromCodeOrThrow(final int mqttQosCode) {
        @Nullable final var mqttQosFromCode = MqttQos.fromCode(mqttQosCode);
        if (null != mqttQosFromCode) {
            return mqttQosFromCode;
        } else {
            throw new InvalidHeaderValueException(MqttHeader.MQTT_QOS.getName(),
                    new InvalidMqttQosCodeException(mqttQosCode));
        }
    }

    private static boolean isRetainOrThrow(final Map<String, String> externalMessageHeaders) {
        final boolean result;
        final var retainHeaderName = MqttHeader.MQTT_RETAIN.getName();
        @Nullable final var retainHeaderValue = externalMessageHeaders.get(retainHeaderName);
        if (null == retainHeaderValue) {
            result = false;
        } else {
            final var trimmedRetainHeaderValue = retainHeaderValue.trim();
            if (trimmedRetainHeaderValue.equalsIgnoreCase("true")) {
                result = true;
            } else if (trimmedRetainHeaderValue.equalsIgnoreCase("false")) {
                result = false;
            } else {
                throw new InvalidHeaderValueException(retainHeaderName,
                        MessageFormat.format("<{0}> is not a boolean.", retainHeaderValue));
            }
        }
        return result;
    }

    @Nullable
    private static ByteBuffer getPayloadOrNull(final ExternalMessage externalMessage, final Charset charset) {
        return externalMessage.getTextPayload()
                .map(s -> s.getBytes(charset))
                .map(ByteBuffer::wrap)
                .or(externalMessage::getBytePayload)
                .orElse(null);
    }

    @Nullable
    private static ByteBuffer getCorrelationDataOrNull(final Map<String, String> externalMessageHeaders,
            final Charset charset) {

        final ByteBuffer result;
        @Nullable final var correlationId = externalMessageHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        if (null != correlationId) {
            result = ByteBuffer.wrap(correlationId.getBytes(charset));
        } else {
            result = null;
        }
        return result;
    }

    @Nullable
    private static MqttTopic getResponseTopicOrNull(final Map<String, String> externalMessageHeaders) {
        final MqttTopic result;
        @Nullable final var replyToValue = externalMessageHeaders.get(ExternalMessage.REPLY_TO_HEADER);
        if (null != replyToValue) {
            result = MqttTopic.of(replyToValue);
        } else {
            result = null;
        }
        return result;
    }

    @Nullable
    private static String getContentTypeOrNull(final Map<String, String> externalMessageHeaders) {
        return externalMessageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER);
    }

    private static Set<UserProperty> getUserPropertiesOrEmptySet(
            final Collection<Map.Entry<String, String>> externalMessageHeaders,
            final MqttTopic actualMqttTopic,
            final MqttQos actualMqttQos,
            final boolean actualRetain
    ) {
        return externalMessageHeaders.stream()
                .filter(header -> !KNOWN_MQTT_HEADER_NAMES.contains(header.getKey()))
                .filter(header -> header.getValue() != null && !header.getValue().isBlank())
                .map(header -> {
                    final var headerKey = header.getKey();
                    final String headerValue;
                    if (headerKey.equals(MqttHeader.MQTT_TOPIC.getName())) {
                        headerValue = actualMqttTopic.toString();
                    } else if (headerKey.equals(MqttHeader.MQTT_QOS.getName())) {
                        headerValue = String.valueOf(actualMqttQos.getCode());
                    } else if (headerKey.equals(MqttHeader.MQTT_RETAIN.getName())) {
                        headerValue = String.valueOf(actualRetain);
                    } else {
                        headerValue = header.getValue();
                    }
                    return new UserProperty(headerKey, headerValue);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
