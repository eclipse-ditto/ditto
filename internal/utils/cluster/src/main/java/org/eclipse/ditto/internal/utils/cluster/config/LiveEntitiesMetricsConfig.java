/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the live entities metrics feature.
 * This feature periodically publishes Prometheus gauge metrics with the count of live/active entities
 * per namespace in each shard region.
 */
@Immutable
public interface LiveEntitiesMetricsConfig {

    /**
     * Returns whether the live entities metrics feature is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    boolean isEnabled();

    /**
     * Returns the interval at which the live entities metrics are refreshed.
     *
     * @return the refresh interval.
     */
    Duration getRefreshInterval();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LiveEntitiesMetricsConfig}.
     */
    enum LiveEntitiesMetricsConfigValue implements KnownConfigValue {

        /**
         * Whether live entities metrics are enabled.
         */
        ENABLED("enabled", true),

        /**
         * The interval at which live entities metrics are refreshed.
         */
        REFRESH_INTERVAL("refresh-interval", Duration.ofSeconds(30));

        private final String path;
        private final Object defaultValue;

        LiveEntitiesMetricsConfigValue(final String thePath, final Object theDefaultValue) {
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
