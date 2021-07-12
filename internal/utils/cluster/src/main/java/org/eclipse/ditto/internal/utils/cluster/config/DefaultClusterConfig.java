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
package org.eclipse.ditto.internal.utils.cluster.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link ClusterConfig}.
 */
public final class DefaultClusterConfig implements ClusterConfig {

    private static final String CONFIG_PATH = "cluster";

    private final int numberOfShards;
    private final List<String> clusterStatusRolesBlocklist;

    private DefaultClusterConfig(final ConfigWithFallback config) {
        numberOfShards = config.getPositiveIntOrThrow(ClusterConfigValue.NUMBER_OF_SHARDS);
        clusterStatusRolesBlocklist = Collections.unmodifiableList(
                new ArrayList<>(
                        config.getStringList(ClusterConfigValue.CLUSTER_STATUS_ROLES_BLOCKLIST.getConfigPath())));
    }

    /**
     * Returns an instance of {@code DefaultClusterConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cluster config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public Collection<String> getClusterStatusRolesBlocklist() {
        return clusterStatusRolesBlocklist;
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
        return numberOfShards == that.numberOfShards &&
                Objects.equals(clusterStatusRolesBlocklist, that.clusterStatusRolesBlocklist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards, clusterStatusRolesBlocklist);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "numberOfShards=" + numberOfShards +
                ", clusterStatusRolesBlocklist=" + clusterStatusRolesBlocklist +
                "]";
    }

}
