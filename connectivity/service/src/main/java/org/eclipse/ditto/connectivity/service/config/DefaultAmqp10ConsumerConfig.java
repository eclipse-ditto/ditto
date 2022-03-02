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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link Amqp10ConsumerConfig}.
 */
@Immutable
final class DefaultAmqp10ConsumerConfig implements Amqp10ConsumerConfig {

    private static final String CONFIG_PATH = "consumer";

    private final Duration redeliveryExpectationTimeout;
    private final ConnectionThrottlingConfig throttlingConfig;

    private DefaultAmqp10ConsumerConfig(final ScopedConfig config) {
        redeliveryExpectationTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(
                ConfigValue.REDELIVERY_EXPECTATION_TIMEOUT);
        throttlingConfig = ConnectionThrottlingConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultAmqp10ConsumerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the AMQP 1.0 config setting.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAmqp10ConsumerConfig of(final Config config) {
        return new DefaultAmqp10ConsumerConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Duration getRedeliveryExpectationTimeout() {
        return redeliveryExpectationTimeout;
    }

    @Override
    public ConnectionThrottlingConfig getThrottlingConfig() {
        return throttlingConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAmqp10ConsumerConfig that = (DefaultAmqp10ConsumerConfig) o;
        return Objects.equals(redeliveryExpectationTimeout, that.redeliveryExpectationTimeout) &&
                Objects.equals(throttlingConfig, that.throttlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(redeliveryExpectationTimeout, throttlingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "redeliveryExpectationTimeout=" + redeliveryExpectationTimeout +
                ", throttlingConfig=" + throttlingConfig +
                "]";
    }
}
