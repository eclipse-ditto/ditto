/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.cache;

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
        ESTIMATED_INVALIDATIONS(CACHE_PREFIX + "_estimated-invalidations");

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

    private volatile @Nullable Cache cache;

    private MetricsStatsCounter(final String cacheName) {
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
    }

    /**
     * Creates an instance to be used by a single cache.
     *
     * @param cacheName The name of the cache.
     * @return the instance.
     */
    static MetricsStatsCounter of(final String cacheName) {
        requireNonNull(cacheName);

        return new MetricsStatsCounter(cacheName);
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
