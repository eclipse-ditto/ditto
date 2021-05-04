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
package org.eclipse.ditto.gateway.service.proxy.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * The implementation of statistics-shard-config.
 */
@Immutable
final class DefaultStatisticsShardConfig implements StatisticsShardConfig, WithConfigPath {

    private static final String CONFIG_PATH = "shards";

    private final String shard;
    private final String role;
    private final String root;

    // for test
    DefaultStatisticsShardConfig(final String shard, final String role, final String root) {
        this.shard = shard;
        this.role = role;
        this.root = root;
    }

    private DefaultStatisticsShardConfig(final ScopedConfig scopedConfig) {
        shard = scopedConfig.getString(ConfigValues.REGION.getConfigPath());
        role = scopedConfig.getString(ConfigValues.ROLE.getConfigPath());
        root = scopedConfig.getString(ConfigValues.ROOT.getConfigPath());
    }

    /**
     * Returns an instance of {@code StatisticsShardConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the authentication config at root.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static DefaultStatisticsShardConfig of(final Config config) {
        return new DefaultStatisticsShardConfig(
                ConfigWithFallback.newInstance(config.atKey(CONFIG_PATH), CONFIG_PATH, ConfigValues.values()));
    }

    @Override
    public String getRegion() {
        return shard;
    }

    @Override
    public String getRoot() {
        return root;
    }

    @Override
    public String getRole() {
        return role;
    }

    /**
     * @return always {@value CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultStatisticsShardConfig that = (DefaultStatisticsShardConfig) o;
        return shard.equals(that.shard) &&
                role.equals(that.role) &&
                root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shard, role, root);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "shard=" + shard +
                ", role=" + role +
                ", root=" + root +
                "]";
    }

}
