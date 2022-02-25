/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the AMQP 1.0 consumer.
 */
@Immutable
public interface Amqp10ConsumerConfig {

    /**
     * Return when to forget messages for which redelivery was requested (they may be consumed by another consumer).
     *
     * @return the duration a redelivery request is kept.
     */
    Duration getRedeliveryExpectationTimeout();

    /**
     * Returns the throttling config.
     *
     * @return the config.
     */
    ConnectionThrottlingConfig getThrottlingConfig();

    /**
     * Returns an instance of {@code Amqp10ConsumerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static Amqp10ConsumerConfig of(final Config config) {
        return DefaultAmqp10ConsumerConfig.of(config);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp10ConsumerConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * When to forget messages for which redelivery was requested (they may be consumed by another consumer).
         */
        REDELIVERY_EXPECTATION_TIMEOUT("redelivery-expectation-timeout", Duration.ofMinutes(2L));

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
