/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.akka.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Contains timestamps required for triggering a stream.
 */
public final class StreamTrigger {

    private final Instant plannedStreamStart;
    private final Instant queryStart;
    private final Instant queryEnd;

    private StreamTrigger(final Instant plannedStreamStart, final Instant queryStart, final Instant queryEnd) {
        this.plannedStreamStart = plannedStreamStart;
        this.queryStart = queryStart;
        this.queryEnd = queryEnd;
    }

    /**
     * Constructor.
     *
     * @param queryStart the minimum creation time of the entities to be queried
     * @param queryEnd the maximum creation time of the entities to be queried
     * @param plannedStreamStart the planned start of the stream: will normally be some time after {@code queryStart},
     * otherwise data might get lost due to clock drift etc.
     * @return the new trigger.
     */
    public static StreamTrigger of(final Instant queryStart, final Instant queryEnd, final Instant plannedStreamStart) {
        return new StreamTrigger(plannedStreamStart, queryStart, queryEnd);
    }

    /**
     * Returns the planned start of the stream.
     *
     * @return the planned start of the stream.
     */
    public Instant getPlannedStreamStart() {
        return plannedStreamStart;
    }

    /**
     * Returns the minimum creation time of the entities to be queried.
     *
     * @return the minimum creation time of the entities to be queried.
     */
    public Instant getQueryEnd() {
        return queryEnd;
    }

    /**
     * Returns the maximum creation time of the entities to be queried.
     *
     * @return the maximum creation time of the entities to be queried.
     */
    public Instant getQueryStart() {
        return queryStart;
    }

    /**
     * Reschedules this trigger at {@code newPlannedStreamStart}.
     *
     * @param newPlannedStreamStart the new planned stream start
     * @return a new instance of trigger with {@code newPlannedStreamStart}.
     */
    public StreamTrigger rescheduleAt(final Instant newPlannedStreamStart) {
        return of(queryStart, queryEnd, newPlannedStreamStart);
    }

    /**
     * Calculates a {@link StreamTrigger} based on the given parameters.
     *
     * @param now the current time
     * @param queryStart the minimum creation time of the entities to be queried.
     * @param startOffset the start-offset: the planned stream start ({@link #getPlannedStreamStart()} will be at
     * least this offset after {@code queryStart}. The actual start-offset may be bigger if {@code queryStart} is in
     * the past.
     * @param streamInterval the interval of the stream: the query end {@link #getQueryEnd()} is calculated by adding
     * this interval to the query start {@link #getQueryStart()}.
     * @return the trigger
     */
    public static StreamTrigger calculateStreamTrigger(final Instant now,
            final Instant queryStart,
            final Duration startOffset,
            final Duration streamInterval) {

        return calculateStreamTrigger(now, queryStart, startOffset, streamInterval, Duration.ZERO, false);
    }

    static StreamTrigger calculateStreamTrigger(final Instant now,
            final Instant queryStart,
            final Duration startOffset,
            final Duration streamInterval,
            final Duration minDelay,
            final boolean isFirstStreamTrigger) {

        final Instant nextSchedulableInstant = now.plus(minDelay);
        final Instant queryEnd = queryStart.plus(streamInterval.plus(minDelay));

        final Instant plannedStreamStart;
        if (isFirstStreamTrigger) {
            plannedStreamStart = nextSchedulableInstant.plus(streamInterval);
        } else {
            final Instant optimalStreamStart = queryEnd.plus(startOffset);
            if (nextSchedulableInstant.isAfter(optimalStreamStart)) {
                plannedStreamStart = nextSchedulableInstant;
            } else {
                plannedStreamStart = optimalStreamStart;
            }
        }

        return of(queryStart, queryEnd, plannedStreamStart);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StreamTrigger that = (StreamTrigger) o;
        return Objects.equals(plannedStreamStart, that.plannedStreamStart) &&
                Objects.equals(queryStart, that.queryStart) &&
                Objects.equals(queryEnd, that.queryEnd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plannedStreamStart, queryStart, queryEnd);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "plannedStreamStart=" + plannedStreamStart +
                ", queryStart=" + queryStart +
                ", queryEnd=" + queryEnd +
                ']';
    }
}
