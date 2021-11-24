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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an alert that writes a log entry to the given ConnectionLogger.
 */
public final class ThrottledLoggerMetricsAlert implements MetricsAlert {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottledLoggerMetricsAlert.class);
    private final Function<String, ConnectionLogger> connectionLoggerSupplier;
    private final AtomicLong currentSlot = new AtomicLong();
    private final CounterKey counterKey;

    /**
     * Returns an instance of {@code MetricsAlertFactory}.
     *
     * @param loggerSupplier the supplier to retrieve the logger to write the throttled log entry
     * @return the {@code MetricsAlertFactory} that creates new ThrottledLoggerMetricsAlert instances
     */
    public static MetricsAlertFactory getFactory(final Function<String, ConnectionLogger> loggerSupplier) {
        return new Factory(loggerSupplier);
    }

    private ThrottledLoggerMetricsAlert(final CounterKey counterKey,
            final Function<String, ConnectionLogger> connectionLoggerSupplier) {
        this.connectionLoggerSupplier = connectionLoggerSupplier;
        this.counterKey = counterKey;
    }

    @Override
    public boolean evaluateCondition(final MeasurementWindow window, final long slot, final long value) {
        // make sure only one log entry is written per time slot
        final long current = currentSlot.get();
        if (current != slot && currentSlot.compareAndSet(current, slot)) {
            return MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION == window;
        } else {
            return false;
        }
    }

    @Override
    public void triggerAction(final long ts, final long newValue) {
        final String message = String.format("Throttling event occurred for %s %s metric at address '%s'.",
                counterKey.getMetricDirection(), counterKey.getMetricType(), counterKey.getAddress());
        final ConnectionLogger connectionLogger = connectionLoggerSupplier.apply(counterKey.getAddress());
        if (connectionLogger != null) {
            connectionLogger.failure(message);
        } else {
            LOGGER.debug("Failed to retrieve the connection logger to write throttled log entry.");
        }
    }

    private static final class Factory implements MetricsAlertFactory {

        private final Function<String, ConnectionLogger> connectionLoggerSupplier;

        private Factory(final Function<String, ConnectionLogger> connectionLoggerSupplier) {
            this.connectionLoggerSupplier = connectionLoggerSupplier;
        }

        @Override
        public MetricsAlert create(final CounterKey counterKey, final ConnectionType connectionType,
                final ConnectivityConfig connectivityConfig) {
            return new ThrottledLoggerMetricsAlert(counterKey, connectionLoggerSupplier);
        }
    }

}
