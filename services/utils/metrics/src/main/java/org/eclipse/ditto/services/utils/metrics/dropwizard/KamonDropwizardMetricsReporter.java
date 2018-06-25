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
package org.eclipse.ditto.services.utils.metrics.dropwizard;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;

/**
 * Reports metrics of "codehale Metrics framework" to Kamon in a scheduled way.
 */
public class KamonDropwizardMetricsReporter extends ScheduledReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonDropwizardMetricsReporter.class);
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE_INSTANCE = "service.instance";
    public static final String METRICS_NAME = "metrics.name";

    private final Map<String, String> tags;

    KamonDropwizardMetricsReporter(final MetricRegistry metricRegistry, final String metricName,
            final String serviceName) {
        super(metricRegistry, format("kamon-%s-reporter", metricName), MetricFilter.ALL, TimeUnit.SECONDS,
                TimeUnit.MILLISECONDS);

        this.tags = new HashMap<>();
        tags.put(SERVICE_NAME, serviceName);
        tags.put(SERVICE_INSTANCE, ConfigUtil.calculateInstanceUniqueSuffix());
        tags.put(METRICS_NAME, metricName);
    }

    @Override
    public void start(long period, final TimeUnit timeUnit) {
        String metricName = tags.get(METRICS_NAME);
        LOGGER.info("Start reporting kamon metrics <{}>.", metricName);

        super.start(period, timeUnit);
    }

    @Override
    public void report(final SortedMap<String, Gauge> gauges,
            final SortedMap<String, Counter> counters,
            final SortedMap<String, Histogram> histograms,
            final SortedMap<String, Meter> meters,
            final SortedMap<String, Timer> timers) {
        gauges.forEach(this::report);
        counters.forEach(this::report);
        histograms.forEach(this::report);
        meters.forEach(this::report);
        timers.forEach(this::report);
    }

    private void report(final String name, final Timer timer) {
        LOGGER.debug("Reporting dropwizard timer with name <{}>.", name);
        final PreparedTimer dittoTimer = DittoMetrics.timer(name).tags(this.tags);
        dittoTimer.reset();
        Arrays.stream(timer.getSnapshot().getValues())
                .forEach(timeInNanos -> dittoTimer.record(timeInNanos, TimeUnit.NANOSECONDS));
    }

    private void report(final String name, final Histogram histogram) {
        LOGGER.debug("Reporting dropwizard histogram with name <{}>.", name);
        final org.eclipse.ditto.services.utils.metrics.instruments.histogram.Histogram dittoHistogram =
                DittoMetrics.histogram(name).tags(this.tags);
        dittoHistogram.reset();
        Arrays.stream(histogram.getSnapshot().getValues()).forEach(dittoHistogram::record);
    }

    private void report(final String name, final Counter counter) {
        LOGGER.debug("Reporting dropwizard counter with name <{}>.", name);
        final org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter dittoCounter =
                DittoMetrics.counter(name).tags(tags);
        dittoCounter.reset();
        dittoCounter.increment(counter.getCount());
    }

    private void report(final String name, final Gauge gauge) {
        LOGGER.debug("Reporting dropwizard gauge with name <{}>.", name);
        final Object value = gauge.getValue();
        if (value == null) {
            // Do nothing since kamon gauges just keep reporting the previously set value
        } else if (value instanceof Long) {
            final org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge dittoGauge =
                    DittoMetrics.gauge(name).tags(this.tags);
            dittoGauge.set((Long) value);
        } else {
            LOGGER.warn(
                    "Gauge <{}> reports values that are not of type long, but of type <{}>. " +
                            "Therefore this gauge can not be reported to prometheus", name, value.getClass().getName());
        }
    }

    private void report(final String name, final Meter meter) {
        LOGGER.debug("Reporting dropwizard meter with name <{}>.", name);
        final org.eclipse.ditto.services.utils.metrics.instruments.histogram.Histogram dittoHistogram =
                DittoMetrics.histogram(name).tags(this.tags);
        dittoHistogram.reset();
        dittoHistogram.record(meter.getCount());
    }
}
