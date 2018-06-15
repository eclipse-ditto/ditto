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

import static java.lang.String.format;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
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

import kamon.Kamon;
import kamon.metric.CounterMetric;
import kamon.metric.GaugeMetric;
import kamon.metric.HistogramMetric;
import kamon.metric.Metric;
import kamon.metric.TimerMetric;

/**
 * Reports metrics of "codehale Metrics framework" to Kamon in a scheduled way.
 */
public class KamonMetricsReporter extends ScheduledReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonMetricsReporter.class);

    private final Map<String, String> tags;

    KamonMetricsReporter(final MetricRegistry metricRegistry, final String metricName, final String serviceName) {
        super(metricRegistry, format("kamon-%s-reporter", metricName), MetricFilter.ALL, TimeUnit.SECONDS,
                TimeUnit.MILLISECONDS);

        this.tags = new HashMap<>();
        tags.put(TracingTags.SERVICE_NAME, serviceName);
        tags.put(TracingTags.SERVICE_INSTANCE, ConfigUtil.calculateInstanceUniqueSuffix());
        tags.put(TracingTags.METRICS_NAME, metricName);
    }

    @Override
    public void start(long period, final TimeUnit timeUnit) {
        String metricName = tags.get(TracingTags.METRICS_NAME);
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

    private void report(String name, Timer timer) {
        final TimerMetric metric = Kamon.timer(name);
        final kamon.metric.Timer t = refine(metric);
        t.record(timer.getCount());
    }

    private void report(String name, Histogram histogram) {
        final HistogramMetric metric = Kamon.histogram(name);
        final kamon.metric.Histogram h = refine(metric);
        h.record(histogram.getCount());
    }

    private void report(String name, Counter counter) {
        final CounterMetric metric = Kamon.counter(name);
        final kamon.metric.Counter c = refine(metric);
        c.increment(counter.getCount());
    }

    private void report(String name, Gauge gauge) {
        final GaugeMetric metric = Kamon.gauge(name);
        final kamon.metric.Gauge g = refine(metric);
        final Object value = gauge.getValue();
        if (value instanceof Long) {
            g.set((Long) value);
        } else {
            LOGGER.warn(
                    "Gauge <{}> reports values that are not of type long. Therefore this gauge can not be reported " +
                            "to prometheus");
        }
    }

    private void report(String name, Meter meter) {
        final HistogramMetric metric = Kamon.histogram(name);
        final kamon.metric.Histogram h = refine(metric);
        h.record(meter.getCount());
    }

    private <M extends Metric<T>, T> T refine(final M metric) {
        return metric.refine(tags);
    }
}
