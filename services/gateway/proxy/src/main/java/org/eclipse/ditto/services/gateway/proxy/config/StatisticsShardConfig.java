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
package org.eclipse.ditto.services.gateway.proxy.config;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Configuration for statistics of shard regions.
 */
public interface StatisticsShardConfig {

    /**
     * Returns the shard region name.
     *
     * @return the shard region name.
     */
    String getShard();

    /**
     * Returns the cluster role where the shard is started.
     *
     * @return the cluster role.
     */
    String getRole();

    /**
     * Returns the actor path of the actor who supervises the shard.
     *
     * @return actor path of the supervisor.
     */
    String getRoot();

    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum ConfigValues implements KnownConfigValue {

        /**
         * The shard region name.
         */
        SHARD("shard", ""),

        /**
         * The cluster role of the shard.
         */
        ROLE("role", ""),

        /**
         * The actor path of the shard's supervisor.
         */
        ROOT("root", "");

        private final String path;
        private final Object defaultValue;

        ConfigValues(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
