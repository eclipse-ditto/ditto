/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.metrics;

import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.KamonCounter;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.services.utils.metrics.instruments.histogram.Histogram;
import org.eclipse.ditto.services.utils.metrics.instruments.histogram.KamonHistogram;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.DefaultTimerBuilder;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.ExpiringTimerBuilder;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;

public final class DittoMetrics {

    private DittoMetrics() {}

    /**
     * Creates a new timer object for a metric with the given name.
     *
     * @param name The name of the metric.
     * @return The new timer.
     */
    public static PreparedTimer timer(final String name) {
        return new DefaultTimerBuilder(name).build();
    }

    public static ExpiringTimerBuilder expiringTimer(final String name) {
        return new ExpiringTimerBuilder(name);
    }

    public static Counter counter(final String name) {
        return KamonCounter.newCounter(name);
    }

    public static Gauge gauge(final String name) {
        return KamonGauge.newGauge(name);
    }

    public static Histogram histogram(final String name) {
        return KamonHistogram.newHistogram(name);
    }
}
