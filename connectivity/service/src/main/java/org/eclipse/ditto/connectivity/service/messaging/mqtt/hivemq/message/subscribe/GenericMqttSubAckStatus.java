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

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

/**
 * Generic representation of an MQTT SubAck message status to abstract HiveMQ API for protocol versions 3 and 5.
 */
@Immutable
public final class GenericMqttSubAckStatus {

    private final int code;
    private final String name;
    private final boolean error;

    private GenericMqttSubAckStatus(final int code, final String name, final boolean error) {
        this.code = code;
        this.name = ConditionChecker.checkArgument(checkNotNull(name, "name"),
                arg -> !arg.isBlank(),
                () -> "The name must not be blank.");
        this.error = error;
    }

    /**
     * Returns an instance of {@code GenericMqttSubAckStatus} for the specified {@code Mqtt3SubAckReturnCode} argument.
     *
     * @param mqtt3SubAckReturnCode provides the properties of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3SubAckReturnCode} is {@code null}.
     */
     public static GenericMqttSubAckStatus ofMqtt3SubAckReturnCode(final Mqtt3SubAckReturnCode mqtt3SubAckReturnCode) {
        checkNotNull(mqtt3SubAckReturnCode, "mqtt3SubAckReturnCode");
        return new GenericMqttSubAckStatus(mqtt3SubAckReturnCode.getCode(),
                mqtt3SubAckReturnCode.name(),
                mqtt3SubAckReturnCode.isError());
    }

    /**
     * Returns an instance of {@code GenericMqttSubAckStatus} for the specified {@code Mqtt5SubAckReasonCode} argument.
     *
     * @param mqtt5SubAckReasonCode provides the properties of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5SubAckReasonCode} is {@code null}.
     */
     public static GenericMqttSubAckStatus ofMqtt5SubAckReasonCode(final Mqtt5SubAckReasonCode mqtt5SubAckReasonCode) {
        checkNotNull(mqtt5SubAckReasonCode, "mqtt5SubAckReasonCode");
        return new GenericMqttSubAckStatus(mqtt5SubAckReasonCode.getCode(),
                mqtt5SubAckReasonCode.name(),
                mqtt5SubAckReasonCode.isError());
    }

    /**
     * Returns the code of this MQTT SubAck message status.
     *
     * @return the byte code of this MQTT SubAck message status.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the name of this MQTT SubAck message status.
     *
     * @return the name of this MQTT SubAck message status.
     */
    public String getName() {
        return name;
    }

    /**
     * Indicates whether this status of a MQTT SubAck message is an error.
     *
     * @return {@code true} if this MQTT SubAck message status represents an error, {@code false} else.
     */
    public boolean isError() {
        return error;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (GenericMqttSubAckStatus) o;
        return code == that.code && error == that.error && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, error);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0}: {1}({2,number,integer})",
                isError() ? "Error" : "Success",
                getName(),
                getCode());
    }

}
