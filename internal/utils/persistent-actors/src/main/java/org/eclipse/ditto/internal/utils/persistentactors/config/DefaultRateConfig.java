/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link RateConfig}.
 */
@Immutable
public final class DefaultRateConfig implements RateConfig {

    private static final String CONFIG_PATH = "rate";

    private final Duration frequency;
    private final int entityAmount;

    private DefaultRateConfig(final ConfigWithFallback config) {
        frequency = config.getNonNegativeAndNonZeroDurationOrThrow(RateConfigValue.FREQUENCY);
        entityAmount = config.getPositiveIntOrThrow(RateConfigValue.ENTITIES);
    }

    /**
     * Returns an instance of {@code DefaultRateConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
