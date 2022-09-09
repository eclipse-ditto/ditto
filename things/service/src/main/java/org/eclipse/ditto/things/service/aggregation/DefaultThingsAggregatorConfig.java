/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.aggregation;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link ThingsAggregatorConfig}.
 */
@Immutable
public final class DefaultThingsAggregatorConfig implements ThingsAggregatorConfig {

    private static final String CONFIG_PATH = "things-aggregator";

    private final Duration singleRetrieveThingTimeout;
    private final int maxParallelism;

    private DefaultThingsAggregatorConfig(final ScopedConfig config) {
        singleRetrieveThingTimeout =
                config.getNonNegativeAndNonZeroDurationOrThrow(ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT);
        maxParallelism = config.getPositiveIntOrThrow(ThingsAggregatorConfigValue.MAX_PARALLELISM);
    }

    /**
     * Returns an instance of {@code DittoThingsAggregatorConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the things aggregator config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
                Objects.equals(singleRetrieveThingTimeout, that.singleRetrieveThingTimeout);
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
