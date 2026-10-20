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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.SourceEnforcementFilterFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationSuccess;

/**
 * Transforms a {@link GenericMqttPublish} to an {@link ExternalMessage}.
 * Transformation may fail, thus the result is represented as {@link TransformationResult}.
 */
@NotThreadSafe
final class MqttPublishToExternalMessageTransformer {

    private static final Set<String> KNOWN_MQTT_HEADER_NAMES = Stream.concat(
            MqttHeader.getAllHeaderNames().stream(),
            Stream.of(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
                    DittoHeaderDefinition.CORRELATION_ID.getKey(),
                    DittoHeaderDefinition.REPLY_TO.getKey())
    ).collect(Collectors.toSet());

    private final String sourceAddress;
    private final Source connectionSource;
    private final SourceEnforcementFilterFactory sourceEnforcementFilterFactory;

    private MqttPublishToExternalMessageTransformer(final String sourceAddress, final Source connectionSource) {
        this.sourceAddress = sourceAddress;
        this.connectionSource = connectionSource;
        this.sourceEnforcementFilterFactory = SourceEnforcementFilterFactory.of(connectionSource);
    }

    /**
     * Returns a new instance of {@code MqttPublishTransformer} for the specified arguments.
     *
     * @param sourceAddress the source address that will be used for the resulting {@code ExternalMessage}
     * (see {@link ExternalMessage#getSourceAddress()}).
     * @param connectionSource provides the necessary properties like for example {@code AuthorizationContext} or
     * {@code EnforcementFilterFactory}s for creating an {@code ExternalMessage} from a {@code GenericMqttPublish}.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code sourceAddress} is blank.
     */
    static MqttPublishToExternalMessageTransformer newInstance(final String sourceAddress,
            final Source connectionSource) {

        return new MqttPublishToExternalMessageTransformer(
                ConditionChecker.checkArgument(checkNotNull(sourceAddress, "sourceAddress"),
                        s -> !s.isBlank(),
                        () -> "The sourceAddress must not be blank."),
                checkNotNull(connectionSource, "connectionSource"));
    }

    /**
     * Transforms the specified {@code GenericMqttPublish} argument into an {@code ExternalMessage}.
     *
     * @param genericMqttPublish the generic representation of a MQTT {@code PUBLISH} message.
     * @return the result of the transformation.
     * @throws NullPointerException if {@code genericMqttPublish} is {@code null}.
     */
    TransformationResult<GenericMqttPublish, ExternalMessage> transform(final GenericMqttPublish genericMqttPublish) {
        checkNotNull(genericMqttPublish, "genericMqttPublish");
        final var mqttPublishMessageHeaders = getHeadersAsMap(genericMqttPublish);
        try {
            return TransformationSuccess.of(genericMqttPublish,
                    getExternalMessage(genericMqttPublish, mqttPublishMessageHeaders));
        } catch (final Exception e) {
            return TransformationFailure.of(
                    genericMqttPublish,
                    new MqttPublishTransformationException(
                            MessageFormat.format("Failed to transform {0} to {1}: {2}",
                                    GenericMqttPublish.class.getSimpleName(),
                                    ExternalMessage.class.getSimpleName(),
                                    e.getMessage()),
                            e,
                            mqttPublishMessageHeaders
                    )
            );
        }
    }

    private static Map<String, String> getHeadersAsMap(final GenericMqttPublish genericMqttPublish) {
        final var result = new HashMap<String, String>();

        result.put(MqttHeader.MQTT_TOPIC.getName(), getTopicAsString(genericMqttPublish));
        result.put(MqttHeader.MQTT_QOS.getName(), getQosCodeAsString(genericMqttPublish));
        result.put(MqttHeader.MQTT_RETAIN.getName(), getIsRetainAsString(genericMqttPublish));

        getMessageExpiryIntervalAsString(genericMqttPublish)
                .ifPresent(messageExpiryInterval -> result.put(MqttHeader.MQTT_MESSAGE_EXPIRY_INTERVAL.getName(), messageExpiryInterval));

        genericMqttPublish.getCorrelationData()
                .map(ByteBufferUtils::toUtf8String)
                .ifPresent(correlationId -> result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId));

        genericMqttPublish.getResponseTopic()
                .map(String::valueOf)
                .ifPresent(replyTo -> result.put(DittoHeaderDefinition.REPLY_TO.getKey(), replyTo));

        genericMqttPublish.getContentType()
                .ifPresent(contentType -> result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType));

        genericMqttPublish.userProperties()
                .filter(userProperty -> !KNOWN_MQTT_HEADER_NAMES.contains(userProperty.name()))
                .forEach(userProperty -> result.put(userProperty.name(), userProperty.value()));

        return result;
    }

    private static String getTopicAsString(final GenericMqttPublish genericMqttPublish) {
        return String.valueOf(genericMqttPublish.getTopic());
    }

    private static String getQosCodeAsString(final GenericMqttPublish genericMqttPublish) {
        final var mqttQos = genericMqttPublish.getQos();
        return String.valueOf(mqttQos.getCode());
    }

    private static String getIsRetainAsString(final GenericMqttPublish genericMqttPublish) {
        return String.valueOf(genericMqttPublish.isRetain());
    }

    private static Optional<String> getMessageExpiryIntervalAsString(final GenericMqttPublish genericMqttPublish) {
        final var messageExpiryInterval = genericMqttPublish.getMessageExpiryInterval().getAsOptionalLong();
        return messageExpiryInterval.isPresent() ?
                Optional.of(String.valueOf(messageExpiryInterval.getAsLong())) :
                Optional.empty();
    }

    private ExternalMessage getExternalMessage(final GenericMqttPublish genericMqttPublish,
            final Map<String, String> headers) {

        final var publishPayload = getPayload(genericMqttPublish);

        return ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTextAndBytes(ByteBufferUtils.toUtf8String(publishPayload), publishPayload)
                .withAuthorizationContext(connectionSource.getAuthorizationContext())
                .withEnforcement(
                        sourceEnforcementFilterFactory.getFilter(headers, getTopicAsString(genericMqttPublish))
                )
                .withSourceAddress(sourceAddress)
                .withPayloadMapping(connectionSource.getPayloadMapping())
                .withHeaderMapping(connectionSource.getHeaderMapping())
                .build();
    }

    private static ByteBuffer getPayload(final GenericMqttPublish genericMqttPublish) {
        return genericMqttPublish.getPayload()
                .map(ByteBuffer::asReadOnlyBuffer)
                .orElseGet(ByteBufferUtils::empty);
    }

}
