/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface MqttConfig {

    /**
     * Returns the maximum number of buffered messages for each MQTT source.
     *
     * @return the buffer size.
     */
    int getSourceBufferSize();

    /**
     * Indicates whether the client should reconnect to enforce a redelivery for a failed acknowledgement.
     *
     * @return true if the client should reconnect, false if not.
     */
    boolean shouldReconnectForRedelivery();

    /**
     * @return the amount of time that a reconnect will be delayed after a failed acknowledgement.
     */
    Duration getReconnectForRedeliveryDelay();

    /**
     * Indicates whether a separate client should be used for publishing. This could be useful when
     * {@link #shouldReconnectForRedelivery()} returns true to avoid that the publisher has downtimes.
     *
     * @return true if a separate client should be used, false if not.
     */
    boolean shouldUseSeparatePublisherClient();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MqttConfig}.
     */
    enum MqttConfigValue implements KnownConfigValue {

        /**
         * The maximum number of buffered messages for each MQTT source.
         */
        SOURCE_BUFFER_SIZE("source-buffer-size", 8),

        /**
         * Indicates whether the client should reconnect to enforce a redelivery for a failed acknowledgement.
         */
        RECONNECT_FOR_REDELIVERY("reconnect-for-redelivery", false),

        /**
         * The amount of time that a reconnect will be delayed after a failed acknowledgement
         */
        RECONNECT_FOR_REDELIVERY_DELAY("reconnect-for-redelivery-delay", Duration.ofSeconds(2)),

        /**
         * Indicates whether a separate client should be used for publishing. This could be useful when
         * {@link #shouldReconnectForRedelivery()} returns true to avoid that the publisher has downtimes.
         */
        SEPARATE_PUBLISHER_CLIENT("separate-publisher-client", false);

        private final String path;
        private final Object defaultValue;

        MqttConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
