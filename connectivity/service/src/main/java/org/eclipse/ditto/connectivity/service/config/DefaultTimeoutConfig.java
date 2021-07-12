/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link TimeoutConfig}.
 */
@Immutable
public final class DefaultTimeoutConfig implements TimeoutConfig {

    private static final String CONFIG_PATH = "timeout";

    private final Duration minTimeout;
    private final Duration maxTimeout;

    private DefaultTimeoutConfig(final ScopedConfig config) {
        minTimeout = config.getNonNegativeDurationOrThrow(TimeoutConfigValue.MIN_TIMEOUT);
        maxTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(TimeoutConfigValue.MAX_TIMEOUT);
    }

    /**
     * Returns an instance of {@code DefaultTimeoutConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTimeoutConfig of(final Config config) {
        return new DefaultTimeoutConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, TimeoutConfigValue.values()));
    }

    @Override
    public Duration getMinTimeout() {
        return this.minTimeout;
    }

    @Override
    public Duration getMaxTimeout() {
        return this.maxTimeout;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTimeoutConfig that = (DefaultTimeoutConfig) o;
        return Objects.equals(minTimeout, that.minTimeout) &&
                Objects.equals(maxTimeout, that.maxTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minTimeout, maxTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "minTimeout=" + minTimeout +
                ", maxTimeout=" + maxTimeout +
                "]";
    }

}
