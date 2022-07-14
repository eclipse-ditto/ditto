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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;

/**
 * Generic representation of a subscription in an MQTT Subscribe message.
 */
@Immutable
public final class GenericMqttSubscription {

    private final MqttTopicFilter mqttTopicFilter;
    private final MqttQos mqttQos;

    private GenericMqttSubscription(final MqttTopicFilter mqttTopicFilter, final MqttQos mqttQos) {
        this.mqttTopicFilter = mqttTopicFilter;
        this.mqttQos = mqttQos;
    }

    /**
     * Returns a new instance of {@code GenericMqttSubscription} for the specified arguments.
     *
     * @param mqttTopicFilter the MQTT topic filter of the returned subscription.
     * @param mqttQos the MQTT QoS of the returned subscription.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static GenericMqttSubscription newInstance(final MqttTopicFilter mqttTopicFilter, final MqttQos mqttQos) {
        return new GenericMqttSubscription(checkNotNull(mqttTopicFilter, "mqttTopicFilter"),
                checkNotNull(mqttQos, "mqttQos"));
    }

    public MqttTopicFilter getMqttTopicFilter() {
        return mqttTopicFilter;
    }

    public MqttQos getMqttQos() {
        return mqttQos;
    }

    public Mqtt3Subscription getAsMqtt3Subscription() {
        return Mqtt3Subscription.builder()
                .topicFilter(mqttTopicFilter)
                .qos(mqttQos)
                .build();
    }

    public Mqtt5Subscription getAsMqtt5Subscription() {
        return Mqtt5Subscription.builder()
                .topicFilter(mqttTopicFilter)
                .qos(mqttQos)
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        final var that = (GenericMqttSubscription) obj;
        return Objects.equals(mqttTopicFilter, that.mqttTopicFilter) && Objects.equals(mqttQos, that.mqttQos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mqttTopicFilter, mqttQos);
    }

    @Override
    public String toString() {
        return "GenericMqttSubscription[" +
                "mqttTopicFilter=" + mqttTopicFilter + ", " +
                "qos=" + mqttQos + ']';
    }

}
