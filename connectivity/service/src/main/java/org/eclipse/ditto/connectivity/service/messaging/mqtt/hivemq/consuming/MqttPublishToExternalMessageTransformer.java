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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.MqttPublishTransformationException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.TransformationSuccess;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import com.hivemq.client.mqtt.MqttVersion;

/**
 * Transforms a {@link GenericMqttPublish} to an {@link ExternalMessage}.
 * Transformation may fail, thus the result is represented as {@link TransformationResult}.
 */
@NotThreadSafe
final class MqttPublishToExternalMessageTransformer {

    private final String sourceAddress;
    private final Source connectionSource;
    @Nullable private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    @Nullable private final EnforcementFilterFactory<String, Signal<?>> topicEnforcementFilterFactory;

    private MqttPublishToExternalMessageTransformer(final String sourceAddress,
            final Source connectionSource,
            @Nullable final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory,
            @Nullable final EnforcementFilterFactory<String, Signal<?>> topicEnforcementFilterFactory) {

        this.sourceAddress = sourceAddress;
        this.connectionSource = connectionSource;
        this.headerEnforcementFilterFactory = headerEnforcementFilterFactory;
        this.topicEnforcementFilterFactory = topicEnforcementFilterFactory;
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
                checkNotNull(connectionSource, "connectionSource"),
                getHeaderEnforcementFilterFactoryOrNull(connectionSource),
                getTopicEnforcementFilterFactoryOrNull(connectionSource)
        );
    }

    @Nullable
    private static EnforcementFilterFactory<Map<String, String>, Signal<?>> getHeaderEnforcementFilterFactoryOrNull(
            final Source connectionSource
    ) {
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> result;
        final var enforcementOptional = connectionSource.getEnforcement();
        if (enforcementOptional.isPresent()) {
            final var enforcement = enforcementOptional.get();
            final var enforcementInput = enforcement.getInput();
            if (enforcementInput.contains(ConnectivityModelFactory.SOURCE_ADDRESS_ENFORCEMENT)) {
                result = null;
            } else {
                result = EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        PlaceholderFactory.newHeadersPlaceholder());
            }
        } else {
            result = headers -> null;
        }
        return result;
    }

    @Nullable
    private static EnforcementFilterFactory<String, Signal<?>> getTopicEnforcementFilterFactoryOrNull(
            final Source connectionSource
    ) {
        return connectionSource.getEnforcement()
                .map(enforcement -> EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                        ConnectivityPlaceholders.newSourceAddressPlaceholder()))
                .orElse(null);
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

        genericMqttPublish.getCorrelationData()
                .map(ByteBufferUtils::toUtf8String)
                .ifPresent(correlationId -> result.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId));

        genericMqttPublish.getResponseTopic()
                .map(String::valueOf)
                .ifPresent(replyTo -> result.put(DittoHeaderDefinition.REPLY_TO.getKey(), replyTo));

        genericMqttPublish.getContentType()
                .ifPresent(contentType -> result.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType));

        genericMqttPublish.userProperties()
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

    private ExternalMessage getExternalMessage(final GenericMqttPublish genericMqttPublish,
            final Map<String, String> headers) {

        final var publishPayload = getPayload(genericMqttPublish);

        return ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTextAndBytes(ByteBufferUtils.toUtf8String(publishPayload), publishPayload)
                .withAuthorizationContext(connectionSource.getAuthorizationContext())
                .withEnforcement(
                        getEnforcementFilter(
                                genericMqttPublish.isMqttVersion(MqttVersion.MQTT_5_0),
                                headers,
                                getTopicAsString(genericMqttPublish)
                        ).orElse(null)
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

    private Optional<EnforcementFilter<Signal<?>>> getEnforcementFilter(final boolean mqttVersion5,
            final Map<String, String> headers,
            final String topic) {

        final Optional<EnforcementFilter<Signal<?>>> result;
        if (mqttVersion5) {
            result = getHeaderEnforcementFilter(headers).or(() -> getTopicEnforcementFilter(topic));
        } else {
            result = getTopicEnforcementFilter(topic);
        }
        return result;
    }

    private Optional<EnforcementFilter<Signal<?>>> getHeaderEnforcementFilter(final Map<String, String> headers) {
        final Optional<EnforcementFilter<Signal<?>>> result;
        if (null != headerEnforcementFilterFactory) {
            result = Optional.ofNullable(headerEnforcementFilterFactory.getFilter(headers));
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private Optional<EnforcementFilter<Signal<?>>> getTopicEnforcementFilter(final String topic) {
        final Optional<EnforcementFilter<Signal<?>>> result;
        if (null != topicEnforcementFilterFactory) {
            result = Optional.ofNullable(topicEnforcementFilterFactory.getFilter(topic));
        } else {
            result = Optional.empty();
        }
        return result;
    }

}
