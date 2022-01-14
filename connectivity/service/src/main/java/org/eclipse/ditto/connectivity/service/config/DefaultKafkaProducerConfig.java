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

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link KafkaProducerConfig}.
 */
@Immutable
final class DefaultKafkaProducerConfig implements KafkaProducerConfig {

    private static final String CONFIG_PATH = "producer";
    private static final String ALPAKKA_PATH = "alpakka";

    private final int queueSize;
    private final int parallelism;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final double randomFactor;
    private final int maxRestartsCount;
    private final Duration maxRestartsWithin;
    private final Config alpakkaConfig;
    private final long initTimeoutSeconds;

    private DefaultKafkaProducerConfig(final Config kafkaProducerScopedConfig) {
        queueSize = kafkaProducerScopedConfig.getInt(ConfigValue.QUEUE_SIZE.getConfigPath());
        parallelism = kafkaProducerScopedConfig.getInt(ConfigValue.PARALLELISM.getConfigPath());
        minBackoff = kafkaProducerScopedConfig.getDuration(ConfigValue.MIN_BACKOFF.getConfigPath());
        maxBackoff = kafkaProducerScopedConfig.getDuration(ConfigValue.MAX_BACKOFF.getConfigPath());
        randomFactor = kafkaProducerScopedConfig.getDouble(ConfigValue.RANDOM_FACTOR.getConfigPath());
        maxRestartsCount = kafkaProducerScopedConfig.getInt(ConfigValue.MAX_RESTARTS_COUNT.getConfigPath());
        maxRestartsWithin = kafkaProducerScopedConfig.getDuration(ConfigValue.MAX_RESTARTS_WITHIN.getConfigPath());
        alpakkaConfig = kafkaProducerScopedConfig.getConfig(ALPAKKA_PATH);
        initTimeoutSeconds = kafkaProducerScopedConfig.getLong(ConfigValue.INIT_TIMEOUT_SECONDS.getConfigPath());
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
    public int getMaxRestartsCount() {
        return maxRestartsCount;
    }

    @Override
    public Duration getMaxRestartsWithin() {
        return maxRestartsWithin;
    }

    @Override
    public Config getAlpakkaConfig() {
        return alpakkaConfig;
    }

    @Override
    public long getInitTimeoutSeconds() {
        return initTimeoutSeconds;
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
                Objects.equals(maxRestartsCount, that.maxRestartsCount) &&
                Objects.equals(maxRestartsWithin, that.maxRestartsWithin) &&
                Objects.equals(alpakkaConfig, that.alpakkaConfig) &&
                Objects.equals(initTimeoutSeconds, that.initTimeoutSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueSize, parallelism, minBackoff, maxBackoff, maxRestartsCount, maxRestartsWithin,
                randomFactor, alpakkaConfig, initTimeoutSeconds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "queueSize=" + queueSize +
                ", parallelism=" + parallelism +
                ", minBackoff=" + minBackoff +
                ", maxBackoff=" + maxBackoff +
                ", randomFactor=" + randomFactor +
                ", maxRestartsCount=" + maxRestartsCount +
                ", maxRestartsWithin=" + maxRestartsWithin +
                ", alpakkaConfig=" + alpakkaConfig +
                ", initTimeoutSeconds=" + initTimeoutSeconds +
                "]";
    }

}
