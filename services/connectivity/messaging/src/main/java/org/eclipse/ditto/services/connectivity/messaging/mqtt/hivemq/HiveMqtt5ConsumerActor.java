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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.model.ContentTypes;

/**
 * Actor which receives message from an MQTT 5 broker and forwards them to a {@code MessageMappingProcessorActor}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5ConsumerActor extends AbstractMqttConsumerActor<Mqtt5Publish> {

    static final String NAME = "HiveMqtt5ConsumerActor";
    @Nullable private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;

    @SuppressWarnings("unused")
    private HiveMqtt5ConsumerActor(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery) {
        super(connectionId, messageMappingProcessor, source, dryRun, reconnectForRedelivery);
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        if (enforcement != null &&
                enforcement.getInput().contains(ConnectivityModelFactory.SOURCE_ADDRESS_ENFORCEMENT)) {
            headerEnforcementFilterFactory = null;
        } else {
            headerEnforcementFilterFactory = enforcement != null ? EnforcementFactoryFactory
                    .newEnforcementFilterFactory(enforcement, PlaceholderFactory.newHeadersPlaceholder()) :
                    input -> null;
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId ID of the connection this consumer is belongs to
     * @param messageMappingProcessor the ActorRef to the {@code MessageMappingProcessor}
     * @param source the source from which this consumer is built
     * @param dryRun whether this is a dry-run/connection test or not
     * @param specificConfig the MQTT specific config.
     * @return the Akka configuration Props object.
     */
    static Props props(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun,
            final MqttSpecificConfig specificConfig) {
        return Props.create(HiveMqtt5ConsumerActor.class, connectionId, messageMappingProcessor, source, dryRun,
                specificConfig.reconnectForRedelivery());
    }

    @Override
    protected Class<Mqtt5Publish> getPublishMessageClass() {
        return Mqtt5Publish.class;
    }

    @Override
    protected HashMap<String, String> extractHeadersMapFromMqttMessage(final Mqtt5Publish message) {
        final HashMap<String, String> headersFromMqttMessage = super.extractHeadersMapFromMqttMessage(message);

        message.getCorrelationData().ifPresent(correlationData -> {
            final String correlationId = ByteBufferUtils.toUtf8String(correlationData);
            headersFromMqttMessage.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        });

        message.getResponseTopic().ifPresent(responseTopic -> {
            final String replyTo = responseTopic.toString();
            headersFromMqttMessage.put(ExternalMessage.REPLY_TO_HEADER, replyTo);
        });

        final Optional<String> contentType = message.getContentType().map(Object::toString);
        if (contentType.isPresent()) {
            headersFromMqttMessage.put(ExternalMessage.CONTENT_TYPE_HEADER, contentType.get());
        } else {
            message.getPayloadFormatIndicator().ifPresent(mqtt5PayloadFormatIndicator -> {
                final String contentTypeFromPayloadIndicator =
                        mqtt5PayloadFormatIndicator == Mqtt5PayloadFormatIndicator.UTF_8
                                ? ContentTypes.TEXT_PLAIN_UTF8.toString()
                                : ContentTypes.APPLICATION_OCTET_STREAM.toString();
                headersFromMqttMessage.put(ExternalMessage.CONTENT_TYPE_HEADER, contentTypeFromPayloadIndicator);
            });
        }

        message.getUserProperties().asList().forEach(
                userProp -> headersFromMqttMessage.put(userProp.getName().toString(), userProp.getValue().toString()));

        return headersFromMqttMessage;
    }

    @Override
    protected Optional<ByteBuffer> getPayload(final Mqtt5Publish message) {
        return message.getPayload();
    }

    @Override
    protected String getTopic(final Mqtt5Publish message) {
        return message.getTopic().toString();
    }

    @Override
    protected MqttQos getQoS(final Mqtt5Publish message) { return message.getQos(); }

    @Override
    protected boolean isRetain(final Mqtt5Publish message) { return message.isRetain(); }

    @Override
    protected void sendPubAck(final Mqtt5Publish message) {
        message.acknowledge();
    }

    @Override
    @Nullable
    EnforcementFilter<CharSequence> getEnforcementFilter(final Map<String, String> headers, final String topic) {
        if (headerEnforcementFilterFactory != null) {
            return headerEnforcementFilterFactory.getFilter(headers);
        } else if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }
}
