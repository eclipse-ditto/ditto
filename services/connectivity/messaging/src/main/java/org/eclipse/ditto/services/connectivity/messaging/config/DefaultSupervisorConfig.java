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
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the config for connection supervision.
 */
@Immutable
public final class DefaultSupervisorConfig implements ConnectionConfig.SupervisorConfig, Serializable {

    private static final long serialVersionUID = -9207034637228951218L;

    private static final String CONFIG_PATH = "supervisor";

    private final ExponentialBackOffConfig exponentialBackOffConfig;

    private DefaultSupervisorConfig(final ExponentialBackOffConfig theExponentialBackOffConfig) {
        exponentialBackOffConfig = theExponentialBackOffConfig;
    }

    /**
     * Returns an instance of {@code DefaultSupervisorConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
