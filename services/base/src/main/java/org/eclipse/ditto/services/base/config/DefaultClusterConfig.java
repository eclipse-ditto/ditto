/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link ClusterConfig}.
 */
public final class DefaultClusterConfig implements ClusterConfig, Serializable {

    private static final String CONFIG_PATH = "cluster";

    private static final long serialVersionUID = 5940121378914735219L;

    private final int numberOfShards;

    private DefaultClusterConfig(final ScopedConfig config) {
        numberOfShards = config.getInt(ClusterConfigValue.NUMBER_OF_SHARDS.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultClusterConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cluster config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultClusterConfig of(final Config config) {
        return new DefaultClusterConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ClusterConfigValue.values()));
    }

    @Override
    public int getNumberOfShards() {
        return numberOfShards;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClusterConfig that = (DefaultClusterConfig) o;
        return numberOfShards == that.numberOfShards;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "numberOfShards=" + numberOfShards +
                "]";
    }

}
