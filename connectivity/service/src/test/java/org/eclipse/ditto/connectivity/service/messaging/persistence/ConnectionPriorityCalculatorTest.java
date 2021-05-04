/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.connectivity.model.AddressMetric;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.junit.Test;

/**
 * Unit tests for {@link ConnectionPriorityCalculator}.
 */
public final class ConnectionPriorityCalculatorTest {

    @Test
    public void calculatePriority() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true, Map.of(Duration.ofHours(24), 10L),
                        null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true, Map.of(Duration.ofHours(24), 10L),
                        null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(10 + 10);
    }

    @Test
    public void calculatePriorityWithMoreConsumedMessagesThanIntMaxValue() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE + 1L), null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true,
                        Map.of(Duration.ofHours(24), 0L), null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void calculatePriorityWithMorePublishedMessagesThanIntMaxValue() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true,
                        Map.of(Duration.ofHours(24), 0L), null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE + 1L), null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void calculatePriorityWithMorePublishedAndConsumedMessagesThanIntMaxValue() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L + 1), null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L + 1), null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void calculatePriorityWithPublishedAndConsumedMessagesEqualToIntMaxValue() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L), null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L + 1), null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void calculatePriorityWithPublishedAndConsumedMessagesOneLessThanIntMaxValue() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L), null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true,
                        Map.of(Duration.ofHours(24), Integer.MAX_VALUE / 2L), null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        final ConnectionMetrics connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);

        assertThat(ConnectionPriorityCalculator.calculatePriority(connectionMetrics)).isEqualTo(Integer.MAX_VALUE - 1);
    }

}
