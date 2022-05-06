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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.GenericMqttAckStatus;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

/**
 * Generic representation of an MQTT SubAck message status to abstract HiveMQ API for protocol versions 3 and 5.
 */
@Immutable
public final class GenericMqttSubAckStatus extends GenericMqttAckStatus {

    private GenericMqttSubAckStatus(final int code, final String name, final boolean error) {
        super(code, name, error);
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

}
