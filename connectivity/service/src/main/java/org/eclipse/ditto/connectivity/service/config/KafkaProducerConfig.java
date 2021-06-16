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
     * Returns the Config for producers needed by the Kafka client.
     *
     * @return consumer configuration needed by the Kafka client.
     */
    Config getAlpakkaConfig();

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

}
