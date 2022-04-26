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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Represents the successful subscription of an MQTT client to one or more
 * MQTT topics via one particular Subscribe message ({@link GenericMqttSubscribe}).
 */
final class SubscribeSuccess implements SubscribeResult {

    private final Set<MqttTopicFilter> mqttTopicFilters;
    private final akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> mqttPublishSource;

    private SubscribeSuccess(final Collection<MqttTopicFilter> mqttTopicFilters,
            final Source<GenericMqttPublish, NotUsed> mqttPublishSource) {

        this.mqttTopicFilters = Set.copyOf(mqttTopicFilters);
        this.mqttPublishSource = mqttPublishSource;
    }

    /**
     * Returns a new instance of {@code SubscribeSuccess} for the specified arguments.
     *
     * @param mqttTopicFilters the string representations of MQTT topic filters for which subscribing was
     * successful.
     * @param mqttPublishSource stream of received MQTT Publish messages for the subscribed topics.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code mqttTopicFilters} is empty.
     */
    static SubscribeSuccess newInstance(final Collection<MqttTopicFilter> mqttTopicFilters,
            final Source<GenericMqttPublish, NotUsed> mqttPublishSource) {

        return new SubscribeSuccess(ConditionChecker.argumentNotEmpty(mqttTopicFilters, "mqttTopicFilters"),
                ConditionChecker.checkNotNull(mqttPublishSource, "mqttPublishSource"));
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    /**
     * Returns the MQTT topic filters for which subscribing was successful.
     *
     * @return an unmodifiable Set containing the MQTT topic filters for which subscribing was successful.
     */
    public Set<MqttTopicFilter> getMqttTopicFilters() {
        return mqttTopicFilters;
    }

    @Override
    public akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> getMqttPublishSourceOrThrow() {
        return mqttPublishSource;
    }

    /**
     * Throws always an IllegalStateException.
     */
    @Override
    public MqttSubscribeException getErrorOrThrow() {
        throw new IllegalStateException("Success cannot provide an error.");
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SubscribeSuccess) o;
        return Objects.equals(mqttTopicFilters, that.mqttTopicFilters) &&
                Objects.equals(mqttPublishSource, that.mqttPublishSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mqttTopicFilters, mqttPublishSource);
    }

}
