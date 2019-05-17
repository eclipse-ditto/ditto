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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link PersistenceStreamConfig}.
 */
@Immutable
public final class DefaultPersistenceStreamConfig implements PersistenceStreamConfig, Serializable {

    private static final String CONFIG_PATH = "persistence";

    private static final long serialVersionUID = 49154106468833606L;

    private final int maxBulkSize;
    private final DefaultStreamStageConfig defaultStreamStageConfig;

    private DefaultPersistenceStreamConfig(final ConfigWithFallback persistenceStreamScopedConfig,
            final DefaultStreamStageConfig defaultStreamStageConfig) {

        maxBulkSize = persistenceStreamScopedConfig.getInt(PersistenceStreamConfigValue.MAX_BULK_SIZE.getConfigPath());
        this.defaultStreamStageConfig = defaultStreamStageConfig;
    }

    /**
     * Returns an instance of DefaultPersistenceStreamConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the persistence stream config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPersistenceStreamConfig of(final Config config) {
        return new DefaultPersistenceStreamConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PersistenceStreamConfigValue.values()),
                DefaultStreamStageConfig.getInstance(config, CONFIG_PATH));
    }

    @Override
    public int getMaxBulkSize() {
        return maxBulkSize;
    }

    @Override
    public int getParallelism() {
        return defaultStreamStageConfig.getParallelism();
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return defaultStreamStageConfig.getExponentialBackOffConfig();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPersistenceStreamConfig that = (DefaultPersistenceStreamConfig) o;
        return maxBulkSize == that.maxBulkSize &&
                defaultStreamStageConfig.equals(that.defaultStreamStageConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxBulkSize, defaultStreamStageConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxBulkSize=" + maxBulkSize +
                ", defaultStreamStageConfig=" + defaultStreamStageConfig +
                "]";
    }

}
