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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.CircuitBreakerConfig}.
 */
@Immutable
public final class DefaultCircuitBreakerConfig implements MongoDbConfig.CircuitBreakerConfig {

    private static final String CONFIG_PATH = "breaker";

    private final int maxFailures;
    private final TimeoutConfig timeoutConfig;

    private DefaultCircuitBreakerConfig(final ScopedConfig config) {
        maxFailures = config.getPositiveIntOrThrow(CircuitBreakerConfigValue.MAX_FAILURES);
        timeoutConfig = DefaultTimeoutConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultCircuitBreakerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the circuit breaker config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCircuitBreakerConfig of(final Config config) {
        return new DefaultCircuitBreakerConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, CircuitBreakerConfigValue.values()));
    }

    @Override
    public int getMaxFailures() {
        return maxFailures;
    }

    @Override
    public TimeoutConfig getTimeoutConfig() {
        return timeoutConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCircuitBreakerConfig that = (DefaultCircuitBreakerConfig) o;
        return maxFailures == that.maxFailures && Objects.equals(timeoutConfig, that.timeoutConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxFailures, timeoutConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxFailures=" + maxFailures +
                ", timeoutConfig=" + timeoutConfig +
                "]";
    }

}
