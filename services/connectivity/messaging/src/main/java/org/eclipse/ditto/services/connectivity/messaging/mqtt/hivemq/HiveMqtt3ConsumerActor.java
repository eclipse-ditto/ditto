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

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which receives message from an MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class HiveMqtt3ConsumerActor extends AbstractMqttConsumerActor<Mqtt3Publish> {

    static final String NAME = "HiveMqtt3ConsumerActor";

    @SuppressWarnings("unused")
    private HiveMqtt3ConsumerActor(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery) {
        super(connectionId, messageMappingProcessor, source, dryRun, reconnectForRedelivery);
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
        return Props.create(HiveMqtt3ConsumerActor.class, connectionId, messageMappingProcessor,
                source, dryRun, specificConfig.reconnectForRedelivery());
    }

    @Override
    Class<Mqtt3Publish> getPublishMessageClass() {
        return Mqtt3Publish.class;
    }

    @Override
    HashMap<String, String> extractHeadersMapFromMqttMessage(final Mqtt3Publish message) {
        final HashMap<String, String> headersFromMqttMessage = new HashMap<>();

        headersFromMqttMessage.put(MQTT_TOPIC_HEADER, message.getTopic().toString());
        headersFromMqttMessage.put(MQTT_QOS_HEADER, String.valueOf(message.getQos().getCode()));
        headersFromMqttMessage.put(MQTT_RETAIN_HEADER, String.valueOf(message.isRetain()));

        return headersFromMqttMessage;
    }

    @Override
    Optional<ByteBuffer> getPayload(final Mqtt3Publish message) {
        return message.getPayload();
    }

    @Override
    String getTopic(final Mqtt3Publish message) {
        return message.getTopic().toString();
    }

    @Override
    void sendPubAck(final Mqtt3Publish message) {
        message.acknowledge();
    }

    @Override
    @Nullable
    EnforcementFilter<CharSequence> getEnforcementFilter(final Map<String, String> headers, final String topic) {
        if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }
}
