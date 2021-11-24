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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Supplier;

/**
 * Delegates to an alert that is resolved using a supplier e.g. because the alert is not yet created, or it might
 * change over time.
 */
final class DelegatingAlert implements MetricsAlert {

    private final Supplier<MetricsAlert> lookup;

    /**
     * Creates a new instance of a {@code DelegatingAlert}.
     *
     * @param lookup the supplier used to resole the delegate
     */
    DelegatingAlert(final Supplier<MetricsAlert> lookup) {
        this.lookup = checkNotNull(lookup, "lookup");
    }

    @Override
    public boolean evaluateCondition(final MeasurementWindow window, final long slot, final long value) {
        final MetricsAlert metricsAlert = lookup.get();
        if (metricsAlert != null) {
            return metricsAlert.evaluateCondition(window, slot, value);
        } else {
            return false;
        }
    }

    @Override
    public void triggerAction(final long timestamp, final long value) {
        final MetricsAlert metricsAlert = lookup.get();
        if (metricsAlert != null) {
            metricsAlert.triggerAction(timestamp, value);
        }
    }

}
