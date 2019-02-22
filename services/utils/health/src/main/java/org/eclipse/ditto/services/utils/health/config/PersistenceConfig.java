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
package org.eclipse.ditto.services.utils.health.config;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of health check persistence.
 * <p>
 * Java serialization is supported for {@code PersistenceConfig}.
 * </p>
 */
public interface PersistenceConfig {

    /**
     * Indicates whether the persistence health check should be enabled.
     *
     * @return {@code true} if the persistence health check should be enabled, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the timeout of the health check for persistence.
     * If the persistence takes longer than that to respond, it is considered "DOWN".
     *
     * @return the timeout of the health check for persistence.
     * @see #isEnabled()
     */
    Duration getTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code PersistenceConfig}.
     */
    enum PersistenceConfigValue implements KnownConfigValue {

        /**
         * Determines whether the persistence health check should be enabled.
         */
        ENABLED("enabled", false),

        /**
         * The timeout of the health check for persistence.
         */
        TIMEOUT("timeout", Duration.ofMinutes(1));

        private final String path;
        private final Object defaultValue;

        private PersistenceConfigValue(final String thePath, final Object theDefaultValue) {
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
