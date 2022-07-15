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
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This exception is thrown to indicate that an error occurred during transformation of an MQTT {@code PUBLISH} message
 * to an {@code ExternalMessage} or vice versa.
 */
public class MqttPublishTransformationException extends RuntimeException {

    @Serial private static final long serialVersionUID = 250822237737454667L;

    private final Map<String, String> mqttPublishHeaders;

    /**
     * Constructs a {@code MqttPublishTransformationException}.
     *
     * @param cause the error that caused the constructed exception.
     * @param mqttPublishHeaders the headers of the MQTT {@code PUBLISH} message. May be empty.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MqttPublishTransformationException(final String detailMessage,
            final Throwable cause,
            final Map<String, String> mqttPublishHeaders) {

        super(detailMessage, cause);
        this.mqttPublishHeaders = Map.copyOf(ConditionChecker.checkNotNull(mqttPublishHeaders, "mqttPublishHeaders"));
    }

    /**
     * Returns the headers of the MQTT {@code PUBLISH} message.
     *
     * @return the possibly empty MQTT publish message headers.
     */
    public Map<String, String> getMqttPublishHeaders() {
        return mqttPublishHeaders;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (MqttPublishTransformationException) o;
        return Objects.equals(mqttPublishHeaders, that.mqttPublishHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mqttPublishHeaders);
    }

}
