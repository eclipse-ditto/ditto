/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link TimeseriesIngestConfig}.
 */
@Immutable
public final class DefaultTimeseriesIngestConfig implements TimeseriesIngestConfig {

    private static final String CONFIG_PATH = "timeseries";

    private static final Duration RETRY_MAX_BACKOFF_CEILING = Duration.ofHours(1);

    private final Duration maxMeasurementAge;
    private final int retryMaxAttempts;
    private final Duration retryBackoff;
    private final Duration retryMaxBackoff;
    private final int retryMaxPendingRetries;

    private DefaultTimeseriesIngestConfig(final ScopedConfig config) {
        maxMeasurementAge =
                config.getNonNegativeAndNonZeroDurationOrThrow(TimeseriesIngestConfigValue.MAX_MEASUREMENT_AGE);
        retryMaxAttempts = config.getPositiveIntOrThrow(TimeseriesIngestConfigValue.RETRY_MAX_ATTEMPTS);
        retryBackoff = config.getNonNegativeAndNonZeroDurationOrThrow(TimeseriesIngestConfigValue.RETRY_BACKOFF);
        retryMaxBackoff =
                config.getNonNegativeAndNonZeroDurationOrThrow(TimeseriesIngestConfigValue.RETRY_MAX_BACKOFF);
        retryMaxPendingRetries =
                config.getNonNegativeIntOrThrow(TimeseriesIngestConfigValue.RETRY_MAX_PENDING_RETRIES);
        if (retryMaxBackoff.compareTo(retryBackoff) < 0 || retryMaxBackoff.compareTo(RETRY_MAX_BACKOFF_CEILING) > 0) {
            throw new DittoConfigError("<" + config.getConfigPath() + "." +
                    TimeseriesIngestConfigValue.RETRY_MAX_BACKOFF.getConfigPath() + "> must lie between the backoff <" +
                    retryBackoff + "> and <" + RETRY_MAX_BACKOFF_CEILING + ">, was <" + retryMaxBackoff + ">.");
        }
    }

    /**
     * Returns an instance of {@code DefaultTimeseriesIngestConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTimeseriesIngestConfig of(final Config config) {
        return new DefaultTimeseriesIngestConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, TimeseriesIngestConfigValue.values()));
    }

    @Override
    public Duration getMaxMeasurementAge() {
        return maxMeasurementAge;
    }

    @Override
    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    @Override
    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    @Override
    public Duration getRetryMaxBackoff() {
        return retryMaxBackoff;
    }

    @Override
    public int getRetryMaxPendingRetries() {
        return retryMaxPendingRetries;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!(o instanceof final DefaultTimeseriesIngestConfig that)) {
            return false;
        }
        return retryMaxAttempts == that.retryMaxAttempts &&
                retryMaxPendingRetries == that.retryMaxPendingRetries &&
                Objects.equals(maxMeasurementAge, that.maxMeasurementAge) &&
                Objects.equals(retryBackoff, that.retryBackoff) &&
                Objects.equals(retryMaxBackoff, that.retryMaxBackoff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxMeasurementAge, retryMaxAttempts, retryBackoff, retryMaxBackoff,
                retryMaxPendingRetries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxMeasurementAge=" + maxMeasurementAge +
                ", retryMaxAttempts=" + retryMaxAttempts +
                ", retryBackoff=" + retryBackoff +
                ", retryMaxBackoff=" + retryMaxBackoff +
                ", retryMaxPendingRetries=" + retryMaxPendingRetries +
                "]";
    }
}
