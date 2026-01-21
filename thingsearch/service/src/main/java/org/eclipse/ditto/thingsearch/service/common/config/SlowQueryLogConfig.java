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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for slow query logging.
 */
@Immutable
public interface SlowQueryLogConfig {

    /**
     * Returns whether slow query logging is enabled.
     *
     * @return true if enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Returns the threshold duration above which queries are considered slow and will be logged.
     *
     * @return the threshold duration.
     */
    Duration getThreshold();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * SlowQueryLogConfig.
     */
    enum SlowQueryLogConfigValue implements KnownConfigValue {

        /**
         * Whether slow query logging is enabled.
         */
        ENABLED("enabled", true),

        /**
         * The threshold duration above which queries are considered slow.
         */
        THRESHOLD("threshold", Duration.ofSeconds(1));

        private final String path;
        private final Object defaultValue;

        SlowQueryLogConfigValue(final String thePath, final Object theDefaultValue) {
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
