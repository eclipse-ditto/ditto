/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.CircuitBreakerConfig}.
 */
@Immutable
public final class DefaultCircuitBreakerConfig implements MongoDbConfig.CircuitBreakerConfig, Serializable {

    private static final String CONFIG_PATH = "breaker";

    private static final long serialVersionUID = 3134626495417410671L;

    private final int maxFailures;
    private final TimeoutConfig timeoutConfig;

    private DefaultCircuitBreakerConfig(final ScopedConfig config) {
        maxFailures = config.getInt(CircuitBreakerConfigValue.MAX_FAILURES.getConfigPath());
        timeoutConfig = DefaultTimeoutConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultCircuitBreakerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the circuit breaker config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
