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

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an alert that triggers when the monitored metric exceeds the given threshold.
 */
final class ThrottledMetricsAlert implements MetricsAlert {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottledMetricsAlert.class);

    private final long threshold;
    private final Supplier<ConnectionMetricsCounter> lookup;
    private final MeasurementWindow targetMeasurementWindow;

    /**
     * Creates a new ThrottledMetricsAlert instance.
     *
     * @param targetMeasurementWindow the measurement window used to detect if throttling occurred
     * @param threshold the threshold to monitor
     * @param lookup a supplier to lookup the counter to record a possible threshold exceedance (the counter may not
     * exist at the time when the alert is created)
     */
    ThrottledMetricsAlert(final MeasurementWindow targetMeasurementWindow, final long threshold,
            final Supplier<ConnectionMetricsCounter> lookup) {
        this.lookup = lookup;
        this.threshold = threshold;
        this.targetMeasurementWindow = targetMeasurementWindow;
    }

    @Override
    public boolean evaluateCondition(final MeasurementWindow window, final long slot, final long value) {
        return targetMeasurementWindow == window && value > threshold;
    }

    @Override
    public void triggerAction(final long ts, final long newValue) {
        // records the exceeded threshold as failure in the linked counter
        Optional.ofNullable(lookup.get())
                .ifPresentOrElse(connectionMetricsCounter -> connectionMetricsCounter.recordFailure(ts),
                        () -> LOGGER.debug("Failed to resolve the target counter of ThrottledAlert."));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "threshold=" + threshold +
                ", lookup=" + lookup +
                ", targetMeasurementWindow=" + targetMeasurementWindow +
                "]";
    }

}
