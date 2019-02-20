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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link org.eclipse.ditto.services.concierge.util.config.ConciergeConfig.ThingsAggregatorConfig}
 * for Ditto's Concierge service.
 */
@Immutable
public final class DefaultThingsAggregatorConfig implements ConciergeConfig.ThingsAggregatorConfig {

    private static final String CONFIG_PATH = "things-aggregator";

    private final Duration singleRetrieveThingTimeout;
    private final int maxParallelism;

    private DefaultThingsAggregatorConfig(final ScopedConfig config) {
        singleRetrieveThingTimeout =
                config.getDuration(ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getConfigPath());
        maxParallelism = config.getInt(ThingsAggregatorConfigValue.MAX_PARALLELISM.getConfigPath());
    }

    /**
     * Returns an instance of {@code DittoConciergeThingsAggregatorConfig} based on the settings of the specified
     * Config.
     *
     * @param config is supposed to provide the settings of the things aggregator config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultThingsAggregatorConfig of(final Config config) {
        return new DefaultThingsAggregatorConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ThingsAggregatorConfigValue.values()));
    }

    @Override
    public Duration getSingleRetrieveThingTimeout() {
        return singleRetrieveThingTimeout;
    }

    @Override
    public int getMaxParallelism() {
        return maxParallelism;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultThingsAggregatorConfig that = (DefaultThingsAggregatorConfig) o;
        return maxParallelism == that.maxParallelism &&
                singleRetrieveThingTimeout.equals(that.singleRetrieveThingTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(singleRetrieveThingTimeout, maxParallelism);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "singleRetrieveThingTimeout=" + singleRetrieveThingTimeout +
                ", maxParallelism=" + maxParallelism +
                "]";
    }

}
