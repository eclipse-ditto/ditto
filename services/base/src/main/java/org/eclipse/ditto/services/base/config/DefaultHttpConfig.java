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
package org.eclipse.ditto.services.base.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.HttpConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpConfig}.
 */
@Immutable
public final class DefaultHttpConfig implements HttpConfig {

    private enum HttpConfigValue implements KnownConfigValue {

        HOSTNAME("hostname", ""),

        PORT("port", 8080);

        private final String path;
        private final Object defaultValue;

        private HttpConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    private static final String CONFIG_PATH = "http";

    private final Config config;

    private DefaultHttpConfig(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Returns an instance of {@code DefaultHttpConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the HTTP config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.base.config.DittoConfigError if {@code config} is {@code null} if the
     * value of {@code config} at {@code configPath} is not of type
     * {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultHttpConfig of(final Config config) {
        return new DefaultHttpConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, HttpConfigValue.values()));
    }

    @Override
    public String getHostname() {
        return config.getString(HttpConfigValue.HOSTNAME.getPath());
    }

    @Override
    public int getPort() {
        return config.getInt(HttpConfigValue.PORT.getPath());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpConfig that = (DefaultHttpConfig) o;
        return config.equals(that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                "]";
    }

}
