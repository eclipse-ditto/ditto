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

package org.eclipse.ditto.services.connectivity.messaging.backoff;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

/**
 * Configuration for back-off strategies.
 */
public interface BackOffConfig {

    /**
     * Get the timeout configuration used for calculating the back-off timeouts.
     *
     * @return the timeout configuration.
     */
    TimeoutConfig getTimeoutConfig();

    /**
     * Returns the timeout that should be used when asking the BackOff actor e.g. if it is currently in back-off mode.
     *
     * @return timeout that should be used when asking the BackOff actor e.g. if it's in back-off mode.
     */
    Duration getAskTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code BackOffConfig}.
     */
    @AllParametersAndReturnValuesAreNonnullByDefault
    enum BackOffConfigValue implements KnownConfigValue {

        /**
         * See documentation on {@link BackOffConfig#getAskTimeout()} ()}.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(5L));

        private final String path;
        private final Object defaultValue;

        BackOffConfigValue(final String thePath, final Object theDefaultValue) {
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
