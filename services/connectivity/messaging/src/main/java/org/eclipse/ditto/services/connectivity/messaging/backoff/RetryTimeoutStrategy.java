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

/**
 * Retry timeout strategy that provides increasing timeouts for retrying to connect.
 */
interface RetryTimeoutStrategy {

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
