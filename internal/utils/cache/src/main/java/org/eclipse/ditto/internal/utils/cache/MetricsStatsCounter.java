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
package org.eclipse.ditto.internal.utils.cache;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;


/**
 * <p>
 * A caffeine {@link StatsCounter} implementation for Ditto Metrics.
 * </p>
 * <p>
 * Inspired by <a href="https://github.com/ben-manes/caffeine/blob/master/examples/stats-metrics">Caffeine examples</a>.
 * </p>
 *
 * @see CacheStats for details on how the statistics are calculated.
 */
public final class MetricsStatsCounter implements StatsCounter {

    private static final String CACHE_PREFIX = "cache";

    /**
     * The names of the Metrics provided by this counter.
     */
    public enum MetricName {

        /**
         * Cache hits.
         */
        HITS(CACHE_PREFIX + "_hits"),
        /**
         * Cache misses.
         */
        MISSES(CACHE_PREFIX + "_misses"),
        /**
         * The total load time in nanoseconds.
         */
        TOTAL_LOAD_TIME(CACHE_PREFIX + "_loads"),
        /**
         * Number of successful loads.
         */
        LOADS_SUCCESS(CACHE_PREFIX + "_loads-success"),
        /**
         * Number of failed loads.
         */
        LOADS_FAILURE(CACHE_PREFIX + "_loads-failure"),
        /**
         * Number of cache evictions, e.g. when the cache grows to large. Manual cache invalidation is NOT included,
         * it is counted by {@link #ESTIMATED_INVALIDATIONS}.
         */
        EVICTIONS(CACHE_PREFIX + "_evictions"),
        /**
         * Sum of the weights of the cache evictions.
         */
        EVICTIONS_WEIGHT(CACHE_PREFIX + "_evictions-weight"),
        /**
         * The estimated size of the cache.
         */
        ESTIMATED_SIZE(CACHE_PREFIX + "_estimated-size"),
        /**
         * The maximum size of the cache.
         */
        MAX_SIZE(CACHE_PREFIX + "_max-size"),
        /**
         * Estimated cache invalidations (manual, in contrast to {@link #EVICTIONS}). The value is estimated, it may
         * be not completely correct in case of parallel loads or evictions.
         */
        ESTIMATED_INVALIDATIONS(CACHE_PREFIX + "_estimated-invalidations"),
        /**
         * Estimated cache invalidations (manual, in contrast to {@link #EVICTIONS}) that did not invalidate an item
         * because it didn't exist in cache.
         */
        ESTIMATED_INVALIDATIONS_WITHOUT_ITEM(CACHE_PREFIX + "_estimated-invalidations-without-item");

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

    private static final String CACHE_NAME_TAG = "cache_name";

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
    private final Counter estimatedInvalidationsWithoutItem;
    private final Supplier<Long> maxSizeSupplier;
    private final Supplier<Long> estimatedSizeSupplier;

    private MetricsStatsCounter(final String cacheName, final Supplier<Long> maxSizeSupplier,
            final Supplier<Long> estimatedSizeSupplier) {
        hitCount = DittoMetrics.counter(MetricName.HITS.getValue()).tag(CACHE_NAME_TAG, cacheName);
        missCount = DittoMetrics.counter(MetricName.MISSES.getValue()).tag(CACHE_NAME_TAG, cacheName);
        totalLoadTime = DittoMetrics.timer(MetricName.TOTAL_LOAD_TIME.getValue()).tag(CACHE_NAME_TAG, cacheName);
        loadSuccessCount = DittoMetrics.counter(MetricName.LOADS_SUCCESS.getValue()).tag(CACHE_NAME_TAG, cacheName);
        loadFailureCount = DittoMetrics.counter(MetricName.LOADS_FAILURE.getValue()).tag(CACHE_NAME_TAG, cacheName);
        evictionCount = DittoMetrics.counter(MetricName.EVICTIONS.getValue()).tag(CACHE_NAME_TAG, cacheName);
        evictionWeight = DittoMetrics.counter(MetricName.EVICTIONS_WEIGHT.getValue()).tag(CACHE_NAME_TAG, cacheName);
        estimatedSize = DittoMetrics.gauge(MetricName.ESTIMATED_SIZE.getValue()).tag(CACHE_NAME_TAG, cacheName);
        maxSize = DittoMetrics.gauge(MetricName.MAX_SIZE.getValue()).tag(CACHE_NAME_TAG, cacheName);
        estimatedInvalidations =
                DittoMetrics.counter(MetricName.ESTIMATED_INVALIDATIONS.getValue()).tag(CACHE_NAME_TAG, cacheName);
        estimatedInvalidationsWithoutItem =
                DittoMetrics.counter(MetricName.ESTIMATED_INVALIDATIONS_WITHOUT_ITEM.getValue())
                        .tag(CACHE_NAME_TAG, cacheName);
        this.maxSizeSupplier = maxSizeSupplier;
        this.estimatedSizeSupplier = estimatedSizeSupplier;
    }

    /**
     * Creates an instance to be used by a single cache.
     *
     * @param cacheName The name of the cache.
     * @param maxSizeSupplier supplier for the maximum size of the cache
     * @param estimatedSizeSupplier supplier for the estimated size of the cache.
     * @return the instance.
     */
    static MetricsStatsCounter of(final String cacheName, final Supplier<Long> maxSizeSupplier,
            final Supplier<Long> estimatedSizeSupplier) {
        return new MetricsStatsCounter(cacheName, maxSizeSupplier, estimatedSizeSupplier);
    }

    @Override
    public void recordHits(final int count) {
        hitCount.increment(count);
    }

    @Override
    public void recordMisses(final int count) {
        missCount.increment(count);
    }

    @Override
    public void recordLoadSuccess(final long loadTimeInNanos) {
        loadSuccessCount.increment();
        totalLoadTime.record(loadTimeInNanos, TimeUnit.NANOSECONDS);
        updateCacheSizeMetrics();
    }

    @Override
    public void recordLoadFailure(final long loadTimeInNanos) {
        loadFailureCount.increment();
        totalLoadTime.record(loadTimeInNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordEviction(final int weight, final RemovalCause removalCause) {
        evictionCount.increment();
        evictionWeight.increment(weight);
        updateCacheSizeMetrics();
    }

    /**
     * Records the invalidation of an entry in the cache.
     */
    void recordInvalidation() {
        estimatedInvalidations.increment();
        updateCacheSizeMetrics();
    }

    void recordInvalidationWithoutItem() {
        estimatedInvalidationsWithoutItem.increment();
    }

    @Override
    public CacheStats snapshot() {
        return CacheStats.of(
                hitCount.getCount(),
                missCount.getCount(),
                loadSuccessCount.getCount(),
                loadFailureCount.getCount(),
                totalLoadTime.getTotalTime(),
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
        maxSize.set(maxSizeSupplier.get());
        estimatedSize.set(estimatedSizeSupplier.get());
    }

}
