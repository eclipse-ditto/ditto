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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the client config.
 */
@Immutable
public final class DefaultClientConfig implements ClientConfig {

    private static final String CONFIG_PATH = "client";

    private final Duration initTimeout;

    private DefaultClientConfig(final ScopedConfig config) {
        initTimeout = config.getDuration(ClientConfigValue.INIT_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultClientConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultClientConfig of(final Config config) {
        return new DefaultClientConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ClientConfigValue.values()));
    }

    @Override
    public Duration getInitTimeout() {
        return initTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClientConfig that = (DefaultClientConfig) o;
        return Objects.equals(initTimeout, that.initTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "initTimeout=" + initTimeout +
                "]";
    }

}
