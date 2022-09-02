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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_DAY_WITH_ONE_HOUR_RESOLUTION;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests {@link SlidingWindowCounter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class SlidingWindowCounterTest {

    @Mock
    public Counter metricsCounter;

    @Before
    public void setup() {
        when(metricsCounter.tag(eq("success"), any(Boolean.class))).thenReturn(metricsCounter);
    }

    @Test
    public void testLastMeasurementAt() {
        final SlidingWindowCounter counter =
                SlidingWindowCounter.newBuilder(metricsCounter)
                        .measurementWindows(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION)
                        .build();
        final long ts = System.currentTimeMillis();
        counter.increment(true, ts);
        assertThat(counter.getLastSuccessMeasurementAt()).isEqualTo(ts);
    }

    @Test
    public void testOneMeasurementEveryMs() {
        final SlidingWindowCounter counter =
                SlidingWindowCounter.newBuilder(metricsCounter)
                        .measurementWindows(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION,
                                ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION)
                        .build();

        final long ts = System.currentTimeMillis() +
                ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow().toMillis();
        final long start = ts
                - ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow().toMillis()
                - ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getResolution().toMillis();

        // add one success measurement every second
        for (long i = start; i <= ts; i += 1000) {
            counter.increment(true, i);
        }
        // add one failure measurement every 5 seconds
        for (long i = start; i <= ts; i += 5000) {
            counter.increment(false, i);
        }

        final Map<Duration, Long> success = counter.getCounts(true);
        final Map<Duration, Long> failure = counter.getCounts(false);

        assertThat(success)
                .containsEntry(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow(), 60L)
                .containsEntry(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow(), 3600L);
        assertThat(failure)
                .containsEntry(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow(), 12L)
                .containsEntry(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow(), 720L);

    }

    @Test
    public void testCounterWritingToCustomMeasurementWindows() {

        final SlidingWindowCounter counter = SlidingWindowCounter.newBuilder(metricsCounter)
                .recordingMeasurementWindows(ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                .reportingMeasurementWindows(ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION, ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
                        ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                .cleanUpEnabled(false)
                .build();

        final long ts = System.currentTimeMillis() +
                ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION.getWindow().toMillis();
        final long start = ts
                - ONE_DAY_WITH_ONE_MINUTE_RESOLUTION.getWindow().toMillis()
                - ONE_DAY_WITH_ONE_MINUTE_RESOLUTION.getResolution().toMillis();

        // add one success measurement every minute
        for (long i = start; i <= ts;
                i += ONE_DAY_WITH_ONE_MINUTE_RESOLUTION.getResolution().toMillis()) {
            counter.increment(true, i);
        }

        final Map<Duration, Long> success = counter.getCounts(true);

        assertThat(success)
                .containsEntry(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow(), 1L)
                .containsEntry(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow(), 60L)
                .containsEntry(ONE_DAY_WITH_ONE_HOUR_RESOLUTION.getWindow(), 1440L);
    }

    @Test
    public void testCounterWithThrottledAlert() {

        final SlidingWindowCounter throttledCounter = SlidingWindowCounter.newBuilder(metricsCounter)
                .recordingMeasurementWindows(ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                .reportingMeasurementWindows(ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION,
                        ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
                        ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                .maximumPerSlot(1L)
                .build();
        final int threshold = 2;

        final SlidingWindowCounter consumedInboundCounter = SlidingWindowCounter.newBuilder(metricsCounter)
                .metricsAlert(new ThrottledMetricsAlert(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, threshold,
                        () -> new DummyConnectionMetricsCounter(throttledCounter)))
                .measurementWindows(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION)
                .cleanUpEnabled(false) // better testability
                .build();

        // add some measurements
        final long now = System.currentTimeMillis();
        increment(consumedInboundCounter, 3, now); //above threshold
        increment(consumedInboundCounter, 1, now - Duration.ofMinutes(1).toMillis()); // below threshold
        increment(consumedInboundCounter, 1, now - Duration.ofMinutes(2).toMillis()); // below threshold
        increment(consumedInboundCounter, 4, now - Duration.ofMinutes(3).toMillis()); // above threshold
        increment(consumedInboundCounter, 5, now - Duration.ofMinutes(4).toMillis()); // above threshold

        // verify measurements of the observed counter is correct
        final Map<Duration, Long> success = consumedInboundCounter.getCounts(true);
        assertThat(success)
                .containsEntry(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow(), 3L)
                .containsEntry(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow(), 14L);

        final Map<Duration, Long> throttled = throttledCounter.getCounts(false);

        assertThat(throttled)
                // last minute was throttled
                .containsEntry(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION.getWindow(), 1L)
                // 3 minutes within last hour were "throttled"
                .containsEntry(ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION.getWindow(), 3L);
    }

    private void increment(final SlidingWindowCounter counter, final int count, final long ts) {
        for (int i = 0; i < count; i++) {
            counter.increment(true, ts);
        }
    }

    /**
     * Simple implementation of
     * {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter}.
     */
    private static final class DummyConnectionMetricsCounter implements ConnectionMetricsCounter {

        private final SlidingWindowCounter counter;

        public DummyConnectionMetricsCounter(final SlidingWindowCounter counter) {
            this.counter = counter;
        }

        @Override
        public void recordSuccess() {
            counter.increment(true);
        }

        @Override
        public void recordFailure() {
            counter.increment(false);
        }

        @Override
        public void recordSuccess(final long ts) {
            counter.increment(true, ts);
        }

        @Override
        public void recordFailure(final long ts) {
            counter.increment(false, ts);
        }

        @Override
        public MetricType getMetricType() {
            return MetricType.CONSUMED;
        }

        @Override
        public void reset() {
            counter.reset();
        }
    }

}
