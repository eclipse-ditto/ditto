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

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;

/**
 * Handles subscriptions of MQTT connections.
 */
final class HiveMqtt3SubscriptionHandler
        extends AbstractMqttSubscriptionHandler<Mqtt3Subscribe, Mqtt3Publish, Mqtt3SubAck> {

    HiveMqtt3SubscriptionHandler(final Connection connection, final Mqtt3AsyncClient client,
            final ThreadSafeDittoLoggingAdapter logger) {

        super(connection, client::subscribe, logger);
    }

    @Override
    Optional<Mqtt3Subscribe> toMqttSubscribe(final Source source) {
        final Mqtt3SubscribeBuilder.Start subscribeBuilder = Mqtt3Subscribe.builder();
        return source.getAddresses().stream().map(address -> asAddressQoSPair(source, address))
                .map(e -> Mqtt3Subscription.builder()
                        .topicFilter(e.getKey())
                        .qos(e.getValue())
                        .build())
                .map(subscribeBuilder::addSubscription)
                .reduce((b1, b2) -> b2)
                .map(Mqtt3SubscribeBuilder.Complete::build);
    }

}
