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
package org.eclipse.ditto.services.thingsearch.updater.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for the search updating functionality.
 * <p>
 * Java serialization is supported for UpdaterConfig.
 * </p>
 */
@Immutable
public interface UpdaterConfig {

    /**
     * Returns the lifetime of an idling ThingUpdater.
     *
     * @return the max idle time.
     */
    Duration getMaxIdleTime();

    /**
     * Returns the amount of write operations to perform in one bulk.
     *
     * @return the max bulk size.
     */
    int getMaxBulkSize();

    /**
     * Returns the interval which defines how long a thing updater is considered active.
     * If not active, the corresponding actor can be stopped.
     *
     * @return the interval.
     */
    Duration getActivityCheckInterval();

    /**
     * Indicates whether event processing should be active.
     *
     * @return {@code true} if event processing should be active, {@code false} else.
     */
    boolean isEventProcessingActive();

    /**
     * Returns the synchronization settings for the Things service.
     *
     * @return the config.
     */
    SyncConfig getThingsSyncConfig();

    /**
     * Returns the synchronization settings for the Policies service.
     *
     * @return the config.
     */
    SyncConfig getPoliciesSyncConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * UpdaterConfig.
     */
    enum UpdaterConfigValue implements KnownConfigValue {

        /**
         * Determines the lifetime of an idling ThingUpdater.
         */
        MAX_IDLE_TIME("max-idle-time", Duration.ofHours(25L)),

        /**
         * Determines how many write operations to perform in one bulk.
         */
        MAX_BULK_SIZE("max-bulk-size", Integer.MAX_VALUE),

        /**
         * Determines the interval which defines how long a thing updater is considered active.
         * If not active, the corresponding actor can be stopped.
         */
        ACTIVITY_CHECK_INTERVAL("activity-check-interval", Duration.ofMinutes(1L)),

        /**
         * Determines whether event processing should be active.
         */
        EVENT_PROCESSING_ACTIVE("event-processing-active", true);

        private final String path;
        private final Object defaultValue;

        private UpdaterConfigValue(final String thePath, final Object theDefaultValue) {
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
