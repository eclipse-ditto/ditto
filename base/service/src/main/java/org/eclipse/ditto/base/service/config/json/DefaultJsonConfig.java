/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.config.json;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link JsonConfig}.
 */
@Immutable
public final class DefaultJsonConfig implements JsonConfig, WithConfigPath {

    private static final String CONFIG_PATH = "json";

    private final double escapingBufferFactor;

    private DefaultJsonConfig(final ConfigWithFallback config) {
        escapingBufferFactor = config.getDouble(ConfigValue.ESCAPING_BUFFER_FACTOR.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultJsonConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JSON config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultJsonConfig of(final Config config) {
        return new DefaultJsonConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public double getEscapingBufferFactor() {
        return escapingBufferFactor;
    }

    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultJsonConfig that = (DefaultJsonConfig) o;
        return escapingBufferFactor == that.escapingBufferFactor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(escapingBufferFactor);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "escapingBufferFactor=" + escapingBufferFactor +
                "]";
    }

}
