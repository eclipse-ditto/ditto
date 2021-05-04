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

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the Ditto cluster.
 */
@Immutable
public interface ClusterConfig {

    /**
     * Returns the number of shards in a cluster.
     *
     * @return the number of shards.
     */
    int getNumberOfShards();

    /**
     * Returns the cluster roles which should not be included when determining cluster status/health.
     *
     * @return the cluster roles blocklist.
     */
    Collection<String> getClusterStatusRolesBlocklist();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ClusterConfig}.
     */
    enum ClusterConfigValue implements KnownConfigValue {

        /**
         * The number of shards in a cluster.
         */
        NUMBER_OF_SHARDS("number-of-shards", 30),

        /**
         * The cluster roles which should not be included when determining cluster status/health.
         */
        CLUSTER_STATUS_ROLES_BLOCKLIST("cluster-status-roles-blocklist", Arrays.asList(
                "dc-default",
                "blocked-namespaces-aware"
        ));

        private final String path;
        private final Object defaultValue;

        ClusterConfigValue(final String thePath, final Object theDefaultValue) {
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

}
