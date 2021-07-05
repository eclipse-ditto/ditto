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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.CircuitBreakerConfig.TimeoutConfig}.
 */
@Immutable
public final class DefaultTimeoutConfig implements MongoDbConfig.CircuitBreakerConfig.TimeoutConfig {

    private static final String CONFIG_PATH = "timeout";

    private final Duration call;
    private final Duration reset;

    private DefaultTimeoutConfig(final ScopedConfig config) {
        call = config.getNonNegativeAndNonZeroDurationOrThrow(TimeoutConfigValue.CALL);
        reset = config.getNonNegativeAndNonZeroDurationOrThrow(TimeoutConfigValue.RESET);
    }

    /**
     * Returns an instance of {@code DefaultTimeoutConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the timeout config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
