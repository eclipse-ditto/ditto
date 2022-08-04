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

import java.io.Serial;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;

/**
 * This exception is thrown to indicate that {@link GenericMqttConnectableClient#connect(GenericMqttConnect)} failed.
 */
public final class MqttClientConnectException extends RuntimeException {

    @Serial private static final long serialVersionUID = 698683333835638078L;

    /**
     * Constructs a {@code MqttClientConnectException} for the specified detail message and cause.
     *
     * @param detailMessage a message that describes the exception.
     * @param cause the cause of the exception.
     */
    public MqttClientConnectException(final String detailMessage, @Nullable final Throwable cause) {
        super(detailMessage, cause);
    }

    /**
     * Constructs a {@code MqttClientConnectException} for the specified detail message and cause.
     *
     * @param cause the cause of the exception.
     */
    public MqttClientConnectException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }

}
