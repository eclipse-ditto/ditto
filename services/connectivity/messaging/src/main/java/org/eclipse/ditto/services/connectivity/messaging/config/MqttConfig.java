/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface MqttConfig {

    /**
     * @see MqttConfigValue#LEGACY_MODE
     * @return see {@link MqttConfigValue#LEGACY_MODE}.
     */
    boolean isLegacyMode();

    /**
     * Returns the maximum number of buffered messages for each MQTT source.
     *
     * @return the buffer size.
     */
    int getSourceBufferSize();

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
         * If Ditto should use the "legacy" MQTT client (based on alpakka-mqtt using Paho).
         */
        LEGACY_MODE("legacy-mode", false);

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
