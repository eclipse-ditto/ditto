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
package org.eclipse.ditto.connectivity.service.config;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link ConnectionThrottlingConfig}.
 */
@Immutable
final class DefaultConnectionThrottlingConfig implements ConnectionThrottlingConfig {

    private final ThrottlingConfig throttlingConfig;
    private final double maxInFlightFactor;
    private final double throttlingDetectionTolerance;

    private DefaultConnectionThrottlingConfig(final ScopedConfig config, final ThrottlingConfig throttlingConfig) {
        this.throttlingConfig = throttlingConfig;
        maxInFlightFactor = config.getPositiveDoubleOrThrow(ConfigValue.MAX_IN_FLIGHT_FACTOR);
        throttlingDetectionTolerance = config.getPositiveDoubleOrThrow(ConfigValue.THROTTLING_DETECTION_TOLERANCE);
        if (maxInFlightFactor < 1.0) {
            throw new DittoConfigError(MessageFormat.format(
                    "The double value at <{0}> must be >= 1.0 but it was <{1}>!",
                    ConfigValue.MAX_IN_FLIGHT_FACTOR.getConfigPath(), maxInFlightFactor));
        }
        if (throttlingDetectionTolerance > 1.0) {
            throw new DittoConfigError(MessageFormat.format(
                    "The double value at <{0}> must be <= 1.0 but it was <{1}>!",
                    ConfigValue.THROTTLING_DETECTION_TOLERANCE.getConfigPath(), maxInFlightFactor));
        }
    }

    static ConnectionThrottlingConfig of(final Config config) {
        final ThrottlingConfig throttlingConfig = ThrottlingConfig.of(config);
        return new DefaultConnectionThrottlingConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()), throttlingConfig);
    }

    @Override
    public boolean isEnabled() {
        return throttlingConfig.isEnabled();
    }

    @Override
    public Duration getInterval() {
        return throttlingConfig.getInterval();
    }

    @Override
    public int getLimit() {
        return throttlingConfig.getLimit();
    }

    @Override
    public double getMaxInFlightFactor() {
        return maxInFlightFactor;
    }

    @Override
    public double getThrottlingDetectionTolerance() {
        return throttlingDetectionTolerance;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultConnectionThrottlingConfig that = (DefaultConnectionThrottlingConfig) o;
        return Double.compare(that.maxInFlightFactor, maxInFlightFactor) == 0
                && Double.compare(that.throttlingDetectionTolerance, throttlingDetectionTolerance) == 0
                && Objects.equals(throttlingConfig, that.throttlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxInFlightFactor, throttlingConfig, throttlingDetectionTolerance);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxInFlightFactor=" + maxInFlightFactor +
                "throttlingDetectionTolerance=" + throttlingDetectionTolerance +
                ", throttlingConfig=" + throttlingConfig +
                "]";
    }

}
