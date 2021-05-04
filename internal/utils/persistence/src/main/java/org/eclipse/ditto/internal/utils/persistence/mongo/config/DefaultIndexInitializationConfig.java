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

import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link IndexInitializationConfig}.
 */
public final class DefaultIndexInitializationConfig implements IndexInitializationConfig {

    private static final String CONFIG_PATH = "index-initialization";

    private final boolean indexInitializationEnabled;

    private DefaultIndexInitializationConfig(final ScopedConfig config) {
        indexInitializationEnabled = config.getBoolean(IndexInitializerConfigValue.ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of DefaultIndexInitializationConfig which tries to obtain its properties from the given
     * config.
     *
     * @param config the Config which contains nested MongoDB settings at path {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultIndexInitializationConfig of(final Config config) {
        return new DefaultIndexInitializationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, IndexInitializerConfigValue.values()));
    }

    @Override
    public boolean isIndexInitializationConfigEnabled() {
        return indexInitializationEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultIndexInitializationConfig that = (DefaultIndexInitializationConfig) o;
        return Objects.equals(indexInitializationEnabled, that.indexInitializationEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexInitializationEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "indexInitializationEnabled=" + indexInitializationEnabled +
                "]";
    }

}

