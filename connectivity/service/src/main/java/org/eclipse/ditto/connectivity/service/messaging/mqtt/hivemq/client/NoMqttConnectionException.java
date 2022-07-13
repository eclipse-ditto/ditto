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

/**
 * This exception is thrown to indicate that a particular Connection type is not an MQTT protocol.
 */
public class NoMqttConnectionException extends Exception {

    @Serial private static final long serialVersionUID = 3468065139441652381L;

    /**
     * Constructs a {@code NoMqttConnectionException} with the specified detail message.
     *
     * @param detailMessage a message that describes the exception.
     */
    NoMqttConnectionException(final String detailMessage) {
        super(detailMessage);
    }

}
