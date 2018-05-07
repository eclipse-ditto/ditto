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
package org.eclipse.ditto.services.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.slf4j.Logger;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension$;
import kamon.Kamon;
import kamon.metric.HistogramMetric;
import kamon.metric.Metric;
import kamon.metric.TimerMetric;

/**
 * This Runnable starts a Prometheus MongoDB metrics reporter if the appropriate configuration settings for Prometheus are
 * provided.
 */
@NotThreadSafe
public final class KamonMongoDbMetricsStarter implements Runnable {

    private final Logger logger;
    private final Runnable implementation;

    private KamonMongoDbMetricsStarter(final Runnable theImplementation, final Logger logger) {
        this.implementation = theImplementation;
        this.logger = logger;
    }

    /**
     * Returns a new instance of {@code KamonMongoDbMetricsStarter}.
     *
     * @param config the configuration settings of Mongo Persistence Extension that creates MongoDB metrics.
     * @param actorSystem the Akka actor system to be used for creating the metric registry.
     * @param serviceName the name of the service which sends metrics.
     * @param logger the logger to be used.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code serviceName} is empty.
     */
    public static KamonMongoDbMetricsStarter newInstance(final Config config,
            final ActorSystem actorSystem,
            final String serviceName,
            final Logger logger) {
        checkNotNull(config, "config");
        checkNotNull(actorSystem, "Akka actor system");
        argumentNotEmpty(serviceName, "service name");
        checkNotNull(logger, "logger");

        final MetricRegistry metricRegistry = createMetricRegistry(actorSystem, config);
        final Runnable implementation = new KamonDropwizardMetricsReporter(metricRegistry, serviceName);
        return new KamonMongoDbMetricsStarter(implementation, logger);
    }

    /**
     * Enables logging of mongo-persistence-plugin statistics.
     */
    @SuppressWarnings("RedundantCast")
    private static MetricRegistry createMetricRegistry(final ActorSystem actorSystem, final Config config) {
        // Would not compile without cast!
        // The cast is not redundant for Maven.
        return ((MongoPersistenceExtension) MongoPersistenceExtension$.MODULE$.apply(actorSystem))
                .configured(config)
                .registry();
    }

    @Override
    public void run() {
        logger.info("Starting reporting dropwizard metrics of MongoDB persistence to Kamon");
        implementation.run();
    }

    /**
     * Metrics reporter that uses {@link com.codahale.metrics.ScheduledReporter} for periodically pulling metrics
     * from a dropwizard {@link com.codahale.metrics.MetricRegistry} and reporting them to Kamon.
     */
    @NotThreadSafe
    private static final class KamonDropwizardMetricsReporter extends ScheduledReporter implements Runnable {

        private static final short POLL_PERIOD = 5; // seconds between polls

        private final Map<String, String> tags;

        private KamonDropwizardMetricsReporter(final MetricRegistry metricRegistry, final String serviceName) {
            super(metricRegistry, "kamon-mongo-reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);

            this.tags = new HashMap<>();
            tags.put(TracingTags.SERVICE_NAME, serviceName);
            tags.put(TracingTags.SERVICE_INSTANCE, ConfigUtil.calculateInstanceUniqueSuffix());
        }

        @Override
        public void run() {
            this.start(POLL_PERIOD, TimeUnit.SECONDS);
        }

        @Override
        public void report(final SortedMap<String, Gauge> gauges,
                final SortedMap<String, Counter> counters,
                final SortedMap<String, Histogram> histograms,
                final SortedMap<String, Meter> meters,
                final SortedMap<String, Timer> timers) {
            // if we can believe the docs of akka-persistence-mongo, they only measure histograms and timers.
            histograms.forEach((name, histogram) -> {
                final HistogramMetric metric = Kamon.histogram(name);
                final kamon.metric.Histogram h = refine(metric);
                h.record(histogram.getCount());
            });

            timers.forEach((name, timer) -> {
                final TimerMetric metric = Kamon.timer(name);
                final kamon.metric.Timer t = refine(metric);
                t.record(timer.getCount());
            });
        }

        private <M extends Metric<T>, T> T refine(final M metric) {
            return metric.refine(tags);
        }

    }

}
