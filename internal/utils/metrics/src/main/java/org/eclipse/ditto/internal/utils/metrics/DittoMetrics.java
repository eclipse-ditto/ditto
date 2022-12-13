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
package org.eclipse.ditto.internal.utils.metrics;

import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.KamonCounter;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.histogram.Histogram;
import org.eclipse.ditto.internal.utils.metrics.instruments.histogram.KamonHistogram;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.Timers;

/**
 * Contains static method factories in order to build Ditto MetricInstruments.
 */
public final class DittoMetrics {

    private DittoMetrics() {
        throw new AssertionError();
    }

    /**
     * Creates a new timer object for a metric with the given name.
     *
     * @param name The name of the metric.
     * @return The new timer.
     */
    public static PreparedTimer timer(final String name) {
        return Timers.newTimer(name);
    }

    /**
     * Creates a {@link org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter} with the given name.
     *
     * @param name the name of the counter.
     * @return the {@link org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter} with the given name.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public static Counter counter(final String name) {
        return KamonCounter.newCounter(name);
    }

    /**
     * Creates a {@link Counter} with the given name and tags.
     *
     * @param name the name of the counter.
     * @param tags the tags of the counter.
     * @return the {@link Counter} with the given name.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public static Counter counter(final String name, final TagSet tags) {
        return KamonCounter.newCounter(name, tags);
    }

    /**
     * Creates a {@link org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge} with the given name.
     *
     * @param name The name of the gauge.
     * @return The {@link org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge} with the given name.
     */
    public static Gauge gauge(final String name) {
        return KamonGauge.newGauge(name);
    }

    /**
     * Creates a {@link org.eclipse.ditto.internal.utils.metrics.instruments.histogram.Histogram} with the given name.
     *
     * @param name The name of the histogram.
     * @return The {@link org.eclipse.ditto.internal.utils.metrics.instruments.histogram.Histogram} with the given name.
     */
    public static Histogram histogram(final String name) {
        return KamonHistogram.newHistogram(name);
    }
}
