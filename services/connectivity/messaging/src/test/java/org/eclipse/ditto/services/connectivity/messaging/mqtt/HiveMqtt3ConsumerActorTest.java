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

package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;

import javax.annotation.Nullable;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt3ConsumerActor;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilderBase;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;

/**
 * Unit test for {@link HiveMqtt3ConsumerActor}.
 */
public final class HiveMqtt3ConsumerActorTest {

    @Nullable
    private Mqtt3Subscribe prepareSubscriptions(final Collection<String> topics) {
        if (topics.isEmpty()) {
            return null;
        }

        final Mqtt3SubscribeBuilder.Start subscribeBuilder = topics.stream()
                .map(topic -> Mqtt3Subscription.builder().topicFilter(topic).qos(MqttQos.AT_MOST_ONCE).build())
                .collect(this.collectMqtt3Subscribption());
        if (subscribeBuilder instanceof Mqtt3SubscribeBuilder.Complete) {
            return ((Mqtt3SubscribeBuilder.Complete) subscribeBuilder).build();
        }
        return null;
    }

    private Collector<Mqtt3Subscription, Mqtt3SubscribeBuilder.Start, Mqtt3SubscribeBuilder.Start> collectMqtt3Subscribption() {
        return Collector.of(
                Mqtt3Subscribe::builder,
                Mqtt3SubscribeBuilderBase::addSubscription,
                (builder1, builder2) -> {throw new UnsupportedOperationException("parallel execution not allowed");});
    }

    @Test
    public void testTheSubscriptions() {

        assertThat(prepareSubscriptions(Collections.emptyList())).isNull();

        final Mqtt3Subscribe subscribe = prepareSubscriptions(Lists.list("topic1", "topic2"));
        System.out.println(subscribe);
        final List<Mqtt3Subscription> expected = Lists.list(
                Mqtt3Subscription.builder().topicFilter("topic1").qos(MqttQos.AT_MOST_ONCE).build(),
                Mqtt3Subscription.builder().topicFilter("topic2").qos(MqttQos.AT_MOST_ONCE).build());
        assertThat(subscribe.getSubscriptions())
                .isEqualTo(expected);
    }

}
