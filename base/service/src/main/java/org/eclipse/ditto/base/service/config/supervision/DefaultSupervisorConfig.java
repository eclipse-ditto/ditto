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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the config for connection supervision.
 */
@Immutable
public final class DefaultSupervisorConfig implements SupervisorConfig {

    private static final String CONFIG_PATH = "supervisor";

    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private final DefaultLocalAskTimeoutConfig localAskTimeoutConfig;
    private final Duration shutdownTimeout;

    private DefaultSupervisorConfig(final ExponentialBackOffConfig theExponentialBackOffConfig,
            final DefaultLocalAskTimeoutConfig theLocalAskTimeoutConfig,
            final Duration theShutdownTimeout) {
        exponentialBackOffConfig = theExponentialBackOffConfig;
        localAskTimeoutConfig = theLocalAskTimeoutConfig;
        shutdownTimeout = theShutdownTimeout;
    }

    /**
     * Returns an instance of {@code DefaultSupervisorConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the supervisor config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSupervisorConfig of(final Config config) {
        final ScopedConfig supervisorScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SupervisorConfigValue.values());

        return new DefaultSupervisorConfig(DefaultExponentialBackOffConfig.of(supervisorScopedConfig),
                DefaultLocalAskTimeoutConfig.of(supervisorScopedConfig),
                supervisorScopedConfig.getNonNegativeAndNonZeroDurationOrThrow(SupervisorConfigValue.SHUTDOWN_TIMEOUT));
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return exponentialBackOffConfig;
    }

    @Override
    public LocalAskTimeoutConfig getLocalAskTimeoutConfig() {
        return localAskTimeoutConfig;
    }

    @Override
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSupervisorConfig that = (DefaultSupervisorConfig) o;

        return Objects.equals(exponentialBackOffConfig, that.exponentialBackOffConfig) &&
                Objects.equals(localAskTimeoutConfig, that.localAskTimeoutConfig) &&
                Objects.equals(shutdownTimeout, that.shutdownTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exponentialBackOffConfig, localAskTimeoutConfig, shutdownTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "exponentialBackOffConfig=" + exponentialBackOffConfig +
                ", localAskTimeoutConfig=" + localAskTimeoutConfig +
                ", shutdownTimeout=" + shutdownTimeout +
                "]";
    }

}
