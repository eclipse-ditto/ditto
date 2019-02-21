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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.OptionsConfig}.
 */
@Immutable
public final class DefaultOptionsConfig implements MongoDbConfig.OptionsConfig, Serializable {

    /**
     * The supposed path of the OptionsConfig within the MongoDB config object.
     */
    static final String CONFIG_PATH = "options";

    private static final long serialVersionUID = -4182418001331538993L;

    private final boolean sslEnabled;

    private DefaultOptionsConfig(final ScopedConfig config) {
        sslEnabled = config.getBoolean(OptionsConfigValue.SSL_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultOptionsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the options config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOptionsConfig of(final Config config) {
        return new DefaultOptionsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, OptionsConfigValue.values()));
    }

    @Override
    public boolean isSslEnabled() {
        return sslEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOptionsConfig that = (DefaultOptionsConfig) o;
        return sslEnabled == that.sslEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sslEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sslEnabled=" + sslEnabled +
                "]";
    }

}
