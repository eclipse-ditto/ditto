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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the exponential back-off strategy.
 */
@Immutable
public interface ExponentialBackOffConfig {

    /**
     * Returns the minimal exponential back-off duration.
     *
     * @return the min duration.
     */
    Duration getMin();

    /**
     * Returns the maximal exponential back-off duration.
     *
     * @return the max duration.
     */
    Duration getMax();

    /**
     * Returns the random factor of the exponential back-off strategy.
     *
     * @return the random factor.
     */
    double getRandomFactor();

    /**
     * Returns the maximum idle time before a corrupted supervisor terminates.
     *
     * @return the maximum idle time in corrupted state.
     */
    Duration getCorruptedReceiveTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ExponentialBackOffConfig}.
     */
    enum ExponentialBackOffConfigValue implements KnownConfigValue {

        /**
         * The minimal exponential back-off duration.
         */
        MIN("min", Duration.ofSeconds(1L)),

        /**
         * The maximal exponential back-off duration.
         */
        MAX("max", Duration.ofSeconds(10L)),

        /**
         * The random factor of the exponential back-off strategy.
         */
        RANDOM_FACTOR("random-factor", 1.0D),

        /**
         * How long to wait before stopping an idle corrupted supervisor actor.
         */
        CORRUPTED_RECEIVE_TIMEOUT("corrupted-receive-timeout", Duration.ofSeconds(600L));

        private final String path;
        private final Object defaultValue;

        private ExponentialBackOffConfigValue(final String thePath, final Object theDefaultValue) {
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
