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

import java.util.Arrays;
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
import kamon.metric.AtomicHdrHistogram;
import kamon.metric.GaugeMetric;
import kamon.metric.HdrHistogram;
import kamon.metric.HistogramMetric;
import kamon.metric.Metric;
import kamon.metric.TimerImpl;
import kamon.metric.TimerMetric;
import kamon.metric.TimerMetricImpl;

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

    private void report(final String name, final Timer timer) {
        final TimerMetric metric = Kamon.timer(name);
        final kamon.metric.Timer kamonTimer = refine(metric);
        reset(kamonTimer, name);
        Arrays.stream(timer.getSnapshot().getValues()).forEach(kamonTimer::record);
    }

    private void report(final String name, final Histogram histogram) {
        final HistogramMetric metric = Kamon.histogram(name);
        final kamon.metric.Histogram kamonHistogram = refine(metric);
        reset(kamonHistogram, name);
        Arrays.stream(histogram.getSnapshot().getValues()).forEach(kamonHistogram::record);
    }

    private void report(final String name, final Counter counter) {
        final GaugeMetric metric = Kamon.gauge(name);
        final kamon.metric.Gauge g = refine(metric);
        g.set(counter.getCount());
    }

    private void report(final String name, final Gauge gauge) {
        final GaugeMetric metric = Kamon.gauge(name);
        final kamon.metric.Gauge g = refine(metric);
        final Object value = gauge.getValue();
        if (value == null) {
            // Do nothing since kamon gauges just keep reporting the previously set value
        } else if (value instanceof Long) {
            g.set((Long) value);
        } else {
            LOGGER.warn(
                    "Gauge <{}> reports values that are not of type long, but of type <{}>. " +
                            "Therefore this gauge can not be reported to prometheus", name, value.getClass().getName());
        }
    }

    private void report(final String name, final Meter meter) {
        final HistogramMetric metric = Kamon.histogram(name);
        final kamon.metric.Histogram h = refine(metric);
        h.record(meter.getCount());
    }

    private <M extends Metric<T>, T> T refine(final M metric) {
        return metric.refine(tags);
    }

    private boolean reset(final kamon.metric.Histogram histogram, final String name) {
        if (histogram instanceof HdrHistogram) {
            ((HdrHistogram) histogram).snapshot(true);
        } else if (histogram instanceof AtomicHdrHistogram) {
            ((AtomicHdrHistogram) histogram).snapshot(true);
        } else if (histogram instanceof TimerImpl) {
            return reset(((TimerImpl) histogram).histogram(), name);
        } else if (histogram instanceof TimerMetricImpl) {
            return reset(((TimerMetricImpl) histogram).underlyingHistogram(), name);
        } else {
            LOGGER.warn("Tried to reset Histogram of type <{}>, but there is no method to reset implemented so far.",
                    histogram.getClass().getName());
            return false;
        }

        LOGGER.debug("Histogram with name <{}> successfully reset", name);
        return true;
    }
}
