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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.ditto.model.connectivity.ImmutableMeasurement;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to collect metrics.
 */
public class ConnectionMetricsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionMetricsCollector.class);

    private final ConnectivityCounterRegistry.Direction direction;
    private final String address;
    private final ConnectivityCounterRegistry.Metric metric;
    private final SlidingWindowCounter counter;

    public ConnectionMetricsCollector(
            final ConnectivityCounterRegistry.Direction direction,
            final String address,
            final ConnectivityCounterRegistry.Metric metric,
            SlidingWindowCounter counter) {
        this.direction = direction;
        this.address = address;
        this.metric = metric;
        this.counter = counter;
    }

    /**
     * Record a successful operation.
     */
    public void recordSuccess() {
        LOGGER.debug("Increment success counter ({},{},{})", direction, address, metric);
        counter.increment();
    }

    /**
     * Record a failed operation.
     */
    public void recordFailure() {
        LOGGER.debug("Increment failure counter ({},{},{})", direction, address, metric);
        counter.increment(false);
    }

    /**
     * Reset all counts.
     */
    public void reset() {
        counter.reset();
    }

    /**
     * @return the direction this collector measures.
     */
    ConnectivityCounterRegistry.Direction getDirection() {
        return direction;
    }

    public String getAddress() {
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
        return new ImmutableMeasurement(metric.getLabel(), success, measurements, getLastMessageTimestamp());
    }

    private Instant getLastMessageTimestamp() {
        return Instant.ofEpochMilli(counter.getLastMeasurementAt());
    }

    /**
     * Executes the given {@link Runnable} and increments the success counter if no exception was thrown or the
     * failed counter if a exception was caught.
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

    public static <T> T record(org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter success,
            org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter failure, final Supplier<T> supplier) {
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConnectionMetricsCollector that = (ConnectionMetricsCollector) o;
        return direction == that.direction &&
                Objects.equals(address, that.address) &&
                metric == that.metric &&
                Objects.equals(counter, that.counter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, address, metric, counter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "direction=" + direction +
                ", address=" + address +
                ", metric=" + metric +
                ", counter=" + counter +
                "]";
    }
}
