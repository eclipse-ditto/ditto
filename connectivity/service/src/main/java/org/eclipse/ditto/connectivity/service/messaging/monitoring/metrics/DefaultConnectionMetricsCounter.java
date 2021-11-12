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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to collect metrics.
 */
public final class DefaultConnectionMetricsCounter implements ConnectionMetricsCounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnectionMetricsCounter.class);

    private final MetricDirection metricDirection;
    private final String address;
    private final MetricType metricType;
    private final SlidingWindowCounter counter;

    DefaultConnectionMetricsCounter(
            final MetricDirection metricDirection,
            final String address,
            final MetricType metricType,
            final SlidingWindowCounter counter) {
        this.metricDirection = metricDirection;
        this.address = address;
        this.metricType = metricType;
        this.counter = counter;
    }

    @Override
    public void recordSuccess() {
        logAction("Increment success counter");
        counter.increment();
    }

    @Override
    public void recordFailure() {
        logAction("Increment failure counter");
        counter.increment(false);
    }

    @Override
    public void recordSuccess(final long ts) {
        logAction("Increment success counter");
        counter.increment(true, ts);
    }

    @Override
    public void recordFailure(final long ts) {
        logAction("Increment failure counter");
        counter.increment(false, ts);
    }

    @Override
    public void reset() {
        logAction("Reset counter");
        counter.reset();
    }

    private void logAction(final String action) {
        LOGGER.trace("{} ({},{},{})", action, metricDirection, address, metricType);
    }

    @Override
    public MetricType getMetricType() {
        return metricType;
    }

    /**
     * @return the metricDirection this collector measures.
     */
    MetricDirection getMetricDirection() {
        return metricDirection;
    }

    String getAddress() {
        return address;
    }

    /**
     * Produces a {@link Measurement} for reporting.
     *
     * @param success whether to count successful or failed operations
     * @return a measurement containing the counts for different intervals
     */
    Measurement toMeasurement(final boolean success) {
        final Map<Duration, Long> measurements = counter.getCounts(success);
        final Instant lastMessageTimestamp = getLastMessageTimestamp(success);
        final Instant timestamp;
        if (lastMessageTimestamp.equals(Instant.EPOCH)) {
            timestamp = null;
        } else {
            timestamp = lastMessageTimestamp;
        }
        return ConnectivityModelFactory.newMeasurement(metricType, success, measurements, timestamp);
    }

    private Instant getLastMessageTimestamp(final boolean success) {
        return Instant.ofEpochMilli(success ? counter.getLastSuccessMeasurementAt() :
                counter.getLastFailureMeasurementAt());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionMetricsCounter that = (DefaultConnectionMetricsCounter) o;
        return metricDirection == that.metricDirection &&
                Objects.equals(address, that.address) &&
                metricType == that.metricType &&
                Objects.equals(counter, that.counter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricDirection, address, metricType, counter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "direction=" + metricDirection +
                ", address=" + address +
                ", metric=" + metricType +
                ", counter=" + counter +
                "]";
    }

}
