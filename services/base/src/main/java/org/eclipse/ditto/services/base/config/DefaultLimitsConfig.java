/*
 * Copyright (c) 2017-2019 Bosch Software Innovations GmbH.
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

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.LimitsConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link LimitsConfig}.
 */
@Immutable
public final class DefaultLimitsConfig implements LimitsConfig {

    private enum LimitsConfigValue implements KnownConfigValue {

        THINGS_MAX_SIZE("things.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        POLICIES_MAX_SIZE("policies.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        MESSAGES_MAX_SIZE("messages.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        THINGS_SEARCH_DEFAULT_PAGE_SIZE(Constants.THINGS_SEARCH_KEY + "." + "default-page-size", 25),

        THINGS_SEARCH_MAX_PAGE_SIZE(Constants.THINGS_SEARCH_KEY + "." + "max-page-size", 200);

        private final String path;
        private final Object defaultValue;

        private LimitsConfigValue(final String thePath, final Object theDefaultValue) {
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

        private static final class Constants {

            private static final long DEFAULT_ENTITY_MAX_SIZE = 100 * 1024L;

            private static final String THINGS_SEARCH_KEY = "things-search";

        }

    }

    private static final String CONFIG_PATH = "limits";

    private final Config config;

    private DefaultLimitsConfig(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Returns an instance of {@code DefaultLimitsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the limits config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.base.config.DittoConfigError if {@code config} is {@code null} if the
     * value of {@code config} at {@code configPath} is not of type
     * {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultLimitsConfig of(final Config config) {
        return new DefaultLimitsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, LimitsConfigValue.values()));
    }

    @Override
    public long getThingsMaxSize() {
        return config.getBytes(LimitsConfigValue.THINGS_MAX_SIZE.getPath());
    }

    @Override
    public long getPoliciesMaxSize() {
        return config.getBytes(LimitsConfigValue.POLICIES_MAX_SIZE.getPath());
    }

    @Override
    public long getMessagesMaxSize() {
        return config.getBytes(LimitsConfigValue.MESSAGES_MAX_SIZE.getPath());
    }

    @Override
    public int getThingsSearchDefaultPageSize() {
        return config.getInt(LimitsConfigValue.THINGS_SEARCH_DEFAULT_PAGE_SIZE.getPath());
    }

    @Override
    public int thingsSearchMaxPageSize() {
        return config.getInt(LimitsConfigValue.THINGS_SEARCH_MAX_PAGE_SIZE.getPath());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultLimitsConfig that = (DefaultLimitsConfig) o;
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
