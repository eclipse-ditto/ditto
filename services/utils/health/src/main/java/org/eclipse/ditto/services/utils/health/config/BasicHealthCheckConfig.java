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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Ditto's runtime health checking behaviour.
 * <p>
 * Java serialization is supported for {@code BaseHealthCheckConfig}.
 * </p>
 */
@Immutable
public interface BasicHealthCheckConfig {

    /**
     * Indicates whether global health checking should be enabled.
     *
     * @return {@code true} if health checking should be enabled, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the interval of health check.
     *
     * @return the interval.
     * @see #isEnabled()
     */
    Duration getInterval();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code BaseHealthCheckConfig}.
     */
    enum HealthCheckConfigValue implements KnownConfigValue {

        /**
         * Determines whether global health checking should be enabled.
         */
        ENABLED("enabled", true),

        /**
         * The interval of health check.
         */
        INTERVAL("interval", Duration.ofMinutes(1L));

        private final String path;
        private final Object defaultValue;

        private HealthCheckConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
