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
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link KafkaProducerConfig}.
 */
@Immutable
public final class DefaultKafkaProducerConfig implements KafkaProducerConfig {

    private static final String CONFIG_PATH = "producer";
    private static final String ALPAKKA_PATH = "alpakka";

    private final int queueSize;
    private final int parallelism;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final double randomFactor;
    private final Config alpakkaConfig;

    private DefaultKafkaProducerConfig(final ScopedConfig kafkaProducerScopedConfig) {
        queueSize = kafkaProducerScopedConfig.getGreaterZeroIntOrThrow(ConfigValue.QUEUE_SIZE);
        parallelism = kafkaProducerScopedConfig.getGreaterZeroIntOrThrow(ConfigValue.PARALLELISM);
        minBackoff = kafkaProducerScopedConfig.getNonNegativeDurationOrThrow(ConfigValue.MIN_BACKOFF);
        maxBackoff = kafkaProducerScopedConfig.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.MAX_BACKOFF);
        randomFactor = kafkaProducerScopedConfig.getGreaterZeroDoubleOrThrow(ConfigValue.RANDOM_FACTOR);
        alpakkaConfig = kafkaProducerScopedConfig.getConfig(ALPAKKA_PATH);
    }

    /**
     * Returns an instance of {@code DefaultKafkaProducerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultKafkaProducerConfig of(final Config config) {
        return new DefaultKafkaProducerConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public int getQueueSize() {
        return queueSize;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public Duration getMinBackoff() {
        return minBackoff;
    }

    @Override
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    @Override
    public double getRandomFactor() {
        return randomFactor;
    }

    @Override
    public Config getAlpakkaConfig() {
        return alpakkaConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultKafkaProducerConfig that = (DefaultKafkaProducerConfig) o;
        return Objects.equals(queueSize, that.queueSize) &&
                Objects.equals(parallelism, that.parallelism) &&
                Objects.equals(minBackoff, that.minBackoff) &&
                Objects.equals(maxBackoff, that.maxBackoff) &&
                Objects.equals(randomFactor, that.randomFactor) &&
                Objects.equals(alpakkaConfig, that.alpakkaConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueSize, parallelism, minBackoff, maxBackoff, randomFactor, alpakkaConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "queueSize=" + queueSize +
                ", parallelism=" + parallelism +
                ", minBackoff=" + minBackoff +
                ", maxBackoff=" + maxBackoff +
                ", randomFactor=" + randomFactor +
                ", alpakkaConfig=" + alpakkaConfig +
                "]";
    }

}
