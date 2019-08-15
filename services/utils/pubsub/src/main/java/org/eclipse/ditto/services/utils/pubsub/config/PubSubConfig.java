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
package org.eclipse.ditto.services.utils.pubsub.config;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Configuration for distributed data update.
 */
public interface PubSubConfig {

    /**
     * @return Seed of hash functions shared across the cluster.
     */
    String getSeed();

    /**
     * @return How many hash functions to use for the Bloom filters.
     * Expected false positive rate is {@code 1/2^this_number}.
     * To limit the amount of wasted bandwidth, this number should
     * be proportional to the logarithm of cluster size for huge clusters.
     */
    int getHashFamilySize();

    /**
     * @return How long to wait to restart pub-sub if a child actor crashes.
     */
    Duration getRestartDelay();

    /**
     * @return How often to update distributed data.
     */
    Duration getUpdateInterval();

    /**
     * @return Probability of forcing an update on each clock tick to recover from
     * temporary disassociation.
     */
    double getForceUpdateProbability();

    /**
     * @return Ratio of reserved Bloom filter elements to current number of topics.
     */
    double getBufferFactor();

    static PubSubConfig of(final Config config) {
        return DefaultPubSubConfig.of(config);
    }


    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Seed to initialize a family of hash functions.
         * Must be identical on all cluster members for pub-sub to work.
         * Rotate when paranoid about collision attacks.
         */
        SEED("seed", "Lorem ipsum dolor sit amet, conectetur adipiscing elit, " +
                "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."),

        /**
         * How many hash functions to use for the Bloom filters.
         * Expected false positive rate is {@code 1/2^this_number}.
         * Bloom filter size is proportional to this number.
         */
        HASH_FAMILY_SIZE("hash-family-size", 10),

        /**
         * How long to wait before restarting actors executing pub-sub.
         */
        RESTART_DELAY("restart-delay", Duration.ofSeconds(10L)),

        /**
         * How often to flush local subscriptions to the distributed data replicator.
         */
        UPDATE_INTERVAL("update-interval", Duration.ofSeconds(3L)),

        /**
         * Probability to flush local subscriptions when there was no change to recover
         * from temporary disassociation, during which a remove member may remove our subscriber
         * from the distributed data when prompted by a dead letter.
         */
        FORCE_UPDATE_PROBABILITY("force-update-probability", 0.01),

        /**
         * How much empty space to reserve in the Bloom filters.
         * 1.0 = optimal size for the present number of subscriptions.
         */
        BUFFER_FACTOR("buffer-factor", 1.0);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
