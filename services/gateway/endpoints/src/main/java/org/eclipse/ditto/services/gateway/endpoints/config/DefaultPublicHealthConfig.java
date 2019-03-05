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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.io.Serializable;
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
public final class DefaultPublicHealthConfig implements PublicHealthConfig, Serializable {

    private static final String CONFIG_PATH = "public-health";

    private static final long serialVersionUID = 3843294309781510818L;

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
