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
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.NotUsed;
import akka.actor.Props;
import akka.stream.javadsl.Sink;

/**
 * Actor which receives message from an MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class HiveMqtt3ConsumerActor extends AbstractMqttConsumerActor<Mqtt3Publish> {

    static final String NAME = "HiveMqtt3ConsumerActor";

    @SuppressWarnings("unused")
    private HiveMqtt3ConsumerActor(final Connection connection, final Sink<Object, NotUsed> inboundMappingSink,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery,
            final ConnectivityStatusResolver connectivityStatusResolver, final ConnectivityConfig connectivityConfig) {
        super(connection, inboundMappingSink, source, dryRun, reconnectForRedelivery, connectivityStatusResolver,
                connectivityConfig);
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
        return Props.create(HiveMqtt3ConsumerActor.class, connection, inboundMappingSink,
                source, dryRun, specificConfig.reconnectForRedelivery(), connectivityStatusResolver,
                connectivityConfig);
    }

    @Override
    protected Class<Mqtt3Publish> getPublishMessageClass() {
        return Mqtt3Publish.class;
    }

    @Override
    protected Optional<ByteBuffer> getPayload(final Mqtt3Publish message) {
        return message.getPayload();
    }

    @Override
    protected String getTopic(final Mqtt3Publish message) {
        return message.getTopic().toString();
    }

    @Override
    protected MqttQos getQoS(final Mqtt3Publish message) { return message.getQos(); }

    @Override
    protected boolean isRetain(final Mqtt3Publish message) { return message.isRetain(); }

    @Override
    protected void sendPubAck(final Mqtt3Publish message) {
        message.acknowledge();
    }

    @Override
    @Nullable
    EnforcementFilter<Signal<?>> getEnforcementFilter(final Map<String, String> headers, final String topic) {
        if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }
}
