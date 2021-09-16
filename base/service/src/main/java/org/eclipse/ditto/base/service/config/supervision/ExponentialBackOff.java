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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Responsible to calculate a restart delay with using exponential back off.
 */
public final class ExponentialBackOff {

    private final Instant lastRestart = Instant.now();
    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private final Duration restartDelay;
    private final Duration resetToMinThreshold;

    private ExponentialBackOff(final ExponentialBackOffConfig exponentialBackOffConfig) {
        resetToMinThreshold = exponentialBackOffConfig.getMax().multipliedBy(2);
        this.exponentialBackOffConfig = exponentialBackOffConfig;
        this.restartDelay = exponentialBackOffConfig.getMin();
    }

    private ExponentialBackOff(final ExponentialBackOffConfig exponentialBackOffConfig, final Duration restartDelay) {
        resetToMinThreshold = exponentialBackOffConfig.getMax().multipliedBy(2);
        this.exponentialBackOffConfig = exponentialBackOffConfig;
        this.restartDelay = restartDelay;
    }

    /**
     * Creates the first instance of {@link ExponentialBackOff} with a {@link #getRestartDelay() restart delay}
     * which is equal to {@link ExponentialBackOffConfig#getMin() the minimum duration} specified by the given
     * {@code exponentialBackOffConfig}.
     *
     * @param exponentialBackOffConfig the config to use.
     * @return the first back off.
     */
    public static ExponentialBackOff initial(final ExponentialBackOffConfig exponentialBackOffConfig) {
        return new ExponentialBackOff(exponentialBackOffConfig);
    }

    /**
     * Use this method at the time when the failure occurs. It will consider the last failure timestamp
     * when calculating the {@link #getRestartDelay() restart delay}.
     *
     * @return the new back off holding the current timestamp as last failure state and with a calculated restart delay.
     */
    public ExponentialBackOff calculateNextBackOff() {
        return new ExponentialBackOff(exponentialBackOffConfig, calculateRestartDelay());
    }

    /**
     * @return the restart delay.
     */
    public Duration getRestartDelay() {
        return restartDelay;
    }

    private Duration calculateRestartDelay() {
        final Duration minBackOff = exponentialBackOffConfig.getMin();
        final Duration maxBackOff = exponentialBackOffConfig.getMax();
        final Instant now = Instant.now();
        final Duration sinceLastError = Duration.between(lastRestart, now);
        if (resetToMinThreshold.compareTo(sinceLastError) <= 0) {
            // no restart if time since last error exceed backoff threshold; reset to minBackOff.
            return minBackOff;
        } else {
            // increase delay.
            final double randomFactor = exponentialBackOffConfig.getRandomFactor();
            return calculateNextBackOffDuration(minBackOff, restartDelay, maxBackOff, randomFactor);
        }
    }

    private static Duration calculateNextBackOffDuration(final Duration minBackOff, final Duration restartDelay,
            final Duration maxBackOff, final double randomFactor) {
        final Duration nextBackoff = restartDelay.plus(randomize(restartDelay, randomFactor));
        return boundDuration(minBackOff, nextBackoff, maxBackOff);
    }

    /**
     * Return a random duration between the base duration and {@code (1 + randomFactor)} times the base duration.
     *
     * @param base the base duration.
     * @param randomFactor the random factor.
     * @return the random duration.
     */
    private static Duration randomize(final Duration base, final double randomFactor) {
        final double multiplier = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor;
        return Duration.ofMillis((long) (base.toMillis() * multiplier));
    }

    private static Duration boundDuration(final Duration min, final Duration duration, final Duration max) {
        if (duration.minus(min).isNegative()) {
            return min;
        } else if (max.minus(duration).isNegative()) {
            return max;
        } else {
            return duration;
        }
    }

}
