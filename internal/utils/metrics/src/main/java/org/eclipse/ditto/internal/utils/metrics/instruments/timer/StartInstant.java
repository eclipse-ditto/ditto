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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

import kamon.Kamon;
import kamon.util.Clock;

/**
 * High precision representation of the instant of the start of an action.
 */
@Immutable
public final class StartInstant implements Comparable<StartInstant> {

    private static final Clock KAMON_CLOCK = Kamon.clock();

    private final long nanos;

    private StartInstant(final long nanos) {
        this.nanos = nanos;
    }

    /**
     * Returns a new instance of {@code StartInstant} for the current system nano time.
     *
     * @return the instance.
     */
    public static StartInstant now() {
        return new StartInstant(System.nanoTime());
    }

    /**
     * Returns the {@code Instant} representation of this start instant.
     *
     * @return the Instant.
     */
    public Instant toInstant() {
        return KAMON_CLOCK.toInstant(nanos);
    }

    /**
     * Returns the nanosecond representation of this start instant.
     *
     * @return the nanoseconds.
     */
    public long toNanos() {
        return nanos;
    }

    @Override
    public int compareTo(final StartInstant o) {
        ConditionChecker.checkNotNull(o, "o");
        return Long.compare(nanos, o.nanos);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        final boolean result;
        if (this == o) {
            result = true;
        } else if (o instanceof StartInstant that) {
            result = nanos == that.nanos;
        } else {
            result = false;
        }
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nanos);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "nanos=" + nanos +
                "]";
    }

}
