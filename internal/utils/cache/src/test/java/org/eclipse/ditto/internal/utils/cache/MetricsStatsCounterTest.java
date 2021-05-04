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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Basic test for {@link MetricsStatsCounter}.
 */
public final class MetricsStatsCounterTest {

    private static final String TEST_CACHE_NAME = "myCache";
    private static final long MAXIMUM_SIZE = 20;

    private final Counter hitCount = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.HITS.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter missCount = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.MISSES.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final PreparedTimer totalLoadTime = DittoMetrics
            .timer(MetricsStatsCounter.MetricName.TOTAL_LOAD_TIME.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter loadSuccessCount = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.LOADS_SUCCESS.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter loadFailureCount = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.LOADS_FAILURE.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter evictionCount = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.EVICTIONS.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter evictionWeight = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.EVICTIONS_WEIGHT.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Gauge estimatedSize = DittoMetrics
            .gauge(MetricsStatsCounter.MetricName.ESTIMATED_SIZE.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Gauge maxSize = DittoMetrics
            .gauge(MetricsStatsCounter.MetricName.MAX_SIZE.getValue())
            .tag("cache_name", TEST_CACHE_NAME);
    private final Counter estimatedInvalidations = DittoMetrics
            .counter(MetricsStatsCounter.MetricName.ESTIMATED_INVALIDATIONS.getValue())
            .tag("cache_name", TEST_CACHE_NAME);

    @Before
    public void resetMetrics() {
        hitCount.reset();
        missCount.reset();
        totalLoadTime.reset();
        loadSuccessCount.reset();
        loadFailureCount.reset();
        evictionCount.reset();
        evictionWeight.reset();
        estimatedSize.reset();
        maxSize.reset();
        estimatedInvalidations.reset();
    }

    @Test
    public void basicUsage() {
        // GIVEN
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache();

        // WHEN
        final long requestTimes0 = 3;
        requestNTimes(cache, 0, requestTimes0);
        final long requestTimes1 = 2;
        requestNTimes(cache, 1, requestTimes1);

        // THEN
        waitUntilAsserted(() -> {
            assertThat(maxSize.get()).isEqualTo(MAXIMUM_SIZE);
            final long expectedEstimatedSize = 2;
            assertThat(estimatedSize.get()).isEqualTo(expectedEstimatedSize);

            // for all keys one miss is expected for first access
            assertThat(missCount.getCount()).isEqualTo(expectedEstimatedSize);
            final long expectedHits = requestTimes0 + requestTimes1 - expectedEstimatedSize;
            assertThat(hitCount.getCount()).isEqualTo(expectedHits);

            assertThat(totalLoadTime.getNumberOfRecords()).isEqualTo(expectedEstimatedSize);
            assertThat(loadSuccessCount.getCount()).isEqualTo(expectedEstimatedSize);
            assertThat(loadFailureCount.getCount()).isEqualTo(0);

            assertThat(evictionCount.getCount()).isEqualTo(0);
            assertThat(evictionWeight.getCount()).isEqualTo(0);

            assertThat(estimatedInvalidations.getCount()).isEqualTo(0);
        });
    }

    @Test
    public void evictions() {
        // GIVEN
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache();

        // WHEN
        final int cacheExceedingElementsCount = 300;
        for (int i = 0; i < (MAXIMUM_SIZE + cacheExceedingElementsCount); i++) {
            cache.get(i);
        }

        // THEN
        waitUntilAsserted(() -> {
            /* It is not exactly testable whether and when the actual size of the cache changes and when evictions
               are applied, because Caffeine uses "Window TinyLfu" cache eviction policy,
               see https://github.com/ben-manes/caffeine/wiki/Efficiency
            */

            assertThat(evictionCount.getCount()).isGreaterThan(0);
            assertThat(evictionWeight.getCount()).isGreaterThan(0);

            // invalidations are no evictions
            assertThat(estimatedInvalidations.getCount()).isEqualTo(0);
        });
    }

    private static void waitUntilAsserted(final ThrowingRunnable throwingRunnable) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(throwingRunnable);
    }

    @Test
    public void invalidate() {
        // GIVEN
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache();

        final int knownKey = 0;
        cache.get(knownKey);

        assertThat(estimatedSize.get()).isEqualTo(1L);

        // WHEN
        cache.invalidate(knownKey);
        final int nonExistingKey = 42;
        cache.invalidate(nonExistingKey);

        // THEN
        waitUntilAsserted(() -> {
            assertThat(estimatedInvalidations.getCount()).isEqualTo(1);
            assertThat(estimatedSize.get()).isEqualTo(0L);

            // evictions are no invalidations
            assertThat(evictionCount.getCount()).isEqualTo(0);
        });
    }

    private CaffeineCache<Integer, Integer> createCaffeineCache() {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder().maximumSize(MAXIMUM_SIZE);
        final AsyncCacheLoader<Integer, Integer> loader = (key, executor) -> CompletableFuture.completedFuture(key);

        return CaffeineCache.of(caffeine, loader, TEST_CACHE_NAME);
    }

    private <K, V> void requestNTimes(final Cache<K, V> cache, final K key, final long requests) {
        for (int i = 0; i < requests; i++) {
            cache.get(key);
        }
    }
}
