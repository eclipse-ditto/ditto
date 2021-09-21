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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import org.eclipse.ditto.connectivity.model.MetricType;

/**
 * Helper class to count metrics.
 */
public interface ConnectionMetricsCounter {

    /**
     * Record a successful operation.
     */
    void recordSuccess();

    /**
     * Record a failed operation.
     */
    void recordFailure();

    /**
     * Record a successful operation at the given timestamp.
     */
    void recordSuccess(long ts);

    /**
     * Record a failed operation at the given timestamp.
     */
    void recordFailure(long ts);

    /**
     * @return the metricType of this collector.
     */
    MetricType getMetricType();

    /**
     * Resets the counter.
     */
    void reset();
}
