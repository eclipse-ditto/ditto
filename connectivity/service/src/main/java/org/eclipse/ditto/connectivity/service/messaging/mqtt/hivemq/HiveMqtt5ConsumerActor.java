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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.NotUsed;
import akka.actor.Props;
import akka.http.javadsl.model.ContentTypes;
import akka.stream.javadsl.Sink;

/**
 * Actor which receives message from an MQTT 5 broker and forwards them to a {@code MessageMappingProcessorActor}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5ConsumerActor extends AbstractMqttConsumerActor<Mqtt5Publish> {

    static final String NAME = "HiveMqtt5ConsumerActor";
    @Nullable private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;

    @SuppressWarnings("unused")
    private HiveMqtt5ConsumerActor(final Connection connection, final Sink<Object, NotUsed> inboundMappingSink,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery,
            final ConnectivityStatusResolver connectivityStatusResolver, final ConnectivityConfig connectivityConfig) {
        super(connection, inboundMappingSink, source, dryRun, reconnectForRedelivery, connectivityStatusResolver,
                connectivityConfig);
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
     * @param connection the connection this consumer belongs to
     * @param inboundMappingSink the mapping sink where received messages are forwarded to
     * @param source the source from which this consumer is built
     * @param dryRun whether this is a dry-run/connection test or not
     * @param specificConfig the MQTT specific config.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection,
            final Sink<Object, NotUsed> inboundMappingSink,
            final Source source,
            final boolean dryRun,
            final MqttSpecificConfig specificConfig,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {
        return Props.create(HiveMqtt5ConsumerActor.class, connection, inboundMappingSink, source, dryRun,
                specificConfig.reconnectForRedelivery(), connectivityStatusResolver, connectivityConfig);
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
    EnforcementFilter<Signal<?>> getEnforcementFilter(final Map<String, String> headers, final String topic) {
        if (headerEnforcementFilterFactory != null) {
            return headerEnforcementFilterFactory.getFilter(headers);
        } else if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }
}
