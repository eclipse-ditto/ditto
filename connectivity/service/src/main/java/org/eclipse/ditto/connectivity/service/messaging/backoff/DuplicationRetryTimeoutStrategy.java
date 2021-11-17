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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;

import java.time.Duration;
import java.util.function.Predicate;

import org.eclipse.ditto.connectivity.service.config.TimeoutConfig;

/**
 * Implements {@code timeout = minTimeout * 2^x} until max timeout is reached.
 */
final class DuplicationRetryTimeoutStrategy implements RetryTimeoutStrategy {

    private static final Duration ZERO_MIN_DURATION_FIRST_INCREMENT = Duration.ofMillis(100);

    private final Duration minTimeout;
    private final Duration maxTimeout;
    private Duration currentTimeout;
    private int currentTries;

    private DuplicationRetryTimeoutStrategy(final Duration minTimeout, final Duration maxTimeout) {
        this.minTimeout = checkArgument(minTimeout, isPositiveOrZero().and(isLowerThanOrEqual(maxTimeout)),
                () -> "minTimeout must be positive and lower than or equal to maxTimeout");
        this.maxTimeout = checkArgument(maxTimeout, isPositiveOrZero(),
                () -> "maxTimeout must be positive");
        this.currentTimeout = minTimeout;
        this.currentTries = 0;
    }

    static DuplicationRetryTimeoutStrategy fromConfig(final TimeoutConfig config) {
        return new DuplicationRetryTimeoutStrategy(config.getMinTimeout(),
                config.getMaxTimeout());
    }

    @Override
    public void reset() {
        this.currentTimeout = this.minTimeout;
        this.currentTries = 0;
    }

    @Override
    public Duration getNextTimeout() {
        final Duration timeout = this.currentTimeout;
        this.increase();
        if (timeout.isZero()) {
            this.currentTimeout = ZERO_MIN_DURATION_FIRST_INCREMENT;
        }

        return timeout;
    }

    @Override
    public int getCurrentTries() {
        return this.currentTries;
    }

    private void increase() {
        this.currentTimeout = minDuration(this.maxTimeout, this.currentTimeout.multipliedBy(2L));
        ++currentTries;
    }

    private static Duration minDuration(final Duration d1, final Duration d2) {
        return isLonger(d1, d2) ? d2 : d1;
    }

    private static boolean isLonger(final Duration d1, final Duration d2) {
        return d2.minus(d1).isNegative();
    }

    private static Predicate<Duration> isLowerThanOrEqual(final Duration otherDuration) {
        return arg -> {
            final Duration minus = arg.minus(otherDuration);

            return minus.isNegative() || minus.isZero();
        };
    }

    private static Predicate<Duration> isPositiveOrZero() {
        return arg -> !arg.isNegative();
    }

}
