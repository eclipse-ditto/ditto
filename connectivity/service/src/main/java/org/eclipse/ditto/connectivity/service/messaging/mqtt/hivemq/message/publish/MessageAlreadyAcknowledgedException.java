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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;

import java.io.Serial;

/**
 * Thrown to indicate that an MQTT Publish should be acknowledged that was already acknowledged.
 */
public final class MessageAlreadyAcknowledgedException extends MqttPublishAcknowledgementException {

    @Serial private static final long serialVersionUID = 1267114534527105266L;

    /**
     * Constructs a {@code MessageAlreadyAcknowledgedException}.
     *
     * @param cause the cause for this exception.
     */
    MessageAlreadyAcknowledgedException(final Throwable cause) {
        super(cause);
    }

}
