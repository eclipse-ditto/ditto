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
package org.eclipse.ditto.internal.utils.health.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link HealthCheckConfig}.
 */
@Immutable
public final class DefaultHealthCheckConfig implements HealthCheckConfig {

    private final BasicHealthCheckConfig basicHealthCheckConfig;
    private final PersistenceConfig persistenceConfig;

    private DefaultHealthCheckConfig(final BasicHealthCheckConfig basicHealthCheckConfig,
            final PersistenceConfig persistenceConfig) {

        this.basicHealthCheckConfig = basicHealthCheckConfig;
        this.persistenceConfig = persistenceConfig;
    }

    /**
     * Returns an instance of {@code DefaultHealthCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the health check as nested Config.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHealthCheckConfig of(final Config config) {
        final var basicHealthCheckConfig = DefaultBasicHealthCheckConfig.of(config);

        final String healthCheckConfigPath = basicHealthCheckConfig.getConfigPath();
        final PersistenceConfig persistenceConfig;
        if (config.hasPath(healthCheckConfigPath)) {
            persistenceConfig = DefaultPersistenceConfig.of(config.getConfig(healthCheckConfigPath));
        } else {
            // Completely rely on fall-back values of PersistenceConfig.
            persistenceConfig = DefaultPersistenceConfig.of(ConfigFactory.empty());
        }

        return new DefaultHealthCheckConfig(basicHealthCheckConfig, persistenceConfig);
    }

    @Override
    public boolean isEnabled() {
        return basicHealthCheckConfig.isEnabled();
    }

    @Override
    public Duration getInterval() {
        return basicHealthCheckConfig.getInterval();
    }

    @Override
    public PersistenceConfig getPersistenceConfig() {
        return persistenceConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHealthCheckConfig that = (DefaultHealthCheckConfig) o;
        return Objects.equals(basicHealthCheckConfig, that.basicHealthCheckConfig) &&
                Objects.equals(persistenceConfig, that.persistenceConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basicHealthCheckConfig, persistenceConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "basicHealthCheckConfig=" + basicHealthCheckConfig +
                ", persistenceConfig=" + persistenceConfig +
                "]";
    }

}
