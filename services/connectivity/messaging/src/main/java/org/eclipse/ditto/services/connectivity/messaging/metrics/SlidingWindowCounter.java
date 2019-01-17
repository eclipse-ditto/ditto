/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Simple implementation of a sliding window using a map. Depending on the given parameters
 * {@code window} and {@code duration} this implementation holds counter for time slots of size {@code duration} to
 * fill the {@code window}.
 */
public final class SlidingWindowCounter {

    private final Clock clock;
    private final MeasurementWindow[] windows;
    private final ConcurrentMap<Long, Long> measurements = new ConcurrentHashMap<>();

    private final AtomicLong lastSuccessTimestamp = new AtomicLong(Instant.EPOCH.toEpochMilli());
    private final AtomicLong lastFailureTimestamp = new AtomicLong(Instant.EPOCH.toEpochMilli());
    private final Duration minResolution;

    /**
     * Instantiates a new {@link SlidingWindowCounter} that records the measurements for the given time windows.
     *
     * @param windows the time windows to record
     */
    SlidingWindowCounter(final Clock clock, final MeasurementWindow... windows) {
        this.clock = clock;
        this.windows = windows;

        minResolution = Stream.of(windows)
                .map(MeasurementWindow::getResolution)
                .min(Duration::compareTo)
                .orElse(Duration.ofMinutes(5));
    }

    /**
     * @return the timestamp of the last {@code success} measurement
     * (initialized to {@code EPOCH} if no measurement was yet processed)
     */
    long getLastSuccessMeasurementAt() {
        return lastSuccessTimestamp.get();
    }

    /**
     * @return the timestamp of the last {@code success} measurement
     * (initialized to {@code EPOCH} if no measurement was yet processed)
     */
    long getLastFailureMeasurementAt() {
        return lastFailureTimestamp.get();
    }

    /**
     * Increment this counter.
     *
     * @param success whether to increment success or failure count
     * @param ts the timestamp when the operation happened (mostly useful for testing)
     */
    void increment(final boolean success, final long ts) {
        final long theTimestamp;
        if (success) {
            theTimestamp = this.lastSuccessTimestamp.getAndUpdate(previous -> Math.max(previous, ts));
        } else {
            theTimestamp = this.lastFailureTimestamp.getAndUpdate(previous -> Math.max(previous, ts));
        }


        for (final MeasurementWindow window : windows) {
            final long slot = getSlot(ts, window.getResolution().toMillis());
            // store failure in negative space
            if (success) {
                measurements.compute(slot, (key, value) -> (value == null) ? 1 : value + 1);
            } else {
                measurements.compute(-slot, (key, value) -> (value == null) ? 1 : value + 1);
            }
        }

        // if diff between current and last timestamp is too large, cleanup old measurements
        if (theTimestamp > ts - minResolution.toMillis()) {
            cleanUp();
        }
    }

    /**
     * Increment counter with current timestamp.
     * @param success whether to increment success or failure count
     */
    void increment(final boolean success) {
        increment(success, clock.instant().toEpochMilli());
    }

    /**
     * Increment success counter with current timestamp.
     */
    void increment() {
        increment(true, clock.instant().toEpochMilli());
    }

    private long getSlot(long ts, final long resolutionInMs) {
        return ts / resolutionInMs;
    }

    private void cleanUp() {
        measurements.entrySet().removeIf(e -> isOld(e.getKey()));
    }

    private boolean isOld(long slot) {
        final long now = clock.instant().toEpochMilli();
        for (final MeasurementWindow window : windows) {
            final long resolutionInMs = window.getResolution().toMillis();
            final long windowInMs = window.getWindow().toMillis();
            // max slot is the current slot for this window
            final long max = getSlot(now, resolutionInMs);
            // min slot is current slot minus window size
            final long min = getSlot(now - windowInMs, resolutionInMs);
            // we check for the absolute value because we store failures in negative timestamps
            final long abs = Math.abs(slot);
            if (abs <= max && abs >= min) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets counts for all measurement windows given.
     *
     * @param success whether to increment success or failure count
     * @return the counts for all windows
     */
    Map<Duration, Long> getCounts(final boolean success) {
        final Map<Duration, Long> result = new HashMap<>();
        final long now = clock.instant().toEpochMilli();
        for (final MeasurementWindow window : windows) {
            // min is where we start to sum up the slots
            final long windowInMs = window.getWindow().toMillis();
            final long resolutionInMs = window.getResolution().toMillis();
            final long min = getSlot(now - windowInMs, resolutionInMs);
            // max is the current active time slot
            final long max = getSlot(now, resolutionInMs);
            long sum = 0;
            for (final Map.Entry<Long, Long> e : measurements.entrySet()) {
                long slot = success ? e.getKey() : -e.getKey();
                if (slot > min && slot <= max) {
                    sum += e.getValue();
                }
            }
            result.put(window.getWindow(), sum);
        }
        return result;
    }


    /**
     * Reset all counts.
     */
    void reset() {
        measurements.clear();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "measurements=" + measurements +
                ", lastSuccessTimestamp=" + lastSuccessTimestamp +
                ", lastFailureTimestamp=" + lastFailureTimestamp +
                "]";
    }
}
