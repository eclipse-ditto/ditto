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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for the physical deletion of thing entities that are marked as
 * {@code "__deleted"}.
 */
@Immutable
public interface DeletionConfig {

    /**
     * Indicates whether physical deletion of marked thing entities is enabled.
     *
     * @return {@code true} if deletion is enabled, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the amount of time after which thing entities should be deleted.
     * I. e. thing entities that are marked as deleted longer than the returned duration are subject for physical
     * deletion.
     *
     * @return the deletion age.
     */
    Duration getDeletionAge();

    /**
     * Returns the interval of physical deletion.
     *
     * @return the interval.
     */
    Duration getRunInterval();

    /**
     * Returns the hour of the first physical deletion run.
     *
     * @return the first interval hour (UTC).
     */
    int getFirstIntervalHour();

    /**
     * An enumeration of the known config path expressions and their associated default values for DeletionConfig.
     */
    enum DeletionConfigValue implements KnownConfigValue {

        /**
         * Determines whether physical deletion of marked thing entities is enabled.
         */
        ENABLED("enabled", true),

        /**
         * Determines the amount of time after which thing entities should be deleted.
         */
        DELETION_AGE("deletion-age", Duration.ofDays(3L)),

        /**
         * Determines the interval of physical deletion.
         */
        RUN_INTERVAL("run-interval", Duration.ofHours(24L)),

        /**
         * The hour of the first physical deletion run.
         */
        FIRST_INTERVAL_HOUR("first-interval-hour", 21);

        private final String configPath;
        private final Object defaultValue;

        private DeletionConfigValue(final String configPath, final Object defaultValue) {
            this.configPath = configPath;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return configPath;
        }

    }

}
