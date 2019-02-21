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
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.CircuitBreakerConfig.TimeoutConfig}.
 */
@Immutable
public final class DefaultTimeoutConfig implements MongoDbConfig.CircuitBreakerConfig.TimeoutConfig, Serializable {

    private static final String CONFIG_PATH = "timeout";

    private static final long serialVersionUID = 1528096509962994968L;

    private final Duration call;
    private final Duration reset;

    private DefaultTimeoutConfig(final ScopedConfig config) {
        call = config.getDuration(TimeoutConfigValue.CALL.getConfigPath());
        reset = config.getDuration(TimeoutConfigValue.RESET.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultTimeoutConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the timeout config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTimeoutConfig of(final Config config) {
        return new DefaultTimeoutConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, TimeoutConfigValue.values()));
    }

    @Override
    public Duration getCall() {
        return call;
    }

    @Override
    public Duration getReset() {
        return reset;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTimeoutConfig that = (DefaultTimeoutConfig) o;
        return Objects.equals(call, that.call) && Objects.equals(reset, that.reset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(call, reset);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "call=" + call +
                ", reset=" + reset +
                "]";
    }

}
