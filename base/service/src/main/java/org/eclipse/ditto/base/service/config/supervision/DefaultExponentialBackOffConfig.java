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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the exponential back-off config.
 */
@Immutable
public final class DefaultExponentialBackOffConfig implements ExponentialBackOffConfig {

    private static final String CONFIG_PATH = "exponential-backoff";

    private final Duration min;
    private final Duration max;
    private final double randomFactor;
    private final Duration corruptedReceiveTimeout;

    private DefaultExponentialBackOffConfig(final ScopedConfig config) {
        min = config.getNonNegativeDurationOrThrow(ExponentialBackOffConfigValue.MIN);
        max = config.getNonNegativeDurationOrThrow(ExponentialBackOffConfigValue.MAX);
        randomFactor = config.getPositiveDoubleOrThrow(ExponentialBackOffConfigValue.RANDOM_FACTOR);
        corruptedReceiveTimeout =
                config.getNonNegativeDurationOrThrow(ExponentialBackOffConfigValue.CORRUPTED_RECEIVE_TIMEOUT);
    }

    /**
     * Returns an instance of {@code DefaultExponentialBackOffConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultExponentialBackOffConfig of(final Config config) {
        return new DefaultExponentialBackOffConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ExponentialBackOffConfigValue.values()));
    }

    @Override
    public Duration getMin() {
        return min;
    }

    @Override
    public Duration getMax() {
        return max;
    }

    @Override
    public double getRandomFactor() {
        return randomFactor;
    }

    @Override
    public Duration getCorruptedReceiveTimeout() {
        return corruptedReceiveTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultExponentialBackOffConfig that = (DefaultExponentialBackOffConfig) o;
        return Double.compare(that.randomFactor, randomFactor) == 0 &&
                Objects.equals(min, that.min) &&
                Objects.equals(max, that.max) &&
                Objects.equals(corruptedReceiveTimeout, that.corruptedReceiveTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, randomFactor, corruptedReceiveTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "min=" + min +
                ", max=" + max +
                ", randomFactor=" + randomFactor +
                ", corruptedReceiveTimeout=" + corruptedReceiveTimeout +
                "]";
    }

}
