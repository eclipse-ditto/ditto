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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.time.Duration;

import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.utils.cache.config.CacheConfigReader;
import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration of search updater stream.
 */
public final class SearchUpdaterStreamConfigReader extends AbstractConfigReader {

    private SearchUpdaterStreamConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a search updater stream config reader from the Akka system config.
     *
     * @param akkaSystemConfig the Akka system config.
     * @return the search updater stream config reader.
     */
    public static SearchUpdaterStreamConfigReader of(final Config akkaSystemConfig) {
        return new SearchUpdaterStreamConfigReader(akkaSystemConfig.getConfig(ConfigKeys.UPDATER_STREAM));
    }

    /**
     * @return maximum length of an array for it to be indexed.
     */
    public int maxArraySize() {
        return config.getInt("max-array-size");
    }

    /**
     * @return the configured minimum delay between bulk writes.
     */
    public Duration writeInterval() {
        return config.getDuration("write-interval");
    }

    /**
     * @return the configured ask-timeout.
     */
    public Duration askTimeout() {
        return config.getDuration("ask-timeout");
    }

    /**
     * @return how long to wait before retrying a cache query if the cached value is out of date.
     */
    public Duration cacheRetryDelay() {
        return config.getDuration("cache.retry-delay");
    }

    /**
     * @return name of the dispatcher to run the cache with.
     */
    public String cacheDispatcher() {
        return config.getString("cache.dispatcher");
    }

    /**
     * @return the cache configuration.
     */
    public CacheConfigReader cache() {
        return CacheConfigReader.newInstance(config.getConfig("cache"));
    }

    /**
     * @return the stream stage configuration for retrieving things and policy-enforcers.
     */
    public Retrieval retrieval() {
        return new Retrieval(config.getConfig("retrieval"));
    }

    /**
     * @return the stream stage configuration for writing into the persistence.
     */
    public Persistence persistence() {
        return new Persistence(config.getConfig("persistence"));
    }

    /**
     * Shared settings of stream stages.
     */
    public static abstract class AbstractStreamStage extends AbstractConfigReader {

        AbstractStreamStage(final Config config) {
            super(config);
        }

        /**
         * @return how many stream elements to process in parallel.
         */
        public final int parallelism() {
            return config.getInt("parallelism");
        }

        /**
         * @return minimum backoff before restart in case of error.
         */
        public final Duration minBackoff() {
            return config.getDuration("min-backoff");
        }

        /**
         * @return maximum backoff before restart in case of error.
         */
        public final Duration maxBackoff() {
            return config.getDuration("max-backoff");
        }

        /**
         * @return random factor in backoff calculation.
         */
        public double randomFactor() {
            return config.getDouble("random-factor");
        }
    }

    /**
     * Stream stage configuration for retrieval of things and policy-enforcers.
     */
    public static final class Retrieval extends AbstractStreamStage {

        Retrieval(final Config config) {
            super(config);
        }
    }

    /**
     * Stream stage configuration for writing into the persistence.
     */
    public static final class Persistence extends AbstractStreamStage {

        Persistence(final Config config) {
            super(config);
        }

        /**
         * @return how many write operations to perform in one bulk.
         */
        public int maxBulkSize() {
            return config.getInt("max-bulk-size");
        }

        /**
         * @return delay between writes to the persistence.
         */
        public Duration writeInterval() {
            return config.getDuration("write-interval");
        }
    }
}
