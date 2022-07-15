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
 * Exception that is thrown to indicate that acknowledgement of an MQTT Publish failed.
 */
public sealed class MqttPublishAcknowledgementException extends Exception
        permits MessageAlreadyAcknowledgedException, ManualAcknowledgementDisabledException,
        AcknowledgementUnsupportedException {

    @Serial private static final long serialVersionUID = 8838327545524779231L;

    /**
     * Constructs a {@code MqttPublishAcknowledgementException}.
     *
     * @param detailMessage the detail message that describes the exception.
     */
    MqttPublishAcknowledgementException(final String detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs a {@code MqttPublishAcknowledgementException}.
     *
     * @param cause the cause of the exception.
     */
    MqttPublishAcknowledgementException(final Throwable cause) {
        super(cause);
    }

}

