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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the config for the reconnect behaviour.
 */
@Immutable
public final class DefaultReconnectConfig implements ReconnectConfig, Serializable {

    private static final String CONFIG_PATH = "reconnect";

    private static final long serialVersionUID = -3953028710017813761L;

    private final Duration initialDelay;
    private final Duration interval;
    private final RateConfig rateConfig;

    private DefaultReconnectConfig(final ScopedConfig config, final RateConfig theRateConfig) {
        initialDelay = config.getDuration(ReconnectConfigValue.INITIAL_DELAY.getConfigPath());
        interval = config.getDuration(ReconnectConfigValue.INTERVAL.getConfigPath());
        rateConfig = theRateConfig;
    }

    /**
     * Returns an instance of {@code DefaultReconnectConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultReconnectConfig of(final Config config) {
        final ConfigWithFallback reconnectScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ReconnectConfigValue.values());

        return new DefaultReconnectConfig(reconnectScopedConfig, DefaultRateConfig.of(reconnectScopedConfig));
    }

    @Override
    public Duration getInitialDelay() {
        return initialDelay;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public RateConfig getRateConfig() {
        return rateConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultReconnectConfig that = (DefaultReconnectConfig) o;
        return Objects.equals(initialDelay, that.initialDelay) &&
                Objects.equals(interval, that.interval) &&
                Objects.equals(rateConfig, that.rateConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialDelay, interval, rateConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "initialDelay=" + initialDelay +
                ", interval=" + interval +
                ", rateConfig=" + rateConfig +
                "]";
    }

}
