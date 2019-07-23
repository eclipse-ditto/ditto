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

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

import akka.actor.ActorContext;

/**
 * Provides configuration settings for the statistics actor.
 */
@Immutable
public interface StatisticsConfig {

    /**
     * Returns the configuration settings of shards for which statistics are reported..
     *
     * @return the config.
     */
    List<StatisticsShardConfig> getShards();

    /**
     * Returns an instance of {@code StatisticsConfig} based on the settings of the actor system.
     *
     * @param context any actor context.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static StatisticsConfig forActor(final ActorContext context) {
        final Config systemConfig = context.system().settings().config();
        final String configPath = String.format("%s.%s", ScopedConfig.DITTO_SCOPE, "gateway");
        return DefaultStatisticsConfig.of(systemConfig.getConfig(configPath));
    }

    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum ConfigValues implements KnownConfigValue {

        /**
         * Configuration for individual shards.
         */
        SHARDS("shards", Collections.emptyList());

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
