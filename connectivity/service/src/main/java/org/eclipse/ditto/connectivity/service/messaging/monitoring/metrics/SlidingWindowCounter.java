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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;

/**
 * Simple implementation of a sliding window using a map. Depending on the given parameters
 * {@code window} and {@code duration} this implementation holds counter for time slots of size {@code duration} to
 * fill the {@code window}.
 */
public final class SlidingWindowCounter {

    private final Clock clock;

    // There are two different windows (usually they are the same), which allow recording using a single window
    // (e.g. history of 1 day with a resolution of 1 minute) and generate multiple measurements from it
    // (e.g. last 1 minute, 1 hour, 1 day).
    private final MeasurementWindow[] windowsForRecording;
    private final MeasurementWindow[] windowsForReporting;

    private final ConcurrentMap<Long, Long> successMeasurements = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> failureMeasurements = new ConcurrentHashMap<>();

    private final AtomicLong lastSuccessTimestamp = new AtomicLong(Instant.EPOCH.toEpochMilli());
    private final AtomicLong lastFailureTimestamp = new AtomicLong(Instant.EPOCH.toEpochMilli());
    private final Duration minResolution;
    private final Counter metricsCounter;
    @Nullable private final MetricsAlert metricsAlert;
    private final long maximumPerSlot;
    private final boolean cleanUpEnabled;

    // allows to override the reported value with a fixed value by checking the last modified timestamp instead of
    // calculating from the measurement maps (allows more accuracy for the shortest window)
    private final Map<MeasurementWindow, Long> lastTimestampOverrides;

    private SlidingWindowCounter(final SlidingWindowCounterBuilder builder) {
        metricsCounter = builder.metricsCounter;
        clock = builder.clock;
        metricsAlert = builder.metricsAlert;
        cleanUpEnabled = builder.cleanUpEnabled;
        windowsForRecording = builder.recordingMeasurementWindows;
        windowsForReporting = builder.reportingMeasurementWindows;
        maximumPerSlot = builder.maximumPerSlot;
        lastTimestampOverrides = builder.lastTimestampOverrides;

        minResolution = Stream.of(windowsForRecording)
                .map(MeasurementWindow::getResolution)
                .min(Duration::compareTo)
                .orElse(Duration.ofMinutes(5));
    }

    /**
     * @param metricsCounter the metricsCounter to use
     * @return a new SlidingWindowCounterBuilder instance
     */
    static SlidingWindowCounterBuilder newBuilder(final Counter metricsCounter) {
        return new SlidingWindowCounterBuilder(metricsCounter);
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
     * Increment success counter with current timestamp.
     */
    void increment() {
        increment(true);
    }

    /**
     * Increment counter with current timestamp.
     *
     * @param success whether to increment success or failure count
     */
    void increment(final boolean success) {
        increment(success, clock.instant().toEpochMilli());
    }

    /**
     * Increment this counter.
     *
     * @param success whether to increment success or failure count
     * @param ts the timestamp when the operation happened (mostly useful for testing)
     */
    void increment(final boolean success, final long ts) {
        final long previousTimestamp;
        if (success) {
            metricsCounter.tag("success", true).increment();
            previousTimestamp = updateTimestampAndReturnPrevious(lastSuccessTimestamp, ts);
            incrementMeasurements(ts, successMeasurements);
        } else {
            metricsCounter.tag("success", false).increment();
            previousTimestamp = updateTimestampAndReturnPrevious(lastFailureTimestamp, ts);
            incrementMeasurements(ts, failureMeasurements);
        }

        // if diff between current and last timestamp is too large, cleanup old measurements
        if (cleanUpEnabled && previousTimestamp > ts - minResolution.toMillis()) {
            cleanUpOldMeasurements();
        }
    }

    private long updateTimestampAndReturnPrevious(final AtomicLong toUpdate, final long ts) {
        return toUpdate.getAndUpdate(previous -> Math.max(previous, ts));
    }

    private void incrementMeasurements(final long ts, final Map<Long, Long> measurements) {
        for (final MeasurementWindow window : windowsForRecording) {
            final long slot = getSlot(ts, window.getResolution().toMillis());
            final long newValue = measurements.compute(slot, (key, value) -> (value == null) ? 1 : value + 1);
            if (metricsAlert != null && metricsAlert.evaluateCondition(window, slot, newValue)) {
                metricsAlert.triggerAction(ts, newValue);
            }
        }
    }

    private void cleanUpOldMeasurements() {
        cleanUpOldMeasurements(successMeasurements);
        cleanUpOldMeasurements(failureMeasurements);
    }

    private void cleanUpOldMeasurements(final Map<Long, Long> measurements) {
        measurements.entrySet().removeIf(e -> isOld(e.getKey()));
    }

    private boolean isOld(final long slot) {
        final long now = clock.instant().toEpochMilli();
        for (final MeasurementWindow window : windowsForRecording) {
            final long resolutionInMs = window.getResolution().toMillis();
            final long windowInMs = window.getWindow().toMillis();
            // max slot is the current slot for this window
            final long max = getSlot(now, resolutionInMs);
            // min slot is current slot minus window size
            final long min = getSlot(now - windowInMs, resolutionInMs);
            if (slot <= max && slot >= min) {
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
        if (success) {
            return getCounts(successMeasurements, lastSuccessTimestamp.get());
        }
        return getCounts(failureMeasurements, lastFailureTimestamp.get());
    }

    /**
     * Gets counts for all measurement windows given.
     *
     * @param measurements the measurements map to use
     * @return the counts for all windows
     */
    private Map<Duration, Long> getCounts(final Map<Long, Long> measurements,
            final long lastTimestamp) {
        final Map<Duration, Long> result = new HashMap<>();
        final long now = clock.instant().toEpochMilli();
        for (final MeasurementWindow window : windowsForReporting) {
            long sum = 0;
            if (lastTimestampOverrides.containsKey(window) && now - window.getWindow().toMillis() < lastTimestamp) {
                sum = lastTimestampOverrides.get(window);
            } else {
                // min is where we start to sum up the slots
                final long windowInMs = window.getWindow().toMillis();
                final long resolutionInMs = window.getResolution().toMillis();
                final long min = getSlot(now - windowInMs, resolutionInMs);
                // max is the current active time slot
                final long max = getSlot(now, resolutionInMs);
                for (final Map.Entry<Long, Long> e : measurements.entrySet()) {
                    final long slot = e.getKey();
                    if (slot > min && slot <= max) {
                        sum += Math.min(maximumPerSlot, e.getValue());
                    }
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
        reset(successMeasurements);
        reset(failureMeasurements);
    }

    private void reset(final Map<Long, Long> measurements) {
        measurements.clear();
    }

    private long getSlot(final long ts, final long resolutionInMs) {
        return ts / resolutionInMs;
    }

    /**
     * Builder of SlidingWindowCounters.
     */
    static final class SlidingWindowCounterBuilder {

        private final Counter metricsCounter;
        private Clock clock = Clock.systemUTC();
        private MetricsAlert metricsAlert = null;
        private boolean cleanUpEnabled = true;
        private MeasurementWindow[] recordingMeasurementWindows;
        private MeasurementWindow[] reportingMeasurementWindows;
        private long maximumPerSlot = Long.MAX_VALUE;
        private final Map<MeasurementWindow, Long> lastTimestampOverrides = new EnumMap<>(MeasurementWindow.class);

        private SlidingWindowCounterBuilder(final Counter metricsCounter) {
            this.metricsCounter = metricsCounter;
        }

        SlidingWindowCounterBuilder clock(final Clock clock) {
            this.clock = clock;
            return this;
        }

        SlidingWindowCounterBuilder metricsAlert(@Nullable final MetricsAlert metricsAlert) {
            this.metricsAlert = metricsAlert;
            return this;
        }

        SlidingWindowCounterBuilder cleanUpEnabled(final boolean cleanUpEnabled) {
            this.cleanUpEnabled = cleanUpEnabled;
            return this;
        }

        SlidingWindowCounterBuilder recordingMeasurementWindows(
                final MeasurementWindow... recordingMeasurementWindows) {
            this.recordingMeasurementWindows = recordingMeasurementWindows;
            return this;
        }

        SlidingWindowCounterBuilder reportingMeasurementWindows(
                final MeasurementWindow... reportingMeasurementWindows) {
            this.reportingMeasurementWindows = reportingMeasurementWindows;
            return this;
        }

        SlidingWindowCounterBuilder measurementWindows(
                final MeasurementWindow... measurementWindows) {
            this.reportingMeasurementWindows = measurementWindows;
            this.recordingMeasurementWindows = measurementWindows;
            return this;
        }

        SlidingWindowCounterBuilder maximumPerSlot(final long maximumPerSlot) {
            this.maximumPerSlot = maximumPerSlot;
            return this;
        }

        SlidingWindowCounterBuilder useLastTimestampForWindow(final MeasurementWindow window,
                final Long fixedValue) {
            this.lastTimestampOverrides.put(window, fixedValue);
            return this;
        }

        SlidingWindowCounter build() {
            return new SlidingWindowCounter(this);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "clock=" + clock +
                ", windowsForRecording=" + Arrays.toString(windowsForRecording) +
                ", windowsForReporting=" + Arrays.toString(windowsForReporting) +
                ", successMeasurements=" + successMeasurements +
                ", failureMeasurements=" + failureMeasurements +
                ", lastSuccessTimestamp=" + lastSuccessTimestamp +
                ", lastFailureTimestamp=" + lastFailureTimestamp +
                ", minResolution=" + minResolution +
                ", metricsCounter=" + metricsCounter +
                ", metricsAlert=" + metricsAlert +
                ", maximumPerSlot=" + maximumPerSlot +
                ", cleanUpEnabled=" + cleanUpEnabled +
                ", lastTimestampOverrides=" + lastTimestampOverrides +
                "]";
    }
}
