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
package org.eclipse.ditto.services.connectivity.util;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * TODO
 */
@Immutable
public final class DefaultRateConfig implements ConnectivityConfig.ReconnectConfig.RateConfig {

    private static final String CONFIG_PATH = "rate";

    private final Duration frequency;
    private final int entityAmount;

    private DefaultRateConfig(final ScopedConfig config) {
        frequency = config.getDuration(RateConfigValue.FREQUENCY.getConfigPath());
        entityAmount = config.getInt(RateConfigValue.ENTITIES.getConfigPath());
    }

    /**
     * TODO
     *
     * @param config
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultRateConfig of(final Config config) {
        return new DefaultRateConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, RateConfigValue.values()));
    }

    @Override
    public Duration getFrequency() {
        return frequency;
    }

    @Override
    public int getEntityAmount() {
        return entityAmount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultRateConfig that = (DefaultRateConfig) o;
        return entityAmount == that.entityAmount &&
                Objects.equals(frequency, that.frequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frequency, entityAmount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "frequency=" + frequency +
                ", entityAmount=" + entityAmount +
                "]";
    }

}
