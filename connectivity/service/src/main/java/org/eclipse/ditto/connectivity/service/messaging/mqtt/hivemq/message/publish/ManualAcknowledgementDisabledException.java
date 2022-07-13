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
 * Thrown to indicate that an MQTT Publish should be manually acknowledged but manual acknowledgement is disabled.
 */
public final class ManualAcknowledgementDisabledException extends MqttPublishAcknowledgementException {

    @Serial private static final long serialVersionUID = 3211487334815235041L;

    /**
     * Constructs a {@code ManualAcknowledgementDisabledException}.
     *
     * @param cause the cause for this exception.
     */
    ManualAcknowledgementDisabledException(final Throwable cause) {
        super(cause);
    }

}
