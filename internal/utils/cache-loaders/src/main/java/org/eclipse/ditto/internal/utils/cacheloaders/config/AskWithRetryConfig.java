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
package org.eclipse.ditto.internal.utils.cacheloaders.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the {@link org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry} pattern.
 */
@Immutable
public interface AskWithRetryConfig {

    /**
     * Returns the duration to wait for a response before treating it as timed out after asking with
     * {@code AskWithRetry}.
     *
     * @return the internal ask timeout duration.
     */
    Duration getAskTimeout();

    /**
     * Returns the strategy to apply for calculating the retry delay.
     *
     * @return the retry strategy.
     */
    RetryStrategy getRetryStrategy();

    /**
     * Returns the amount of retry attempts which should be done on an occurred ask timeout after
     * {@link #getAskTimeout()} was reached.
     *
     * @return the amount of retries to do when encountering an "ask timeout" during asking with {@code AskWithRetry}.
     */
    int getRetryAttempts();

    /**
     * Gets a fixed delay after which the retry attempts should be done when encountering "ask timeouts".
     *
     * @return the fixed delay after which to do retries.
     */
    Duration getFixedDelay();

    /**
     * Gets the min backoff duration after which a retry attempt should be done when encountering "ask timeouts".
     * This is the shortest delay (the 2nd retry) to expect between retries.
     *
     * @return the min backoff delay after which to do retries.
     */
    Duration getBackoffDelayMin();

    /**
     * Gets the max backoff duration after which a retry attempt should be done when encountering "ask timeouts".
     * This is the longest delay to expect between retries.
     *
     * @return the max backoff delay after which to do retries.
     */
    Duration getBackoffDelayMax();

    /**
     * Gets the random factor to apply when using {@link #getBackoffDelayMin()} and {@link #getBackoffDelayMax()} for
     * calculating the next backoff delay. Must be between {@code 0.0} and {@code 1.0}.
     *
     * @return a random factor to apply for backoff delays.
     */
    double getBackoffDelayRandomFactor();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code AskWithRetryConfig}.
     */
    enum AskWithRetryConfigValue implements KnownConfigValue {

        /**
         * The duration to wait for a response before treating it as timed out.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(3)),

        /**
         * The {@link RetryStrategy} to apply for calculating the retry delay.
         */
        RETRY_STRATEGY("retry-strategy", RetryStrategy.OFF.name()),

        /**
         * The amount of retry attempts which should be done on an occurred ask timeout.
         * {@code 0} deactivates retries.
         */
        RETRY_ATTEMPTS("retry-attempts", 3),

        /**
         * A fixed delay after which the retry attempts should be done when encountering "ask timeouts".
         */
        FIXED_DELAY("fixed-delay", Duration.ofSeconds(5)),

        /**
         * The min backoff duration after which a retry attempt should be done when encountering "ask timeouts".
         * This is the shortest delay (the 2nd retry) to expect between retries.
         */
        BACKOFF_DELAY_MIN("backoff-delay.min", Duration.ofSeconds(1)),

        /**
         * The max backoff duration after which a retry attempt should be done when encountering "ask timeouts".
         * This is the longest delay to expect between retries.
         */
        BACKOFF_DELAY_MAX("backoff-delay.max", Duration.ofSeconds(10)),

        /**
         * The random factor to apply when calculating the next backoff delay, must be between
         * {@code 0.0} and {@code 1.0}.
         */
        BACKOFF_DELAY_RANDOM_FACTOR("backoff-delay.random-factor", 0.5);

        private final String path;
        private final Object defaultValue;

        AskWithRetryConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
