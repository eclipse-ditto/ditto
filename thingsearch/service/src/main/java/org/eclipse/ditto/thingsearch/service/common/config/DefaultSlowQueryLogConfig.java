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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation for {@link SlowQueryLogConfig}.
 */
@Immutable
public final class DefaultSlowQueryLogConfig implements SlowQueryLogConfig {

    /**
     * Path where the slow query log config values are expected.
     */
    static final String CONFIG_PATH = "slow-query-log";

    private final boolean enabled;
    private final Duration threshold;

    private DefaultSlowQueryLogConfig(final ConfigWithFallback configWithFallback) {
        enabled = configWithFallback.getBoolean(SlowQueryLogConfigValue.ENABLED.getConfigPath());
        threshold = configWithFallback.getNonNegativeDurationOrThrow(SlowQueryLogConfigValue.THRESHOLD);
    }

    /**
     * Returns an instance of DefaultSlowQueryLogConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the slow query log config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSlowQueryLogConfig of(final Config config) {
        return new DefaultSlowQueryLogConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SlowQueryLogConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getThreshold() {
        return threshold;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSlowQueryLogConfig that = (DefaultSlowQueryLogConfig) o;
        return enabled == that.enabled &&
                Objects.equals(threshold, that.threshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, threshold);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", threshold=" + threshold +
                "]";
    }

}
