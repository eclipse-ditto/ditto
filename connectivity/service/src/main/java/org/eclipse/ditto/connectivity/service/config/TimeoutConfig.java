/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

/**
 * A config containing timeouts that can be used by algorithms to provide a timeout strategy.
 */
public interface TimeoutConfig {

    /**
     * A minimum timeout.
     *
     * @return the minimum timeout.
     */
    Duration getMinTimeout();

    /**
     * A maximum timeout. Should be greater than {@link TimeoutConfig#getMinTimeout()}.
     *
     * @return the maximum timeout.
     * @see TimeoutConfig#getMinTimeout()
     */
    Duration getMaxTimeout();


    /**
     * An enumeration of the known config path expressions and their associated default values for {@code
     * TimeoutConfig}.
     */
    @AllParametersAndReturnValuesAreNonnullByDefault
    enum TimeoutConfigValue implements KnownConfigValue {

        /**
         * See documentation on {@link TimeoutConfig#getMinTimeout()}.
         */
        MIN_TIMEOUT("min-timeout", Duration.ofSeconds(1L)),

        /**
         * See documentation on {@link TimeoutConfig#getMaxTimeout()}.
         */
        MAX_TIMEOUT("max-timeout", Duration.ofSeconds(600L));

        private final String path;
        private final Object defaultValue;

        TimeoutConfigValue(final String thePath, final Object theDefaultValue) {
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
