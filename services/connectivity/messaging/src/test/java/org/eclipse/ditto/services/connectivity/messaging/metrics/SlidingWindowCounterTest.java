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
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.Test;

/**
 * Tests {@link SlidingWindowCounter}.
 */
public class SlidingWindowCounterTest {

    @Test
    public void testLastMeasurementAt() {
        final SlidingWindowCounter counter = new SlidingWindowCounter(Clock.systemUTC(), MeasurementWindow.ONE_HOUR);
        final long ts = System.currentTimeMillis();
        counter.increment(true, ts);
        assertThat(counter.getLastSuccessMeasurementAt()).isEqualTo(ts);
    }

    @Test
    public void testOneMeasurementEveryMs() {
        final SlidingWindowCounter counter = new SlidingWindowCounter(Clock.systemUTC(),
                MeasurementWindow.ONE_MINUTE,
                MeasurementWindow.ONE_HOUR);

        final long ts = System.currentTimeMillis() + MeasurementWindow.ONE_MINUTE.getWindow().toMillis();
        final long start = ts
                - MeasurementWindow.ONE_HOUR.getWindow().toMillis()
                - MeasurementWindow.ONE_HOUR.getResolution().toMillis();

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

        assertThat(success).containsEntry(MeasurementWindow.ONE_MINUTE.getWindow(), 60L);
        assertThat(success).containsEntry(MeasurementWindow.ONE_HOUR.getWindow(), 3600L);
        assertThat(failure).containsEntry(MeasurementWindow.ONE_MINUTE.getWindow(), 12L);
        assertThat(failure).containsEntry(MeasurementWindow.ONE_HOUR.getWindow(), 720L);

    }

}
