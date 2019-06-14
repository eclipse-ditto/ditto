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
     * Returns the timeout for messages to Things shard.
     *
     * @return the timeout.
     */
    Duration getAskTimeout();

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
     * Returns the configuration settings of the stream cache.
     *
     * @return the config.
     */
    StreamCacheConfig getCacheConfig();

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
         * The timeout for messages to Things shard.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(30L));

        private final String configPath;
        private final Object defaultValue;

        private StreamConfigValue(final String configPath, final Object defaultValue) {
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
