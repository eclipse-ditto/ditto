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

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

/**
 * A generic MQTT SubAck message that abstracts MQTT protocol version 3 and 5.
 */
@Immutable
public final class GenericMqttSubAck {

    private final List<GenericMqttSubAckStatus> genericMqttSubAckStatuses;

    private GenericMqttSubAck(final Stream<GenericMqttSubAckStatus> genericMqttSubAckStatuses) {
        this.genericMqttSubAckStatuses = genericMqttSubAckStatuses.toList();
    }

    /**
     * Returns an instance of {@code GenericMqttSubAck} for the specified {@code Mqtt3SubAck} argument.
     *
     * @param mqtt3SubAck provides the return codes of the SubAck statuses of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3SubAck} is {@code null}.
     */
    public static GenericMqttSubAck ofMqtt3SubAck(final Mqtt3SubAck mqtt3SubAck) {
        checkNotNull(mqtt3SubAck, "mqtt3SubAck");
        final var returnCodes = mqtt3SubAck.getReturnCodes();
        return new GenericMqttSubAck(returnCodes.stream().map(GenericMqttSubAckStatus::ofMqtt3SubAckReturnCode));
    }

    /**
     * Returns an instance of {@code GenericMqttSubAck} for the specified {@code Mqtt5SubAck} argument.
     *
     * @param mqtt5SubAck provides the reason codes of the SubAck statuses of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5SubAck} is {@code null}.
     */
    public static GenericMqttSubAck ofMqtt5SubAck(final Mqtt5SubAck mqtt5SubAck) {
        checkNotNull(mqtt5SubAck, "mqtt5SubAck");
        final var reasonCodes = mqtt5SubAck.getReasonCodes();
        return new GenericMqttSubAck(reasonCodes.stream().map(GenericMqttSubAckStatus::ofMqtt5SubAckReasonCode));
    }

    /**
     * Returns an unmodifiable list containing the generic MQTT SubAck statuses.
     *
     * @return the generic MQTT SubAck statuses.
     */
    public List<GenericMqttSubAckStatus> getGenericMqttSubAckStatuses() {
        return genericMqttSubAckStatuses;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        final var that = (GenericMqttSubAck) obj;
        return Objects.equals(genericMqttSubAckStatuses, that.genericMqttSubAckStatuses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genericMqttSubAckStatuses);
    }

}
