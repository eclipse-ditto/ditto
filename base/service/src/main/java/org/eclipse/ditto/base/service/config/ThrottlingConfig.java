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
package org.eclipse.ditto.base.service.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides configuration settings for throttling based on an {@code interval} and a {@code limit}.
 */
@Immutable
public interface ThrottlingConfig {

    /**
     * Expected path of this config relative to its parent.
     */
    String CONFIG_PATH = "throttling";

    /**
     * Returns whether throttling should be enabled or not.
     *
     * @return whether throttling should be enabled or not.
     */
    boolean isEnabled();

    /**
     * Returns the throttling interval meaning in which duration may the configured
     * {@link #getLimit() limit} be processed before throttling further messages.
     *
     * @return the consumer throttling interval.
     */
    Duration getInterval();

    /**
     * Returns the throttling limit defining processed messages per configured
     * {@link #getInterval() interval}.
     *
     * @return the consumer throttling limit.
     */
    int getLimit();

    /**
     * Render this object into a Config object from which a copy of this object can be constructed.
     *
     * @return the config representation.
     */
    default Config render() {
        final Map<String, Object> map = new HashMap<>();
        map.put(ConfigValue.ENABLED.getConfigPath(), isEnabled());
        map.put(ConfigValue.INTERVAL.getConfigPath(), getInterval().toMillis() + "ms");
        map.put(ConfigValue.LIMIT.getConfigPath(), getLimit());
        return ConfigFactory.parseMap(map).atKey(CONFIG_PATH);
    }

    /**
     * Returns an instance of {@code ThrottlingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static ThrottlingConfig of(final Config config) {
        return DefaultThrottlingConfig.of(config);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ThrottlingConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether throttling should be enabled.
         */
        ENABLED("enabled", false),

        /**
         * The throttling interval meaning in which duration may the configured
         * {@link #LIMIT limit} be processed before throttling further messages.
         */
        INTERVAL("interval", Duration.ofSeconds(1)),

        /**
         * The throttling limit defining processed messages per configured
         * {@link #INTERVAL interval}.
         */
        LIMIT("limit", 100);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
