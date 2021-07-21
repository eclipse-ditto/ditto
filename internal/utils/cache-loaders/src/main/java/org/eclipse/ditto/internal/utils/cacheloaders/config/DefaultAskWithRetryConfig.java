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
package org.eclipse.ditto.internal.utils.cacheloaders.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig}.
 */
@Immutable
public final class DefaultAskWithRetryConfig implements AskWithRetryConfig {

    private final Duration askTimeout;
    private final RetryStrategy retryStrategy;
    private final int retryAttempts;
    private final Duration fixedDelay;
    private final Duration backoffDelayMin;
    private final Duration backoffDelayMax;
    private final double backoffDelayRandomFactor;

    private DefaultAskWithRetryConfig(final ConfigWithFallback configWithFallback) {
        askTimeout = configWithFallback.getNonNegativeAndNonZeroDurationOrThrow(AskWithRetryConfigValue.ASK_TIMEOUT);
        retryStrategy = configWithFallback.getEnum(RetryStrategy.class,
                AskWithRetryConfigValue.RETRY_STRATEGY.getConfigPath());
        retryAttempts = configWithFallback.getNonNegativeIntOrThrow(AskWithRetryConfigValue.RETRY_ATTEMPTS);
        fixedDelay = configWithFallback.getNonNegativeDurationOrThrow(AskWithRetryConfigValue.FIXED_DELAY);
        backoffDelayMin = configWithFallback.getNonNegativeDurationOrThrow(AskWithRetryConfigValue.BACKOFF_DELAY_MIN);
        backoffDelayMax = configWithFallback.getNonNegativeDurationOrThrow(AskWithRetryConfigValue.BACKOFF_DELAY_MAX);
        backoffDelayRandomFactor =
                configWithFallback.getNonNegativeDoubleOrThrow(AskWithRetryConfigValue.BACKOFF_DELAY_RANDOM_FACTOR);
    }

    /**
     * Returns an instance of {@code DefaultAskWithRetryConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cache config at {@code configPath}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAskWithRetryConfig of(final Config config, final String configPath) {
        return new DefaultAskWithRetryConfig(
                ConfigWithFallback.newInstance(config, configPath, AskWithRetryConfigValue.values()));
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public RetryStrategy getRetryStrategy() {
        return retryStrategy;
    }

    @Override
    public int getRetryAttempts() {
        return retryAttempts;
    }

    @Override
    public Duration getFixedDelay() {
        return fixedDelay;
    }

    @Override
    public Duration getBackoffDelayMin() {
        return backoffDelayMin;
    }

    @Override
    public Duration getBackoffDelayMax() {
        return backoffDelayMax;
    }

    @Override
    public double getBackoffDelayRandomFactor() {
        return backoffDelayRandomFactor;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAskWithRetryConfig that = (DefaultAskWithRetryConfig) o;
        return retryAttempts == that.retryAttempts &&
                Double.compare(that.backoffDelayRandomFactor, backoffDelayRandomFactor) == 0 &&
                askTimeout.equals(that.askTimeout) && retryStrategy == that.retryStrategy &&
                fixedDelay.equals(that.fixedDelay) && backoffDelayMin.equals(that.backoffDelayMin) &&
                backoffDelayMax.equals(that.backoffDelayMax);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout, retryStrategy, retryAttempts, fixedDelay, backoffDelayMin, backoffDelayMax,
                backoffDelayRandomFactor);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                ", retryStrategy=" + retryStrategy +
                ", retryAttempts=" + retryAttempts +
                ", fixedDelay=" + fixedDelay +
                ", backoffDelayMin=" + backoffDelayMin +
                ", backoffDelayMax=" + backoffDelayMax +
                ", backoffDelayRandomFactor=" + backoffDelayRandomFactor +
                "]";
    }

}
