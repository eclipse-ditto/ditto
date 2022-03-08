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
package org.eclipse.ditto.base.service.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the hidden implementation of {@link ThrottlingConfig}.
 */
@Immutable
final class DefaultThrottlingConfig implements ThrottlingConfig {

    private final boolean enabled;
    private final Duration interval;
    private final int limit;

    private DefaultThrottlingConfig(final ScopedConfig config) {
        enabled = config.getBoolean(ConfigValue.ENABLED.getConfigPath());
        interval = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.INTERVAL);
        limit = config.getPositiveIntOrThrow(ConfigValue.LIMIT);
    }

    static DefaultThrottlingConfig of(final Config config) {
        return new DefaultThrottlingConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultThrottlingConfig)) {
            return false;
        }
        final DefaultThrottlingConfig that = (DefaultThrottlingConfig) o;
        return enabled == that.enabled &&
                limit == that.limit &&
                Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, interval, limit);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", interval=" + interval +
                ", limit=" + limit +
                "]";
    }

}
