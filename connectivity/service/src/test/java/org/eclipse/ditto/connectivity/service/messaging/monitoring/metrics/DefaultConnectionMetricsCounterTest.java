/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.stream.IntStream;

import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests {@link DefaultConnectionMetricsCounter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultConnectionMetricsCounterTest {

    private DefaultConnectionMetricsCounter underTest;

    @Mock
    public Counter metricsCounter;


    @Before
    public void setUp() {
        when(metricsCounter.tag(eq("success"), any(Boolean.class))).thenReturn(metricsCounter);
        underTest = new DefaultConnectionMetricsCounter(MetricDirection.INBOUND, "source", MetricType.CONSUMED,
                SlidingWindowCounter.newBuilder(metricsCounter)
                        .measurementWindows(MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION)
                        .build());
    }

    @Test
    public void testResetCounter() {
        // initially counter is expected to be empty
        assertCounterValues(0L, 0L);

        // record some values
        recordCounterValues(10, 5);

        // expect the correct counter values
        assertCounterValues(10L, 5L);

        // reset counter
        underTest.reset();

        // counter is expected to be empty after reset
        assertCounterValues(0L, 0L);

        // record some values again
        recordCounterValues(20, 10);
        // expect the correct counter values
        assertCounterValues(20L, 10L);
    }

    private void recordCounterValues(final int successes, final int failures) {
        IntStream.range(0, successes).forEach(i -> underTest.recordSuccess());
        IntStream.range(0, failures).forEach(i -> underTest.recordFailure());
    }

    private void assertCounterValues(final long successes, final long failures) {
        assertThat(underTest.toMeasurement(true).getCounts()).containsEntry(Duration.ofMinutes(1), successes);
        assertThat(underTest.toMeasurement(false).getCounts()).containsEntry(Duration.ofMinutes(1), failures);
    }
}
