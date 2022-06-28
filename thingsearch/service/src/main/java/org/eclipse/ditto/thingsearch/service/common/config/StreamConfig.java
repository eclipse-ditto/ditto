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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides streaming configuration settings of the Search service.
 */
@Immutable
public interface StreamConfig {

    /**
     * Returns the maximum length of an array for it to be indexed.
     *
     * @return the length.
     */
    int getMaxArraySize();

    /**
     * Returns the minimal delay between event dumps.
     *
     * @return the interval.
     */
    Duration getWriteInterval();

    /**
     * @return the duration before the update actor is stopped after receiving a ThingDeleted event
     */
    Duration getThingDeletionTimeout();

    /**
     * Returns the configuration for the used "ask with retry" pattern in the search updater for retrieval of things and
     * policies.
     *
     * @return the "ask with retry" pattern config for retrieval of things and policies.
     */
    AskWithRetryConfig getAskWithRetryConfig();

    /**
     * Returns the configuration settings for the retrieval of things and policy-enforcers.
     *
     * @return the config.
     */
    StreamStageConfig getRetrievalConfig();

    /**
     * Returns the configuration settings for writing into the persistence.
     *
     * @return the config.
     */
    PersistenceStreamConfig getPersistenceConfig();

    /**
     * Returns the configuration settings of the policy enforcers to cache.
     *
     * @return the config.
     */
    StreamCacheConfig getPolicyCacheConfig();

    /**
     * Returns the configuration settings of the things to cache.
     *
     * @return the config.
     */
    StreamCacheConfig getThingCacheConfig();

    /**
     * An enumeration of known config path expressions and their associated default values for {@code StreamConfig}.
     */
    enum StreamConfigValue implements KnownConfigValue {

        /**
         * The maximum length of an array for it to be indexed.
         */
        MAX_ARRAY_SIZE("max-array-size", 25),

        /**
         * The minimal delay between event dumps.
         */
        WRITE_INTERVAL("write-interval", Duration.ofSeconds(1L)),

        /**
         * The delay before the updater actor is stopped after receiving a ThingDeleted event.
         */
        THING_DELETION_TIMEOUT("thing-deletion-timeout", Duration.ofMinutes(5));

        private final String configPath;
        private final Object defaultValue;

        StreamConfigValue(final String configPath, final Object defaultValue) {
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
