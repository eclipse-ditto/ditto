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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

/**
 * Status of a single subscription of a sent Subscribe message ({@link GenericMqttSubscribe}).
 * This type associates a {@link MqttTopicFilter} with its {@link GenericMqttSubAckStatus}.
 * The SubAck message status indicates whether the subscription to the topic filter was successful or if it failed with
 * an error.
 */
@Immutable
public final class SubscriptionStatus {

    private final MqttTopicFilter mqttTopicFilter;
    private final GenericMqttSubAckStatus genericMqttSubAckStatus;

    private SubscriptionStatus(final MqttTopicFilter mqttTopicFilter,
            final GenericMqttSubAckStatus genericMqttSubAckStatus) {

        this.mqttTopicFilter = checkNotNull(mqttTopicFilter, "mqttTopicFilter");
        this.genericMqttSubAckStatus = checkNotNull(genericMqttSubAckStatus, "genericMqttSubAckStatus");
    }

    /**
     * Returns a new instance of {@code SubscriptionStatus} for the specified arguments.
     *
     * @param mqttTopicFilter the MQTT topic filter.
     * @param genericMqttSubAckStatus the MQTT SubAck status which is associated with {@code mqttTopicFilter}.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubscriptionStatus newInstance(final MqttTopicFilter mqttTopicFilter,
            final GenericMqttSubAckStatus genericMqttSubAckStatus) {

        return new SubscriptionStatus(mqttTopicFilter, genericMqttSubAckStatus);
    }

    /**
     * Returns the MQTT topic filter.
     *
     * @return the MQTT topic filter.
     */
    public MqttTopicFilter getMqttTopicFilter() {
        return mqttTopicFilter;
    }

    /**
     * Returns the MQTT SubAck status.
     *
     * @return the MQTT SubAck status.
     */
    public GenericMqttSubAckStatus getGenericMqttSubAckStatus() {
        return genericMqttSubAckStatus;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (SubscriptionStatus) obj;
        return Objects.equals(mqttTopicFilter, that.mqttTopicFilter) &&
                Objects.equals(genericMqttSubAckStatus, that.genericMqttSubAckStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mqttTopicFilter, genericMqttSubAckStatus);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0}[Topic filter: {1} => {2}]",
                getClass().getSimpleName(),
                mqttTopicFilter,
                genericMqttSubAckStatus);
    }

}
