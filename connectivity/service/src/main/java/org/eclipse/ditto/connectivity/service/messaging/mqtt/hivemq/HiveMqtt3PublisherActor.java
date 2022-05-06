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
import java.util.Optional;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.Props;

/**
 * Actor responsible for publishing messages to an MQTT broker using the given {@link Mqtt3Client}.
 */
public final class HiveMqtt3PublisherActor extends AbstractMqttPublisherActor<Mqtt3Publish, Mqtt3Publish> {

    static final String NAME = "HiveMqtt3PublisherActor";

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt3PublisherActor(final Connection connection,
            final Mqtt3Client client,
            final boolean dryRun,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        super(connection, client.toAsync()::publish, dryRun, connectivityStatusResolver, connectivityConfig);
    }

    /**
     * Create Props object for this publisher actor.
     *
     * @param connection the connection the publisher actor belongs to.
     * @param client the HiveMQ client.
     * @param dryRun whether this publisher is only created for a test or not.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @return the Props object.
     */
    public static Props props(final Connection connection,
            final Mqtt3Client client,
            final boolean dryRun,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        return Props.create(HiveMqtt3PublisherActor.class,
                connection,
                client,
                dryRun,
                connectivityStatusResolver,
                connectivityConfig);
    }

    @Override
    Mqtt3Publish mapExternalMessageToMqttMessage(final String topic, final MqttQos qos,
            final boolean retain, final ExternalMessage externalMessage) {

        final ByteBuffer payload;
        if (externalMessage.isTextMessage()) {
            final Charset charset = getCharsetFromMessage(externalMessage);
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
        return Mqtt3Publish.builder()
                .topic(topic)
                .qos(qos)
                .retain(retain)
                .payload(payload)
                .build();
    }

    @Override
    String getTopic(final Mqtt3Publish message) {
        return message.getTopic().toString();
    }

    @Override
    Optional<ByteBuffer> getPayload(final Mqtt3Publish message) {
        return message.getPayload();
    }
}
