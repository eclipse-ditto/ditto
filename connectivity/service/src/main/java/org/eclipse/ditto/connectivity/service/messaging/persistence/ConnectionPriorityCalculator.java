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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.MetricType;

/**
 * Calculates the priority of a connection based on its connection metrics.
 * The calculation logic is to sum up the number of consumed inbound messages with the number of published outbound
 * messages.
 * The priority is capped to {@link Integer#MAX_VALUE}.
 */
final class ConnectionPriorityCalculator {

    private ConnectionPriorityCalculator() {}

    static Integer calculatePriority(final ConnectionMetrics connectionMetrics) {
        final Long consumedInboundMessages =
                getMeasurement(connectionMetrics.getInboundMetrics().getMeasurements(), MetricType.CONSUMED)
                        .map(Measurement::getCounts)
                        .flatMap(counts -> counts.keySet().stream().max(Duration::compareTo).map(counts::get))
                        .orElse(0L);
        final Long publishedOutboundMessages =
                getMeasurement(connectionMetrics.getOutboundMetrics().getMeasurements(), MetricType.PUBLISHED)
                        .map(Measurement::getCounts)
                        .flatMap(counts -> counts.keySet().stream().max(Duration::compareTo).map(counts::get))
                        .orElse(0L);

        final int priority;
        if (Integer.MAX_VALUE < consumedInboundMessages) {
            priority = Integer.MAX_VALUE;
        } else if ((Integer.MAX_VALUE - consumedInboundMessages) < publishedOutboundMessages) {
            priority = Integer.MAX_VALUE;
        } else {
            // Overflow is impossible as it's checked in the previous conditions.
            priority = Math.toIntExact(consumedInboundMessages + publishedOutboundMessages);
        }
        return priority;
    }

    private static Optional<Measurement> getMeasurement(final Set<Measurement> measurements,
            final MetricType metricType) {
        checkNotNull(metricType, "metricType");
        return measurements.stream()
                .filter(Measurement::isSuccess)
                .filter(measurement -> metricType.equals(measurement.getMetricType()))
                .findAny();
    }

}
