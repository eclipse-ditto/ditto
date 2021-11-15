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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter;

/**
 * Default implementation of {@link ConnectionMonitor}.
 */
public final class DefaultConnectionMonitor implements ConnectionMonitor {

    private final ConnectionMetricsCounter counter;
    private final ConnectionLogger logger;

    private DefaultConnectionMonitor(final ConnectionMonitorBuilder builder) {
        this.counter = builder.counter;
        this.logger = builder.logger;
    }

    @Override
    public ConnectionLogger getLogger() {
        return logger;
    }

    @Override
    public ConnectionMetricsCounter getCounter() {
        return counter;
    }

    /**
     * Creates a new builder for an {@code DefaultConnectionMonitor}.
     *
     * @param counter the counter to use in the connection monitor.
     * @param logger the logger to use in the connection monitor.
     * @return a new builder.
     */
    public static Builder builder(final ConnectionMetricsCounter counter, final ConnectionLogger logger) {
        return new ConnectionMonitorBuilder(counter, logger);
    }

    /**
     * Implementation of {@link ConnectionMonitor.Builder} that allows building a {@code DefaultConnectionMonitor}.
     */
    private static class ConnectionMonitorBuilder implements Builder {

        private final ConnectionMetricsCounter counter;
        private final ConnectionLogger logger;

        private ConnectionMonitorBuilder(final ConnectionMetricsCounter counter,
                final ConnectionLogger logger) {
            this.counter = checkNotNull(counter);
            this.logger = checkNotNull(logger);
        }

        /**
         * @return A new {@code DefaultConnectionMonitor.}
         */
        @Override
        public ConnectionMonitor build() {
            return new DefaultConnectionMonitor(this);
        }

    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionMonitor that = (DefaultConnectionMonitor) o;
        return Objects.equals(counter, that.counter) &&
                Objects.equals(logger, that.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(counter, logger);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "counter=" + counter +
                ", logger=" + logger +
                "]";
    }

}
