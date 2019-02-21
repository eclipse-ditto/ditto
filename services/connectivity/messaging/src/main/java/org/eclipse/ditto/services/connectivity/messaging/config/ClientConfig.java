/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's client.
 * <p>
 * Java serialization is supported for {@code ClientConfig}.
 * </p>
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
        INIT_TIMEOUT("init-timeout", "5s");

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
