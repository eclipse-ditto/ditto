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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link KafkaCommitterConfig}.
 */
@Immutable
final class DefaultKafkaCommitterConfig implements KafkaCommitterConfig {

    private static final String CONFIG_PATH = "committer";
    private static final String ALPAKKA_PATH = "alpakka";

    private final Config alpakkaConfig;

    private DefaultKafkaCommitterConfig(final Config kafkaCommitterScopedConfig) {
        alpakkaConfig = getConfigOrEmpty(kafkaCommitterScopedConfig, ALPAKKA_PATH);
    }

    /**
     * Returns an instance of {@code DefaultKafkaCommitterConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultKafkaCommitterConfig of(final Config config) {
        return new DefaultKafkaCommitterConfig(getConfigOrEmpty(config, CONFIG_PATH));
    }

    private static Config getConfigOrEmpty(final Config config, final String configKey) {
        return config.hasPath(configKey) ? config.getConfig(configKey) : ConfigFactory.empty();
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
        final DefaultKafkaCommitterConfig that = (DefaultKafkaCommitterConfig) o;
        return Objects.equals(alpakkaConfig, that.alpakkaConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alpakkaConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "alpakkaConfig=" + alpakkaConfig +
                "]";
    }

}
