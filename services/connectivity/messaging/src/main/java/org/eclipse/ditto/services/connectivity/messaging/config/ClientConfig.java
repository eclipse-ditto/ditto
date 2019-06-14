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

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's client.
 */
@Immutable
public interface ClientConfig {

    /**
     * Returns the duration after the init process is triggered (in case no connect command was received by the
     * client actor).
     *
     * @return the init timeout.
     */
    Duration getInitTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code ClientConfig}.
     */
    enum ClientConfigValue implements KnownConfigValue {

        /**
         * The duration after the init process is triggered.
         */
        INIT_TIMEOUT("init-timeout", Duration.ofSeconds(5L));

        private final String path;
        private final Object defaultValue;

        private ClientConfigValue(final String thePath, final Object theDefaultValue) {
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
