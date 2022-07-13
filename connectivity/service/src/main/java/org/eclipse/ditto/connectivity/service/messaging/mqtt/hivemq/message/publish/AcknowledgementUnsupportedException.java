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
 * This exception is thrown if an MQTT Publish message should be acknowledged that does not support acknowledgement.
 */
public final class AcknowledgementUnsupportedException extends MqttPublishAcknowledgementException {

    @Serial private static final long serialVersionUID = -8075192009635996607L;

    /**
     * Constructs a {@code AcknowledgementUnsupportedException}.
     *
     * @param detailMessage the detail message that describes the exception.
     */
    AcknowledgementUnsupportedException(final String detailMessage) {
        super(detailMessage);
    }

}
