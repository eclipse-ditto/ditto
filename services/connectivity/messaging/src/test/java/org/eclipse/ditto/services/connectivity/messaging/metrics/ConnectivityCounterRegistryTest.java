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
import static org.eclipse.ditto.model.connectivity.MetricDirection.INBOUND;
import static org.eclipse.ditto.model.connectivity.MetricDirection.OUTBOUND;
import static org.eclipse.ditto.model.connectivity.MetricType.CONSUMED;
import static org.eclipse.ditto.model.connectivity.MetricType.FILTERED;
import static org.eclipse.ditto.model.connectivity.MetricType.MAPPED;
import static org.eclipse.ditto.model.connectivity.MetricType.PUBLISHED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
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
                IntStream.rangeClosed(0, counter.getMetricType().ordinal()).forEach(i -> {
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

    private Measurement getMeasurement(final MetricType metricType, final boolean b) {
        return ConnectivityModelFactory.newMeasurement(metricType, b, getCounters(metricType.ordinal() + 1),
                FIXED_INSTANT);
    }

    @Test
    public void mergeRetrieveConnectionMetricsResponses() {

        final RetrieveConnectionMetricsResponse merged = ConnectivityCounterRegistry.mergeRetrieveConnectionMetricsResponse(
                TestConstants.Metrics.METRICS_RESPONSE1, TestConstants.Metrics.METRICS_RESPONSE2);

        assertThat(merged.getConnectionId()).isEqualTo(TestConstants.Metrics.ID);

        // check overall sum of connection metrics
        assertThat(merged.getConnectionMetrics().getInboundMetrics().getMeasurements())
                .contains(TestConstants.Metrics.mergeMeasurements(MetricType.CONSUMED, true,
                        TestConstants.Metrics.INBOUND, 4));
        assertThat(merged.getConnectionMetrics().getInboundMetrics().getMeasurements())
                .contains(
                        TestConstants.Metrics.mergeMeasurements(MetricType.MAPPED, true, TestConstants.Metrics.MAPPING,
                                4));

        assertThat(merged.getConnectionMetrics().getOutboundMetrics().getMeasurements())
                .contains(TestConstants.Metrics.mergeMeasurements(MetricType.PUBLISHED, true,
                        TestConstants.Metrics.OUTBOUND, 4));
        assertThat(merged.getConnectionMetrics().getOutboundMetrics().getMeasurements())
                .contains(
                        TestConstants.Metrics.mergeMeasurements(MetricType.MAPPED, true, TestConstants.Metrics.MAPPING,
                                4));

        // check source metrics
        assertThat(merged.getSourceMetrics().getAddressMetrics()).containsKeys("source1", "source2", "source3");
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source1").getMeasurements())
                .contains(TestConstants.Metrics.INBOUND, TestConstants.Metrics.MAPPING);
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source2").getMeasurements())
                .contains(TestConstants.Metrics.mergeMeasurements(MetricType.CONSUMED, true,
                        TestConstants.Metrics.INBOUND, 2),
                        TestConstants.Metrics.mergeMeasurements(MetricType.MAPPED, true, TestConstants.Metrics.MAPPING,
                                2));
        assertThat(merged.getSourceMetrics().getAddressMetrics().get("source3").getMeasurements())
                .contains(TestConstants.Metrics.INBOUND, TestConstants.Metrics.MAPPING);

        // check target metrics
        assertThat(merged.getTargetMetrics().getAddressMetrics()).containsKeys("target1", "target2", "target3");
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target1").getMeasurements())
                .contains(TestConstants.Metrics.MAPPING, TestConstants.Metrics.OUTBOUND);
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target2").getMeasurements())
                .contains(
                        TestConstants.Metrics.mergeMeasurements(MetricType.MAPPED, true, TestConstants.Metrics.MAPPING,
                                2),
                        TestConstants.Metrics.mergeMeasurements(MetricType.PUBLISHED, true,
                                TestConstants.Metrics.OUTBOUND, 2));
        assertThat(merged.getTargetMetrics().getAddressMetrics().get("target3").getMeasurements())
                .contains(TestConstants.Metrics.MAPPING, TestConstants.Metrics.OUTBOUND);
    }
}
