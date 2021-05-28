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

package org.eclipse.ditto.connectivity.service.messaging.backoff;

import java.time.Duration;

import org.eclipse.ditto.connectivity.service.config.TimeoutConfig;

/**
 * Retry timeout strategy that provides increasing timeouts for retrying to connect.
 */
public interface RetryTimeoutStrategy {

    /**
     * Creates a new RetryTimeoutStrategy duplicating the {@link #getNextTimeout()} until the configured max timeout
     * of the passed {@code timeoutConfig} is reached with the formula: {@code timeout = minTimeout * 2^x}
     *
     * @param timeoutConfig the timeout config to apply.
     * @return the created RetryTimeoutStrategy
     * @since 2.0.0
     */
    static RetryTimeoutStrategy newDuplicationRetryTimeoutStrategy(final TimeoutConfig timeoutConfig) {
        return DuplicationRetryTimeoutStrategy.fromConfig(timeoutConfig);
    }

    /**
     * Resets the timeout and the current tries.
     */
    void reset();

    /**
     * Calculate the next timeout.
     *
     * @return the next timeout to use according to the retry strategy.
     */
    Duration getNextTimeout();

    /**
     * Get how often {@link #getNextTimeout()} was called.
     *
     * @return how often a timeout was requested.
     */
    int getCurrentTries();

}
