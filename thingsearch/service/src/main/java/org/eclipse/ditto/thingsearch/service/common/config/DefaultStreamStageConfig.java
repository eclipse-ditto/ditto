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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultExponentialBackOffConfig;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link StreamStageConfig}.
 */
@Immutable
public final class DefaultStreamStageConfig implements StreamStageConfig {

    private final int parallelism;
    private final DefaultExponentialBackOffConfig exponentialBackOffConfig;

    private DefaultStreamStageConfig(final ConfigWithFallback streamStageScopedConfig,
            final DefaultExponentialBackOffConfig exponentialBackOffConfig) {

        parallelism = streamStageScopedConfig.getInt(StreamStageConfigValue.PARALLELISM.getConfigPath());
        this.exponentialBackOffConfig = exponentialBackOffConfig;
    }

    /**
     * Returns an instance of DefaultStreamStageConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the stream stage config at {@code configPath}.
     * @param configPath the supposed path of the nested stream stage config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultStreamStageConfig getInstance(final Config config, final String configPath) {
        final var configWithFallback =
                ConfigWithFallback.newInstance(config, configPath, StreamStageConfigValue.values());
        return new DefaultStreamStageConfig(configWithFallback, DefaultExponentialBackOffConfig.of(configWithFallback));
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return exponentialBackOffConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultStreamStageConfig that = (DefaultStreamStageConfig) o;
        return parallelism == that.parallelism &&
                exponentialBackOffConfig.equals(that.exponentialBackOffConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parallelism, exponentialBackOffConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "parallelism=" + parallelism +
                ", exponentialBackOffConfig=" + exponentialBackOffConfig +
                "]";
    }

}
