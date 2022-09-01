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
package org.eclipse.ditto.thingsearch.service.common.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of stream stages.
 */
@Immutable
public interface StreamStageConfig {

    /**
     * Returns the amount of maximum parallel operations to perform.
     *
     * @return the amount.
     */
    int getParallelism();

    /**
     * Returns the config for the back-offs in case of failure.
     *
     * @return the config.
     */
    ExponentialBackOffConfig getExponentialBackOffConfig();

    /**
     * An enumeration of known config path expressions and their associated default values for
     * {@code StreamStageConfig}.
     */
    enum StreamStageConfigValue implements KnownConfigValue {

        /**
         * Must be a power of two.
         */
        PARALLELISM("parallelism", 16);

        private final String configPath;
        private final Object defaultValue;

        StreamStageConfigValue(final String configPath, final Object defaultValue) {
            this.configPath = configPath;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return configPath;
        }

    }

}
