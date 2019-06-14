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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the public health endpoint config.
 */
@Immutable
public final class DefaultPublicHealthConfig implements PublicHealthConfig {

    private static final String CONFIG_PATH = "public-health";

    private final Duration cacheTimeout;

    private DefaultPublicHealthConfig(final ScopedConfig scopedConfig) {
        cacheTimeout = scopedConfig.getDuration(PublicHealthConfigValue.CACHE_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultPublicHealthConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the public health config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPublicHealthConfig of(final Config config) {
        return new DefaultPublicHealthConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PublicHealthConfigValue.values()));
    }

    @Override
    public Duration getCacheTimeout() {
        return cacheTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPublicHealthConfig that = (DefaultPublicHealthConfig) o;
        return cacheTimeout.equals(that.cacheTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "cacheTimeout=" + cacheTimeout +
                "]";
    }

}
