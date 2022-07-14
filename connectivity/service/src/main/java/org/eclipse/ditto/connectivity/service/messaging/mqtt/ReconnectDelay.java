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
 * Delay how long to wait before reconnecting an MQTT client.
 */
@Immutable
public final class ReconnectDelay implements Comparable<ReconnectDelay> {

    /**
     * The lower boundary a {@code ReconnectDelay} a never falls below.
     */
    static final Duration LOWER_BOUNDARY = Duration.ofSeconds(1L);

    private final Duration duration;

    private ReconnectDelay(final Duration duration) {
        this.duration = duration;
    }

    /**
     * Returns an instance of {@code ReconnectDelay} for the specified {@code Duration} argument or
     * {@link #LOWER_BOUNDARY} if the argument is less than the lower boundary.
     *
     * @param duration the duration of the returned {@code ReconnectDelay}.
     * @return the instance.
     * @throws NullPointerException if {@code duration} is {@code null}.
     */
    static ReconnectDelay ofOrLowerBoundary(final Duration duration) {
        final Duration durationWithinLowerBoundary;
        if (0 < LOWER_BOUNDARY.compareTo(ConditionChecker.checkNotNull(duration, "duration"))) {
            durationWithinLowerBoundary = LOWER_BOUNDARY;
        } else {
            durationWithinLowerBoundary = duration;
        }
        return new ReconnectDelay(durationWithinLowerBoundary);
    }

    public Duration getDuration() {
        return duration;
    }

    @Override
    public int compareTo(final ReconnectDelay o) {
        ConditionChecker.checkNotNull(o, "o");
        return duration.compareTo(o.getDuration());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ReconnectDelay) o;
        return Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration);
    }

    /**
     * @return the string representation of the wrapped {@code Duration}.
     */
    @Override
    public String toString() {
        return duration.toString();
    }

}
