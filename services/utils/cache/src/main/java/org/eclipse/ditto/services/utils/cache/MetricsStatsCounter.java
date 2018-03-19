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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
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
         * The total load time in nanoseconds.
         */
        TOTAL_LOAD_TIME("loads"),
        /**
         * Number of successful loads.
         */
        LOADS_SUCCESS("loads-success"),
        /**
         * Number of failed loads.
         */
        LOADS_FAILURE("loads-failure"),
        /**
         * Number of cache evictions, e.g. when the cache grows to large. Manual cache invalidation is NOT included,
         * it is counted by {@link #ESTIMATED_INVALIDATIONS}.
         */
        EVICTIONS("evictions"),
        /**
         * Sum of the weights of the cache evictions.
         */
        EVICTIONS_WEIGHT("evictions-weight"),
        /**
         * The estimated size of the cache.
         */
        ESTIMATED_SIZE("estimated-size"),
        /**
         * The maximum size of the cache.
         */
        MAX_SIZE("max-size"),
        /**
         * Estimated cache invalidations (manual, in contrast to {@link #EVICTIONS}). The value is estimated, it may
         * be not completely correct in case of parallel loads or evictions.
         */
        ESTIMATED_INVALIDATIONS("estimated-invalidations");

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
    private final SimpleGauge<Long> estimatedSize;
    private final SimpleGauge<Long> maxSize;
    private final Meter estimatedInvalidations;

    private volatile @Nullable Cache cache;

    private MetricsStatsCounter(final MetricRegistry registry, final String metricsPrefix) {
        hitCount = registry.meter(createMetricName(metricsPrefix, MetricName.HITS));
        missCount = registry.meter(createMetricName(metricsPrefix, MetricName.MISSES));
        totalLoadTime = registry.timer(createMetricName(metricsPrefix, MetricName.TOTAL_LOAD_TIME));
        loadSuccessCount = registry.meter(createMetricName(metricsPrefix, MetricName.LOADS_SUCCESS));
        loadFailureCount = registry.meter(createMetricName(metricsPrefix, MetricName.LOADS_FAILURE));
        evictionCount = registry.meter(createMetricName(metricsPrefix, MetricName.EVICTIONS));
        evictionWeight = registry.meter(createMetricName(metricsPrefix, MetricName.EVICTIONS_WEIGHT));
        estimatedSize = registry.register(createMetricName(metricsPrefix, MetricName.ESTIMATED_SIZE),
                new SimpleGauge<>(null));
        maxSize = registry.register(createMetricName(metricsPrefix, MetricName.MAX_SIZE),
                new SimpleGauge<>(null));
        estimatedInvalidations = registry.meter(createMetricName(metricsPrefix, MetricName.ESTIMATED_INVALIDATIONS));
    }

    private String createMetricName(final String metricsPrefix, final MetricName metricName) {
        return name(metricsPrefix, metricName.getValue());
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

    /**
     * Configures the given cache, which allows reporting of estimated and maximum size.
     *
     * @param cache the cache.
     */
    public void configureCache(final Cache cache) {
        if (this.cache != null) {
            throw new IllegalStateException("Cache has already been configured!");
        }
        this.cache = requireNonNull(cache);
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
        updateCacheSizeMetrics();
    }

    @Override
    public void recordLoadFailure(long loadTime) {
        loadFailureCount.mark();
        totalLoadTime.update(loadTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordEviction() {
        recordEviction(DEFAULT_EVICTION_WEIGHT);
        updateCacheSizeMetrics();
    }

    @Override
    public void recordEviction(int weight) {
        evictionCount.mark();
        evictionWeight.mark(weight);
        updateCacheSizeMetrics();
    }

    /**
     * Records the invalidation of an entry in the cache.
     */
    public void recordInvalidation() {
        estimatedInvalidations.mark();
        updateCacheSizeMetrics();
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

    private void updateCacheSizeMetrics() {
        if (cache != null) {
            estimatedSize.setValue(getRequiredCache().estimatedSize());
            final Long currentMaxCacheSize = getMaxSize();
            if (currentMaxCacheSize != null) {
                maxSize.setValue(currentMaxCacheSize);
            }
        }
    }

    @Nullable
    private Long getMaxSize() {
        @SuppressWarnings("unchecked")
        final Optional<Policy.Eviction> evictionOptional = getRequiredCache().policy().eviction();
        return evictionOptional.map(Policy.Eviction::getMaximum).orElse(null);
    }

    private Cache getRequiredCache() {
        return requireNonNull(cache);
    }

    /**
     * Simple {@link Gauge} implementation.
     */
    private static final class SimpleGauge<T> implements Gauge<T> {

        @Nullable
        private volatile T value;

        private SimpleGauge(@Nullable T value) {
            this.value = value;
        }

        @Nullable
        @Override
        public T getValue() {
            return this.value;
        }

        public void setValue(T value) {
            this.value = value;
        }

    }
}