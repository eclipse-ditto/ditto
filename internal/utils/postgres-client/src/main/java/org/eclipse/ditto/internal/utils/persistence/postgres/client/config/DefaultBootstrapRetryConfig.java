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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PostgresConfig.BootstrapConfig.RetryConfig}.
 */
@Immutable
public final class DefaultBootstrapRetryConfig implements PostgresConfig.BootstrapConfig.RetryConfig {

    static final String CONFIG_PATH = "retry";

    private final boolean enabled;
    private final Duration maxDuration;
    private final Duration minBackoff;
    private final Duration maxBackoff;

    private DefaultBootstrapRetryConfig(final ScopedConfig config) {
        enabled = config.getBoolean(RetryConfigValue.ENABLED.getConfigPath());
        maxDuration = config.getNonNegativeDurationOrThrow(RetryConfigValue.MAX_DURATION);
        minBackoff = config.getNonNegativeAndNonZeroDurationOrThrow(RetryConfigValue.MIN_BACKOFF);
        maxBackoff = config.getNonNegativeAndNonZeroDurationOrThrow(RetryConfigValue.MAX_BACKOFF);
        if (maxBackoff.compareTo(minBackoff) < 0) {
            throw new DittoConfigError("The duration at <" + config.getConfigPath() + "."
                    + RetryConfigValue.MAX_BACKOFF.getConfigPath() + "> must not be shorter than <"
                    + config.getConfigPath() + "." + RetryConfigValue.MIN_BACKOFF.getConfigPath() + "> (" + minBackoff
                    + ") but it was <" + maxBackoff + ">!");
        }
    }

    /**
     * Returns an instance of {@code DefaultBootstrapRetryConfig} based on the settings of the given Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws DittoConfigError if {@code config} is invalid.
     */
    public static DefaultBootstrapRetryConfig of(final Config config) {
        return new DefaultBootstrapRetryConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, RetryConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getMaxDuration() {
        return maxDuration;
    }

    @Override
    public Duration getMinBackoff() {
        return minBackoff;
    }

    @Override
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultBootstrapRetryConfig that = (DefaultBootstrapRetryConfig) o;
        return enabled == that.enabled &&
                Objects.equals(maxDuration, that.maxDuration) &&
                Objects.equals(minBackoff, that.minBackoff) &&
                Objects.equals(maxBackoff, that.maxBackoff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxDuration, minBackoff, maxBackoff);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", maxDuration=" + maxDuration +
                ", minBackoff=" + minBackoff +
                ", maxBackoff=" + maxBackoff +
                "]";
    }

}
