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
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;

import akka.actor.Props;

/**
 * Actor responsible for publishing messages to an MQTT 5 broker using the given {@link Mqtt5Client}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5PublisherActor extends AbstractMqttPublisherActor<Mqtt5Publish, Mqtt5PublishResult> {

    static final String NAME = "HiveMqtt5PublisherActor";

    private static final Set<String> MQTT_HEADER_MAPPING = new HashSet<>();

    static {
        MQTT_HEADER_MAPPING.add(DittoHeaderDefinition.CORRELATION_ID.getKey());
        MQTT_HEADER_MAPPING.add(ExternalMessage.REPLY_TO_HEADER);
        MQTT_HEADER_MAPPING.add(ExternalMessage.CONTENT_TYPE_HEADER);
    }

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt5PublisherActor(final Connection connection,
            final Mqtt5Client client,
            final boolean dryRun,
            final String clientId,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        super(connection, client.toAsync()::publish, dryRun, clientId, connectivityStatusResolver, connectivityConfig);
    }

    /**
     * Create Props object for this publisher actor.
     *
     * @param connection the connection the publisher actor belongs to.
     * @param client the HiveMQ client.
     * @param dryRun whether this publisher is only created for a test or not.
     * @param clientId identifier of the client actor.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @return the Props object.
     */
    public static Props props(final Connection connection,
            final Mqtt5Client client,
            final boolean dryRun,
            final String clientId,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        return Props.create(HiveMqtt5PublisherActor.class,
                connection,
                client,
                dryRun,
                clientId,
                connectivityStatusResolver,
                connectivityConfig);
    }

    @Override
    Mqtt5Publish mapExternalMessageToMqttMessage(final String topic, final MqttQos qos,
            final boolean retain, final ExternalMessage externalMessage) {

        final Charset charset = getCharsetFromMessage(externalMessage);

        final ByteBuffer payload;
        if (externalMessage.isTextMessage()) {
            payload = externalMessage
                    .getTextPayload()
                    .map(text -> ByteBuffer.wrap(text.getBytes(charset)))
                    .orElse(ByteBufferUtils.empty());
        } else if (externalMessage.isBytesMessage()) {
            payload = externalMessage.getBytePayload()
                    .orElse(ByteBufferUtils.empty());
        } else {
            payload = ByteBufferUtils.empty();
        }

        final ByteBuffer correlationData = ByteBuffer.wrap(externalMessage.getHeaders()
                .getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), "").getBytes(charset));

        final String responseTopic = externalMessage.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);

        final String contentType = externalMessage.getHeaders().get(ExternalMessage.CONTENT_TYPE_HEADER);

        final Mqtt5UserProperties userProperties = externalMessage.getHeaders()
                .entrySet()
                .stream()
                .filter(header -> !MQTT_HEADER_MAPPING.contains(header.getKey()))
                .reduce(Mqtt5UserProperties.builder(),
                        (builder, entry) -> builder.add(entry.getKey(), entry.getValue()),
                        (builder1, builder2) -> builder1.addAll(builder2.build().asList())
                )
                .build();

        return Mqtt5Publish.builder()
                .topic(topic)
                .qos(qos)
                .retain(retain)
                .payload(payload)
                .correlationData(correlationData)
                .responseTopic(responseTopic)
                .contentType(contentType)
                .userProperties(userProperties)
                .build();
    }

    @Override
    String getTopic(final Mqtt5Publish message) {
        return message.getTopic().toString();
    }

    @Override
    Optional<ByteBuffer> getPayload(final Mqtt5Publish message) {
        return Optional.empty();
    }
}
