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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.GenericMqttAckStatus;

import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;

/**
 * Generic representation of an MQTT ConnAck message status to abstract HiveMQ API for protocol versions 3 and 5.
 */
public final class GenericMqttConnAckStatus extends GenericMqttAckStatus {

    private GenericMqttConnAckStatus(final int code, final String name, final boolean error) {
        super(code, name, error);
    }

    /**
     * Returns an instance of {@code GenericMqttConnAckStatus} for the specified {@code Mqtt3ConnAckReturnCode}
     * argument.
     *
     * @param mqtt3ConnAckReturnCode provides the properties of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3ConnAckReturnCode} is {@code null}.
     */
    public static GenericMqttConnAckStatus ofMqtt3ConnAckReturnCode(final Mqtt3ConnAckReturnCode mqtt3ConnAckReturnCode) {
        checkNotNull(mqtt3ConnAckReturnCode, "mqtt3ConnAckReturnCode");
        return new GenericMqttConnAckStatus(mqtt3ConnAckReturnCode.getCode(),
                mqtt3ConnAckReturnCode.name(),
                mqtt3ConnAckReturnCode.isError());
    }

    /**
     * Returns an instance of {@code GenericMqttConnAckStatus} for the specified {@code Mqtt5ConnAckReasonCode}
     * argument.
     *
     * @param mqtt5ConnAckReasonCode provides the properties of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5ConnAckReasonCode} is {@code null}.
     */
    public static GenericMqttConnAckStatus ofMqtt5ConnAckReasonCode(final Mqtt5ConnAckReasonCode mqtt5ConnAckReasonCode) {
        checkNotNull(mqtt5ConnAckReasonCode, "mqtt5ConnAckReasonCode");
        return new GenericMqttConnAckStatus(mqtt5ConnAckReasonCode.getCode(),
                mqtt5ConnAckReasonCode.name(),
                mqtt5ConnAckReasonCode.isError());
    }

}
