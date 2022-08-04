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

import java.io.Serial;

/**
 * This exception is thrown to indicate that a particular String does not represent a valid
 * {@link com.hivemq.client.mqtt.datatypes.MqttTopicFilter}.
 */
public final class InvalidMqttTopicFilterStringException extends Exception {

    @Serial private static final long serialVersionUID = 3082479365980327668L;

    /**
     * Constructs a {@code InvalidMqttTopicFilterException} with the specified detail message and cause.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the cause of the exception.
     */
    InvalidMqttTopicFilterStringException(final String detailMessage, final Throwable cause) {
        super(detailMessage, cause);
    }

}
