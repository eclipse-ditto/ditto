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

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.PreparedTimer;

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

    private final Counter hitCount;
    private final Counter missCount;
    private final Counter loadSuccessCount;
    private final Counter loadFailureCount;
    private final PreparedTimer totalLoadTime;
    private final Counter evictionCount;
    private final Counter evictionWeight;
    private final Gauge estimatedSize;
    private final Gauge maxSize;
    private final Counter estimatedInvalidations;

    private volatile @Nullable Cache cache;

    private MetricsStatsCounter(final String metricsPrefix) {
        hitCount = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.HITS));
        missCount = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.MISSES));
        totalLoadTime = DittoMetrics.timer(createMetricName(metricsPrefix, MetricName.TOTAL_LOAD_TIME));
        loadSuccessCount = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.LOADS_SUCCESS));
        loadFailureCount = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.LOADS_FAILURE));
        evictionCount = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.EVICTIONS));
        evictionWeight = DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.EVICTIONS_WEIGHT));
        estimatedSize = DittoMetrics.gauge(createMetricName(metricsPrefix, MetricName.ESTIMATED_SIZE));
        maxSize = DittoMetrics.gauge(createMetricName(metricsPrefix, MetricName.MAX_SIZE));
        estimatedInvalidations =
                DittoMetrics.counter(createMetricName(metricsPrefix, MetricName.ESTIMATED_INVALIDATIONS));
    }

    private String createMetricName(final String metricsPrefix, final MetricName metricName) {
        return name(metricsPrefix, metricName.getValue());
    }

    /**
     * Creates an instance to be used by a single cache.
     *
     * @param metricsPrefix the prefix name for the metrics.
     * @return the instance.
     */
    static MetricsStatsCounter of(final String metricsPrefix) {
        requireNonNull(metricsPrefix);

        return new MetricsStatsCounter(metricsPrefix);
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
        hitCount.increment(count);
    }

    @Override
    public void recordMisses(int count) {
        missCount.increment(count);
    }

    @Override
    public void recordLoadSuccess(long loadTimeInNanos) {
        loadSuccessCount.increment();
        totalLoadTime.record(loadTimeInNanos, TimeUnit.NANOSECONDS);
        updateCacheSizeMetrics();
    }

    @Override
    public void recordLoadFailure(long loadTimeInNanos) {
        loadFailureCount.increment();
        totalLoadTime.record(loadTimeInNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordEviction() {
        recordEviction(DEFAULT_EVICTION_WEIGHT);
        updateCacheSizeMetrics();
    }

    @Override
    public void recordEviction(int weight) {
        evictionCount.increment();
        evictionWeight.increment(weight);
        updateCacheSizeMetrics();
    }

    /**
     * Records the invalidation of an entry in the cache.
     */
    public void recordInvalidation() {
        estimatedInvalidations.increment();
        updateCacheSizeMetrics();
    }

    @Override
    public CacheStats snapshot() {
        return new CacheStats(
                hitCount.getCount(),
                missCount.getCount(),
                loadSuccessCount.getCount(),
                loadFailureCount.getCount(),
                Arrays.stream(totalLoadTime.getRecords()).mapToLong(Long::longValue).sum(),
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
            estimatedSize.set(getRequiredCache().estimatedSize());
            final Long currentMaxCacheSize = getMaxSize();
            if (currentMaxCacheSize != null) {
                maxSize.set(currentMaxCacheSize);
            }
        }
    }

    @Nullable
    private Long getMaxSize() {
        @SuppressWarnings("unchecked") final Optional<Policy.Eviction> evictionOptional =
                getRequiredCache().policy().eviction();
        return evictionOptional.map(Policy.Eviction::getMaximum).orElse(null);
    }

    private Cache getRequiredCache() {
        return requireNonNull(cache);
    }
}