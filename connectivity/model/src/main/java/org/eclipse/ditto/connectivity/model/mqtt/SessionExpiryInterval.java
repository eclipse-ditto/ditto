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
package org.eclipse.ditto.connectivity.model.mqtt;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Representation of the MQTT 5 client session expiry interval.
 * The minimum seconds of the interval is zero; this is also the default.
 * The maximum seconds is {@value MAX_INTERVAL_SECONDS} which means no session expiry at all.
 */
@Immutable
public final class SessionExpiryInterval {

    static final byte MIN_INTERVAL_SECONDS = 0;

    static final long MAX_INTERVAL_SECONDS = 4_294_967_295L;

    private final long expirySeconds;

    private SessionExpiryInterval(final long expirySeconds) {
        this.expirySeconds = expirySeconds;
    }

    /**
     * Returns an instance of the default {@code SessionExpiryInterval}.
     *
     * @return the default session expiry interval.
     */
    public static SessionExpiryInterval defaultSessionExpiryInterval() {
        return new SessionExpiryInterval(0L);
    }

    /**
     * Returns an instance of {@code SessionExpiryInterval} for the specified {@code Duration} interval.
     *
     * @param duration the duration of the session expiry interval. The seconds of this duration must be between
     * {@value #MIN_INTERVAL_SECONDS} and {@value #MAX_INTERVAL_SECONDS} (both inclusive).
     * @return the SessionExpiryInterval for {@code duration}.
     * @throws NullPointerException if {@code duration} is {@code null}.
     * @throws IllegalSessionExpiryIntervalSecondsException if the seconds of {@code duration} is less than
     * {@value MIN_INTERVAL_SECONDS} or greater than {@value #MAX_INTERVAL_SECONDS}.
     */
    public static SessionExpiryInterval of(final Duration duration)
            throws IllegalSessionExpiryIntervalSecondsException {

        ConditionChecker.checkNotNull(duration, "duration");
        final long durationSeconds = duration.getSeconds();
        if (durationSeconds < MIN_INTERVAL_SECONDS || durationSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalSessionExpiryIntervalSecondsException(
                    String.format("Expected seconds of duration to be within [%d, %d] but it was <%d>.",
                            MIN_INTERVAL_SECONDS,
                            MAX_INTERVAL_SECONDS,
                            durationSeconds),
                    null);
        } else {
            return new SessionExpiryInterval(durationSeconds);
        }
    }

    /**
     * Returns the seconds of this session expiry interval.
     *
     * @return the seconds of this session expiry interval.
     */
    public long getSeconds() {
        return expirySeconds;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SessionExpiryInterval that = (SessionExpiryInterval) o;
        return expirySeconds == that.expirySeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(expirySeconds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "expirySeconds=" + expirySeconds +
                "]";
    }

}
