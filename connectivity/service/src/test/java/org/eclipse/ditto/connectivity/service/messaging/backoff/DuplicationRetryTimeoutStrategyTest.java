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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.connectivity.service.config.TimeoutConfig;
import org.junit.Test;


/**
 * Unit test for {@link DuplicationRetryTimeoutStrategy}.
 */
public final class DuplicationRetryTimeoutStrategyTest {

    private static final Duration MIN_TIMEOUT = Duration.ofMillis(2L);
    private static final Duration MAX_TIMEOUT = Duration.ofMillis(30L);

    @Test
    public void timeoutIncreasesByDouble() {
        final DuplicationRetryTimeoutStrategy strategy =
                DuplicationRetryTimeoutStrategy.fromConfig(createTimeoutConfig());
        Duration expectedCurrentTimeout = MIN_TIMEOUT;
        do {
            assertThat(strategy.getNextTimeout()).isEqualTo(expectedCurrentTimeout);
            expectedCurrentTimeout = expectedCurrentTimeout.multipliedBy(2L);
        } while (expectedCurrentTimeout.minus(MAX_TIMEOUT).isNegative());

    }

    @Test
    public void timeoutUsesMaximum() {
        final DuplicationRetryTimeoutStrategy strategy =
                DuplicationRetryTimeoutStrategy.fromConfig(createTimeoutConfig());
        while (strategy.getNextTimeout().minus(MAX_TIMEOUT).isNegative()) {
            // just to get to the point where it should be max
        }

        assertThat(strategy.getNextTimeout()).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void triesAreCounted() {
        final int iterations = 17;
        final DuplicationRetryTimeoutStrategy strategy =
                DuplicationRetryTimeoutStrategy.fromConfig(createTimeoutConfig());
        for (int i = 0; i < iterations; ++i) {
            assertThat(strategy.getCurrentTries()).isEqualTo(i);
            strategy.getNextTimeout();
        }
    }

    @Test
    public void resetWillResetTimeoutAndTries() {
        final int iterations = 17;
        final DuplicationRetryTimeoutStrategy strategy =
                DuplicationRetryTimeoutStrategy.fromConfig(createTimeoutConfig());
        for (int i = 0; i < iterations; ++i) {
            strategy.getNextTimeout();
        }

        final Duration timeoutBeforeReset = strategy.getNextTimeout();
        final int triesBeforeReset = strategy.getCurrentTries();

        strategy.reset();

        assertThat(strategy.getCurrentTries()).isLessThan(triesBeforeReset);
        assertThat(strategy.getNextTimeout()).isLessThan(timeoutBeforeReset);
    }

    private static TimeoutConfig createTimeoutConfig() {
        return new TimeoutConfig() {
            @Override
            public Duration getMinTimeout() {
                return MIN_TIMEOUT;
            }

            @Override
            public Duration getMaxTimeout() {
                return MAX_TIMEOUT;
            }
        };
    }

}
