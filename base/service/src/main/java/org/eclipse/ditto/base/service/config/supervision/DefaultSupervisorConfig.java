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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the config for connection supervision.
 */
@Immutable
public final class DefaultSupervisorConfig implements SupervisorConfig {

    private static final String CONFIG_PATH = "supervisor";

    private final ExponentialBackOffConfig exponentialBackOffConfig;

    private DefaultSupervisorConfig(final ExponentialBackOffConfig theExponentialBackOffConfig) {
        exponentialBackOffConfig = theExponentialBackOffConfig;
    }

    /**
     * Returns an instance of {@code DefaultSupervisorConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the supervisor config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSupervisorConfig of(final Config config) {
        final ScopedConfig supervisorScopedConfig = DefaultScopedConfig.newInstance(config, CONFIG_PATH);

        return new DefaultSupervisorConfig(DefaultExponentialBackOffConfig.of(supervisorScopedConfig));
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return exponentialBackOffConfig;
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

        return Objects.equals(exponentialBackOffConfig, that.exponentialBackOffConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exponentialBackOffConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "exponentialBackOffConfig=" + exponentialBackOffConfig +
                "]";
    }

}
