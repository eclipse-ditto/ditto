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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common;

import java.io.Serial;
import java.text.MessageFormat;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This exception is thrown to indicate that a user provided MQTT QoS code is invalid.
 */
public final class InvalidMqttQosCodeException extends RuntimeException {

    @Serial private static final long serialVersionUID = -7534650891294569575L;

    /**
     * Constructs a {@code InvalidMqttQosCodeException} for the specified invalid MQTT QoS code.
     *
     * @param invalidMqttQosCode an integer that represents an invalid MQTT QoS code.
     */
    public InvalidMqttQosCodeException(final int invalidMqttQosCode) {
        super(createDetailMessage(invalidMqttQosCode));
    }

    private static String createDetailMessage(final int invalidMqttQosCode) {
        return MessageFormat.format("<{0,number,integer}> is not a valid MQTT QoS code.", invalidMqttQosCode);
    }

    /**
     * Constructs a {@code InvalidMqttQosCodeException} for the specified invalid MQTT QoS code.
     *
     * @param invalidMqttQosCode a string that represents an invalid MQTT QoS code.
     * @param cause the cause that makes {@code invalidMqttQosCode} invalid or {@code null}.
     * @throws NullPointerException if {@code invalidMqttQosCode} is {@code null}.
     */
    public InvalidMqttQosCodeException(final String invalidMqttQosCode, final Throwable cause) {
        super(createDetailMessage(ConditionChecker.checkNotNull(invalidMqttQosCode, "invalidMqttQosCode")), cause);
    }

    private static String createDetailMessage(final String invalidMqttQosCode) {
        return MessageFormat.format("<{0}> is not a valid MQTT QoS code.", invalidMqttQosCode);
    }

}
