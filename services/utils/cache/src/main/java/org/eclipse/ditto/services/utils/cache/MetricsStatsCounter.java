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
package org.eclipse.ditto.services.utils.cache;

import static com.codahale.metrics.MetricRegistry.name;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;

/**
 * <p>
 * A caffeine {@link StatsCounter} implementation for Dropwizard Metrics.
 * </p>
 * <p>
 * Inspired by <a href="https://github.com/ben-manes/caffeine/blob/master/examples/stats-metrics">Caffeine examples</a>.
 * </p>
 *
 * @see CacheStats for details on how the statistics are calculated.
 */
public final class MetricsStatsCounter implements StatsCounter {

    /**
     * The names of the Metrics provided by this counter.
     */
    public enum MetricName {
        /**
         * Cache hits.
         */
        HITS("hits"),
        /**
         * Cache misses.
         */
        MISSES("misses"),
        /**
         * Calls of the cache's load function. Might be lower than {@link #MISSES} in case of parallel cache access.
         */
        LOADS("loads"),
        /**
         * Number of successful loads.
         */
        LOADS_SUCCESS("loads-success"),
        /**
         * Number of failed loads.
         */
        LOADS_FAILURE("loads-failure"),
        /**
         * Number of cache evictions, e.g. when the cache grows to large. Manual cache invalidation is NOT counted.
         */
        EVICTIONS("evictions"),
        /**
         * Sum of the weights of the cache evictions.
         */
        EVICTIONS_WEIGHT("evictions-weight");

        private final String name;

        MetricName(final String name) {
            this.name = name;
        }

        /**
         * Returns the String value of this metric name, i.e. "just the name".
         *
         * @return the String value.
         */
        public String getValue() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final int DEFAULT_EVICTION_WEIGHT = 1;

    private final Meter hitCount;
    private final Meter missCount;
    private final Meter loadSuccessCount;
    private final Meter loadFailureCount;
    private final Timer totalLoadTime;
    private final Meter evictionCount;
    private final Meter evictionWeight;

    private MetricsStatsCounter(final MetricRegistry registry, final String metricsPrefix) {
        hitCount = registry.meter(name(metricsPrefix, "hits"));
        missCount = registry.meter(name(metricsPrefix, "misses"));
        totalLoadTime = registry.timer(name(metricsPrefix, "loads"));
        loadSuccessCount = registry.meter(name(metricsPrefix, "loads-success"));
        loadFailureCount = registry.meter(name(metricsPrefix, "loads-failure"));
        evictionCount = registry.meter(name(metricsPrefix, "evictions"));
        evictionWeight = registry.meter(name(metricsPrefix, "evictions-weight"));
    }

    /**
     * Creates an instance to be used by a single cache.
     *
     * @param metricsPrefix the prefix name for the metrics.
     * @param registry the {@link MetricRegistry}.
     * @return the instance.
     */
    static MetricsStatsCounter of(final String metricsPrefix, final MetricRegistry registry) {
        requireNonNull(metricsPrefix);
        requireNonNull(registry);

        return new MetricsStatsCounter(registry, metricsPrefix);
    }

    @Override
    public void recordHits(int count) {
        hitCount.mark(count);
    }

    @Override
    public void recordMisses(int count) {
        missCount.mark(count);
    }

    @Override
    public void recordLoadSuccess(long loadTime) {
        loadSuccessCount.mark();
        totalLoadTime.update(loadTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordLoadFailure(long loadTime) {
        loadFailureCount.mark();
        totalLoadTime.update(loadTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordEviction() {
        recordEviction(DEFAULT_EVICTION_WEIGHT);
    }

    @Override
    public void recordEviction(int weight) {
        evictionCount.mark();
        evictionWeight.mark(weight);
    }

    @Override
    public CacheStats snapshot() {
        return new CacheStats(
                hitCount.getCount(),
                missCount.getCount(),
                loadSuccessCount.getCount(),
                loadFailureCount.getCount(),
                totalLoadTime.getCount(),
                evictionCount.getCount(),
                evictionWeight.getCount());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "snapshot=" + snapshot()
                + ']';
    }
}