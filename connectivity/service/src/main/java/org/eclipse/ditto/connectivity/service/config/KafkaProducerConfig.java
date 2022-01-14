/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the Kafka producer.
 */
@Immutable
public interface KafkaProducerConfig {

    /**
     * @return number of maximum buffered messages before dropping them.
     */
    int getQueueSize();

    /**
     * @return number of maximum parallel message publications.
     */
    int getParallelism();

    /**
     * @return minimum duration before restarting the producer stream after a failure.
     */
    Duration getMinBackoff();

    /**
     * @return maximum duration before restarting the producer stream after a failure.
     */
    Duration getMaxBackoff();

    /**
     * @return random value to vary the restart interval.
     */
    double getRandomFactor();

    /**
     * @return maximum restarts count before failing the producer stream.
     */
    int getMaxRestartsCount();

    /**
     * @return duration during within maximum restarts may happen before failing the producer stream.
     */
    Duration getMaxRestartsWithin();

    /**
     * Returns the Config for producers needed by the Kafka client.
     *
     * @return consumer configuration needed by the Kafka client.
     */
    Config getAlpakkaConfig();

    /**
     * @return timeout before the producer is initialized and considered "ready".
     */
    long getInitTimeoutSeconds();

    /**
     * Returns an instance of {@code KafkaProducerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static KafkaProducerConfig of(final Config config) {
        return DefaultKafkaProducerConfig.of(config);
    }

    enum ConfigValue implements KnownConfigValue {

        QUEUE_SIZE("queue-size", 1000),

        PARALLELISM("parallelism", 10),

        MIN_BACKOFF("min-backoff", Duration.ofSeconds(3)),

        MAX_BACKOFF("max-backoff", Duration.ofSeconds(30)),

        RANDOM_FACTOR("random-factor", 0.2),

        MAX_RESTARTS_COUNT("max-restarts-count", 5),

        MAX_RESTARTS_WITHIN("max-restarts-within", Duration.ofMinutes(5)),

        INIT_TIMEOUT_SECONDS("init-timeout-seconds", 3);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
