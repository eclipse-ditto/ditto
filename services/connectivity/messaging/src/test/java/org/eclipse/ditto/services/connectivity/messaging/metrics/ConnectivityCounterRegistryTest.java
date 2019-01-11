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

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Direction.INBOUND;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Direction.OUTBOUND;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.CONSUMED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.FILTERED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.MAPPED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.PUBLISHED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.ditto.model.connectivity.ImmutableMeasurement;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests {@link ConnectivityCounterRegistry}.
 */
public class ConnectivityCounterRegistryTest {

    private static final String CONNECTION_ID = "theConnection";
    private static final String SOURCE = "source1";
    private static final String TARGET = "target1";
    private static final Instant FIXED_INSTANT = Instant.now();
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.systemDefault());

    private static Map<Duration, Long> getCounters(final long value) {
        return Stream.of(MeasurementWindow.values())
                .map(MeasurementWindow::getWindow)
                .collect(toMap(d -> d, d -> value));
    }

    @BeforeClass
    public static void setUp() {
        Stream.of(
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, CONSUMED, INBOUND, SOURCE),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, MAPPED, INBOUND, SOURCE),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, CONSUMED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, FILTERED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, MAPPED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, PUBLISHED, OUTBOUND, TARGET)
        ).forEach(counter ->
                // just to have some different values...
                IntStream.rangeClosed(0, counter.getMetric().ordinal()).forEach(i -> {
                    counter.recordSuccess();
                    counter.recordFailure();
                }));
    }

    @Test
    public void testAggregateSourceMetrics() {

        final SourceMetrics sourceMetrics = ConnectivityCounterRegistry.aggregateSourceMetrics(CONNECTION_ID);

        final Measurement[] expected = {
                getMeasurement(MAPPED, true),
                getMeasurement(MAPPED, false),
                getMeasurement(CONSUMED, true),
                getMeasurement(CONSUMED, false)
        };

        assertThat(sourceMetrics.getAddressMetrics().get(SOURCE).getMeasurements()).containsExactlyInAnyOrder(expected);

    }

    @Test
    public void testAggregateTargetMetrics() {

        final TargetMetrics targetMetrics = ConnectivityCounterRegistry.aggregateTargetMetrics(CONNECTION_ID);

        final Measurement[] expected = {
                getMeasurement(MAPPED, true),
                getMeasurement(MAPPED, false),
                getMeasurement(CONSUMED, true),
                getMeasurement(CONSUMED, false),
                getMeasurement(FILTERED, true),
                getMeasurement(FILTERED, false),
                getMeasurement(PUBLISHED, true),
                getMeasurement(PUBLISHED, false)
        };

        assertThat(targetMetrics.getAddressMetrics().get(TARGET).getMeasurements()).containsExactlyInAnyOrder(expected);

    }

    private ImmutableMeasurement getMeasurement(final ConnectivityCounterRegistry.Metric metric, final boolean b) {
        return new ImmutableMeasurement(metric.getLabel(), b, getCounters(metric.ordinal() + 1), FIXED_INSTANT);
    }

}