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

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the hidden implementation of {@link org.eclipse.ditto.base.service.config.ThrottlingConfig}.
 */
@Immutable
final class DefaultConnectionThrottlingConfig implements ConnectionThrottlingConfig {

    private final ThrottlingConfig throttlingConfig;
    private final int consumerMaxInFlight;

    private DefaultConnectionThrottlingConfig(final ScopedConfig config) {
        throttlingConfig = ThrottlingConfig.of(config);
        consumerMaxInFlight = config.getNonNegativeIntOrThrow(ConfigValue.CONSUMER_MAX_IN_FLIGHT);
    }

    static ConnectionThrottlingConfig of(final Config config) {
        return new DefaultConnectionThrottlingConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Duration getInterval() {
        return throttlingConfig.getInterval();
    }

    @Override
    public int getLimit() {
        return throttlingConfig.getLimit();
    }

    @Override
    public int getConsumerMaxInFlight() {
        return consumerMaxInFlight;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultConnectionThrottlingConfig)) {
            return false;
        }
        final DefaultConnectionThrottlingConfig that = (DefaultConnectionThrottlingConfig) o;
        return consumerMaxInFlight == that.consumerMaxInFlight &&
                Objects.equals(throttlingConfig, that.throttlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerMaxInFlight, throttlingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "consumerMaxInFlight=" + consumerMaxInFlight +
                ", throttlingConfig=" + throttlingConfig +
                "]";
    }

}
