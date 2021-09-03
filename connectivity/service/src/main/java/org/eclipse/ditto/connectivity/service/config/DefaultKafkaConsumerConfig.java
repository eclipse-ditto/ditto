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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultExponentialBackOffConfig;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link KafkaConsumerConfig}.
 */
@Immutable
final class DefaultKafkaConsumerConfig implements KafkaConsumerConfig {

    private static final String CONFIG_PATH = "consumer";
    private static final String ALPAKKA_PATH = "alpakka";
    private static final String RESTART_PATH = "restart";

    private final ConnectionThrottlingConfig throttlingConfig;
    private final ExponentialBackOffConfig consumerRestartBackOffConfig;
    private final Config alpakkaConfig;

    private DefaultKafkaConsumerConfig(final Config kafkaConsumerScopedConfig) {
        throttlingConfig = ConnectionThrottlingConfig.of(kafkaConsumerScopedConfig);
        consumerRestartBackOffConfig =
                DefaultExponentialBackOffConfig.of(getConfigOrEmpty(kafkaConsumerScopedConfig, RESTART_PATH));
        alpakkaConfig = getConfigOrEmpty(kafkaConsumerScopedConfig, ALPAKKA_PATH);
    }

    /**
     * Returns an instance of {@code DefaultKafkaConsumerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultKafkaConsumerConfig of(final Config config) {
        return new DefaultKafkaConsumerConfig(getConfigOrEmpty(config, CONFIG_PATH));
    }

    private static Config getConfigOrEmpty(final Config config, final String configKey) {
        return config.hasPath(configKey) ? config.getConfig(configKey) : ConfigFactory.empty();
    }

    @Override
    public ConnectionThrottlingConfig getThrottlingConfig() {
        return throttlingConfig;
    }

    @Override
    public ExponentialBackOffConfig getConsumerRestartBackOffConfig() {
        return consumerRestartBackOffConfig;
    }

    @Override
    public Config getAlpakkaConfig() {
        return alpakkaConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultKafkaConsumerConfig that = (DefaultKafkaConsumerConfig) o;
        return Objects.equals(throttlingConfig, that.throttlingConfig) &&
                Objects.equals(alpakkaConfig, that.alpakkaConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(throttlingConfig, alpakkaConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "throttlingConfig=" + throttlingConfig +
                ", alpakkaConfig=" + alpakkaConfig +
                "]";
    }

}
