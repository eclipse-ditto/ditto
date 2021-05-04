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

import java.util.Optional;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

/**
 * Handles subscriptions of MQTT 5 connections.
 *
 * @since 1.1.0
 */
final class HiveMqtt5SubscriptionHandler
        extends AbstractMqttSubscriptionHandler<Mqtt5Subscribe, Mqtt5Publish, Mqtt5SubAck> {

    HiveMqtt5SubscriptionHandler(final Connection connection, final Mqtt5AsyncClient client,
            final ThreadSafeDittoLoggingAdapter logger) {

        super(connection, client::subscribe, logger);
    }

    @Override
    Optional<Mqtt5Subscribe> toMqttSubscribe(final Source source) {
        final Mqtt5SubscribeBuilder.Start subscribeBuilder = Mqtt5Subscribe.builder();
        return source.getAddresses().stream().map(address -> asAddressQoSPair(source, address))
                .map(e -> Mqtt5Subscription.builder()
                        .topicFilter(e.getKey())
                        .qos(e.getValue())
                        .build())
                .map(subscribeBuilder::addSubscription)
                .reduce((b1, b2) -> b2)
                .map(Mqtt5SubscribeBuilder.Complete::build);
    }

}
