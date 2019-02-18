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

import java.util.Objects;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.ClusterConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link ClusterConfig}.
 */
public final class DefaultClusterConfig implements ClusterConfig {

    private enum ClusterConfigValue implements KnownConfigValue {

        NUMBER_OF_SHARDS("number-of-shards", 30);

        private final String path;
        private final Object defaultValue;

        private ClusterConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    private static final String CONFIG_PATH = "cluster";

    private final Config config;

    private DefaultClusterConfig(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Returns an instance of {@code DefaultClusterConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cluster config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is {@code null} if the
     * value of {@code config} at {@code configPath} is not of type
     * {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultClusterConfig of(final Config config) {
        return new DefaultClusterConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ClusterConfigValue.values()));
    }

    @Override
    public int getNumberOfShards() {
        return config.getInt(ClusterConfigValue.NUMBER_OF_SHARDS.getConfigPath());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClusterConfig that = (DefaultClusterConfig) o;
        return config.equals(that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                "]";
    }

}
