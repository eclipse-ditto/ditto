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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.InvalidMqttQosCodeException;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

import scala.util.Try;

/**
 * A {@link PublishTarget} for MQTT.
 * This class bundles an {@link MqttTopic} together with the {@link MqttQos} which both are provided by the persisted
 * connection.
 */
final class MqttPublishTarget implements PublishTarget {

    /**
     * For target the default is qos=0 because we have qos=0 all over the Akka cluster.
     */
    static final MqttQos DEFAULT_TARGET_QOS = MqttQos.AT_MOST_ONCE;

    private final MqttTopic topic;
    private final MqttQos qos;

    private MqttPublishTarget(final MqttTopic topic, final MqttQos qos) {
        this.topic = ConditionChecker.checkNotNull(topic, "topic");
        this.qos = ConditionChecker.checkNotNull(qos, "qos");
    }

    /**
     * Returns a new instance of {@code MqttPublishTarget} for the specified {@code GenericTarget} argument.
     *
     * @param genericTarget provides the address which is used as string representation of the {@link MqttTopic} and
     * an optional MQTT QoS code. If {@code genericTarget} contains no QoS code, {@link #DEFAULT_TARGET_QOS} is used
     * as fall-back because this matches the QoS that is used all over Akka cluster.
     * @return a {@code Success} that contains the {@code MqttPublishTarget} instance or a {@code Failure} if
     * {@code genericTarget} contains an invalid topic or QoS code.
     * @throws NullPointerException if {@code genericTarget} is {@code null}.
     */
    static Try<MqttPublishTarget> tryNewInstance(final GenericTarget genericTarget) {
        ConditionChecker.checkNotNull(genericTarget, "genericTarget");
        return Try.apply(() -> new MqttPublishTarget(
                getMqttTopicOrThrow(genericTarget.getAddress()),
                genericTarget.getQos()
                        .map(MqttPublishTarget::getMqttQosOrThrow)
                        .orElse(DEFAULT_TARGET_QOS)
        ));
    }

    private static MqttTopic getMqttTopicOrThrow(final String mqttTopicString) {
        if (mqttTopicString.isEmpty()) {
            throw new IllegalArgumentException("Topic must be at least one character long.");
        } else if (mqttTopicString.isBlank()) {
            throw new IllegalArgumentException("Topic must not be blank.");
        } else {
            return MqttTopic.of(mqttTopicString);
        }
    }

    private static MqttQos getMqttQosOrThrow(final int mqttQosCode) {
        @Nullable final var mqttQos = MqttQos.fromCode(mqttQosCode);
        if (null != mqttQos) {
            return mqttQos;
        } else {
            throw new InvalidMqttQosCodeException(mqttQosCode);
        }
    }

    MqttTopic getTopic() {
        return topic;
    }

    MqttQos getQos() {
        return qos;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        final var that = (MqttPublishTarget) obj;
        return Objects.equals(topic, that.topic) && Objects.equals(qos, that.qos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, qos);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topic=" + topic +
                ", qos=" + qos +
                "]";
    }

}
