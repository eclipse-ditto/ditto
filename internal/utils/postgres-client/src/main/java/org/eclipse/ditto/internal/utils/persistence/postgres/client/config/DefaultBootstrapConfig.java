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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PostgresConfig.BootstrapConfig}.
 */
@Immutable
public final class DefaultBootstrapConfig implements PostgresConfig.BootstrapConfig {

    static final String CONFIG_PATH = "bootstrap";

    private final Duration attemptTimeout;
    private final boolean exitOnFailure;
    private final DefaultBootstrapRetryConfig retryConfig;

    private DefaultBootstrapConfig(final ScopedConfig config) {
        attemptTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(BootstrapConfigValue.ATTEMPT_TIMEOUT);
        exitOnFailure = config.getBoolean(BootstrapConfigValue.EXIT_ON_FAILURE.getConfigPath());
        retryConfig = DefaultBootstrapRetryConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultBootstrapConfig} based on the settings of the given Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultBootstrapConfig of(final Config config) {
        return new DefaultBootstrapConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, BootstrapConfigValue.values()));
    }

    @Override
    public Duration getAttemptTimeout() {
        return attemptTimeout;
    }

    @Override
    public boolean isExitOnFailure() {
        return exitOnFailure;
    }

    @Override
    public DefaultBootstrapRetryConfig getRetryConfig() {
        return retryConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultBootstrapConfig that = (DefaultBootstrapConfig) o;
        return exitOnFailure == that.exitOnFailure &&
                Objects.equals(attemptTimeout, that.attemptTimeout) &&
                Objects.equals(retryConfig, that.retryConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptTimeout, exitOnFailure, retryConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "attemptTimeout=" + attemptTimeout +
                ", exitOnFailure=" + exitOnFailure +
                ", retryConfig=" + retryConfig +
                "]";
    }

}
