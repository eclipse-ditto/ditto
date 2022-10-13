/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * The keep-alive interval for a HiveMQ MQTT client.
 * This interval is the number of seconds that the broker permits between when a client finishes sending one MQTT
 * packet and starts to send the next
 */
@Immutable
public final class KeepAliveInterval {

    static final int MIN_INTERVAL_SECONDS = 0;
    static final int MAX_INTERVAL_SECONDS = 65_535;
    static final int DEFAULT_INTERVAL_SECONDS = 60;

    private final int intervalSeconds;

    private KeepAliveInterval(final int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * Returns an instance of {@code KeepAliveInterval} for the specified {@code Duration} interval.
     *
     * @param duration the duration of the keep alive interval. The seconds of this duration must be between
     * {@value #MIN_INTERVAL_SECONDS} and {@value #MAX_INTERVAL_SECONDS} (both inclusive).
     * @return the KeepAliveInterval for {@code duration}.
     * @throws NullPointerException if {@code duration} is {@code null}.
     * @throws IllegalKeepAliveIntervalSecondsException if the seconds of {@code duration} is less than
     * {@value MIN_INTERVAL_SECONDS} or greater than {@value #MAX_INTERVAL_SECONDS}.
     */
    public static KeepAliveInterval of(final Duration duration) throws IllegalKeepAliveIntervalSecondsException {
        ConditionChecker.checkNotNull(duration, "duration");
        final var durationSeconds = duration.getSeconds();
        if (durationSeconds < MIN_INTERVAL_SECONDS || durationSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalKeepAliveIntervalSecondsException(
                    String.format("Expected seconds of duration to be within [%d, %d] but it was <%d>.",
                            MIN_INTERVAL_SECONDS,
                            MAX_INTERVAL_SECONDS,
                            durationSeconds),
                    null);
        } else {
            return new KeepAliveInterval((int) durationSeconds);
        }
    }

    /**
     * Returns an instance of {@code KeepAliveInterval} with zero seconds.
     *
     * @return the zero keep alive interval.
     */
    public static KeepAliveInterval zero() {
        return new KeepAliveInterval(MIN_INTERVAL_SECONDS);
    }

    /**
     * Returns an instance of {@code KeepAliveInterval} with the default of {@value #DEFAULT_INTERVAL_SECONDS} seconds.
     * This is the default value that is specified by HiveMQ Client API version 3 and 5.
     *
     * @return the default keep alive.
     */
    public static KeepAliveInterval defaultKeepAlive() {
        return new KeepAliveInterval(DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Returns the seconds of this keep alive interval.
     *
     * @return the seconds of this keep alive interval.
     */
    public int getSeconds() {
        return intervalSeconds;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (KeepAliveInterval) o;
        return intervalSeconds == that.intervalSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervalSeconds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "intervalSeconds=" + intervalSeconds +
                "]";
    }

}
