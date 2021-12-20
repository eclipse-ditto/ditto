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

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;

/**
 * The MetricsAlert interface defines a condition to check whether the given action should be executed.
 */
interface MetricsAlert {

    /**
     * Evaluates if the given action {@link #triggerAction} should be called.
     *
     * @param window the window in which the new measurement occurred
     * @param slot the time slot when the alert occurred
     * @param value the updated value
     * @return {@code true} if the condition was met, {@code false} otherwise
     */
    boolean evaluateCondition(final MeasurementWindow window, final long slot, final long value);

    /**
     * Is executed if the condition ({@link #evaluateCondition}) was met.
     *
     * @param timestamp the timestamp of the new measurement
     * @param value the updated value
     */
    void triggerAction(final long timestamp, final long value);

    /**
     * Defines a combination of MetricDirection and MetricType.
     */
    enum Key {

        CONSUMED_INBOUND(MetricDirection.INBOUND, MetricType.CONSUMED),
        MAPPED_INBOUND(MetricDirection.INBOUND, MetricType.MAPPED),
        THROTTLED_INBOUND(MetricDirection.INBOUND, MetricType.THROTTLED);

        private final MetricDirection metricDirection;
        private final MetricType metricType;

        /**
         * Finds the combination of the given MetricDirection and MetricType, if it exists.
         *
         * @param metricDirection a metricDirection
         * @param metricType a metricType
         * @return the combination of the given MetricDirection and MetricType, if it exists or an empty Optional otherwise.
         */
        static Optional<Key> from(final MetricDirection metricDirection, final MetricType metricType) {
            return Arrays.stream(values())
                    .filter(k -> k.metricDirection == metricDirection && k.metricType == metricType)
                    .findAny();
        }

        MetricDirection getMetricDirection() {
            return metricDirection;
        }

        MetricType getMetricType() {
            return metricType;
        }

        Key(final MetricDirection metricDirection, final MetricType metricType) {
            this.metricDirection = metricDirection;
            this.metricType = metricType;
        }
    }
}
