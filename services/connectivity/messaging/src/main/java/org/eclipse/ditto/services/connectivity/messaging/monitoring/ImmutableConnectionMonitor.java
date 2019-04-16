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

package org.eclipse.ditto.services.connectivity.messaging.monitoring;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.ConnectionMetricsCollector;

// TODO: doc & test
@Immutable
public final class ImmutableConnectionMonitor implements ConnectionMonitor {
    private final ConnectionMetricsCollector counter;
    private final ConnectionLogger logger;

    private ImmutableConnectionMonitor(final ImmutableConnectionMonitorBuilder builder) {
        this.counter = builder.counter;
        this.logger = builder.logger;
    }

    @Override
    public ConnectionLogger getLogger() {
        return logger;
    }

    @Override
    public ConnectionMetricsCollector getCounter() {
        return counter;
    }

    public static ConnectionMonitor.Builder builder(final ConnectionMetricsCollector counter, final ConnectionLogger logger) {
        return new ImmutableConnectionMonitorBuilder(counter, logger);
    }

    static class ImmutableConnectionMonitorBuilder implements ConnectionMonitor.Builder {

        private final ConnectionMetricsCollector counter;
        private final ConnectionLogger logger;

        private ImmutableConnectionMonitorBuilder(final ConnectionMetricsCollector counter, final ConnectionLogger logger) {
            // intentionally empty
            this.counter = checkNotNull(counter);
            this.logger = checkNotNull(logger);
        }

        @Override
        public ConnectionMonitor build() {
            return new ImmutableConnectionMonitor(this);
        }

    }

}
