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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to collect metrics.
 */
public final class ConnectionMetricsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionMetricsCollector.class);

    private final MetricDirection metricDirection;
    private final String address;
    private final MetricType metricType;
    private final SlidingWindowCounter counter;

    ConnectionMetricsCollector(
            final MetricDirection metricDirection,
            final String address,
            final MetricType metricType,
            SlidingWindowCounter counter) {
        this.metricDirection = metricDirection;
        this.address = address;
        this.metricType = metricType;
        this.counter = counter;
    }

    /**
     * Record a successful operation.
     */
    public void recordSuccess() {
        LOGGER.trace("Increment success counter ({},{},{})", metricDirection, address, metricType);
        counter.increment();
    }

    /**
     * Record a failed operation.
     */
    public void recordFailure() {
        LOGGER.trace("Increment failure counter ({},{},{})", metricDirection, address, metricType);
        counter.increment(false);
    }

    /**
     * Reset all counts.
     */
    public void reset() {
        counter.reset();
    }

    /**
     * @return the metricDirection this collector measures.
     */
    MetricDirection getMetricDirection() {
        return metricDirection;
    }

    public String getAddress() {
        return address;
    }

    /**
     * @return the metricType of this collector
     */
    MetricType getMetricType() {
        return metricType;
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

    /**
     * Executes the given {@link Runnable} and increments the success counter if no exception was thrown or the failed
     * counter if a exception was caught.
     *
     * @param runnable the runnable to be executed
     */
    public void record(final Runnable runnable) {
        try {
            runnable.run();
            recordSuccess();
        } catch (final Exception e) {
            recordFailure();
            throw e;
        }
    }

    /**
     * Executes the passed {@code supplier} and increments the success counter if no exception was thrown or the failed
     * counter if a exception was caught.
     */
    public <T> T record(final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            recordSuccess();
            return result;
        } catch (final Exception e) {
            recordFailure();
            throw e;
        }
    }

    /**
     * Executes the passed {@code supplier} and increments the passed {@code success} counter if no exception was
     * thrown or the passed {@code failed} counter if a exception was caught.
     */
    public static <T> T record(final Counter success, final Counter failure, final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            success.increment();
            return result;
        } catch (final Exception e) {
            failure.increment();
            throw e;
        }
    }

    /**
     * Retrieves the result of the given {@link Supplier} and updates the given metrics accordingly.
     *
     * @param <T> the result type
     * @param metrics the metrics that will be updated
     * @param supplier the supplier to be executed
     * @return the result of the supplier
     */
    public static <T> T record(final Set<ConnectionMetricsCollector> metrics, final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            metrics.forEach(ConnectionMetricsCollector::recordSuccess);
            return result;
        } catch (final Exception e) {
            metrics.forEach(ConnectionMetricsCollector::recordFailure);
            throw e;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionMetricsCollector that = (ConnectionMetricsCollector) o;
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
