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

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the Kafka consumer.
 */
@Immutable
public interface KafkaConsumerConfig {

    /**
     * Returns the throttling config.
     *
     * @return the config.
     */
    ConnectionThrottlingConfig getThrottlingConfig();

    /**
     * Returns the config to configure the backOff for restarting the qos 1 consumer stream after a failed
     * acknowledgement.
     *
     * @return the config.
     */
    ExponentialBackOffConfig getRestartBackOffConfig();

    /**
     * Returns the Config for consumers needed by the Kafka client.
     *
     * @return consumer configuration needed by the Kafka client.
     */
    Config getAlpakkaConfig();

    /**
     * Returns the interval in which metrics from the Apache Kafka client should be collected.
     *
     * @return the interval.
     */
    Duration getMetricCollectingInterval();

    /**
     * @return timeout before the consumer is initialized and considered "ready".
     */
    long getInitTimeoutSeconds();

    /**
     * Returns an instance of {@code KafkaConsumerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static KafkaConsumerConfig of(final Config config) {
        return DefaultKafkaConsumerConfig.of(config);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code KafkaConsumerConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The interval in which Apache Kafka client metrics should be collected.
         */
        METRIC_COLLECTING_INTERVAL("metric-collecting-interval", Duration.ofSeconds(10L)),

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
