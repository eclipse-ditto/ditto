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
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.Test;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Basic test for {@link MetricsStatsCounter}.
 */
public final class MetricsStatsCounterTest {

    private static final String METRICS_PREFIX = "myPrefix";
    private static final long MAXIMUM_SIZE = 20;

    @Test
    public void basicUsage() {
        // GIVEN
        final MetricRegistry registry = new MetricRegistry();
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache(registry);

        // WHEN
        final long requestTimes0 = 3;
        requestNTimes(cache, 0, requestTimes0);
        final long requestTimes1 = 2;
        requestNTimes(cache, 1, requestTimes1);

        // THEN
        waitUntilAsserted(() -> {
            assertThat(getGauge(registry, createMetricName(MetricsStatsCounter.MetricName.MAX_SIZE)).getValue())
                    .isEqualTo(MAXIMUM_SIZE);
            final long expectedEstimatedSize = 2;
            assertThat(getGauge(registry, createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_SIZE)).getValue())
                    .isEqualTo(expectedEstimatedSize);

            // for all keys one miss is expected for first access
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.MISSES)).getCount())
                    .isEqualTo(expectedEstimatedSize);
            final long expectedHits = requestTimes0 + requestTimes1 - expectedEstimatedSize;
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.HITS)).getCount())
                    .isEqualTo(expectedHits);

            final Timer totalLoadTimeTimer =
                    registry.timer(createMetricName(MetricsStatsCounter.MetricName.TOTAL_LOAD_TIME));
            assertThat(totalLoadTimeTimer.getCount())
                    .isEqualTo(expectedEstimatedSize);
            final Duration maxExpectedLoadDuration = Duration.ofSeconds(1);
            assertThat(totalLoadTimeTimer.getMeanRate())
                    .isLessThan(maxExpectedLoadDuration.toNanos());
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.LOADS_SUCCESS)).getCount())
                    .isEqualTo(expectedEstimatedSize);
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.LOADS_FAILURE)).getCount())
                    .isEqualTo(0);

            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.EVICTIONS)).getCount())
                    .isEqualTo(0);
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.EVICTIONS_WEIGHT)).getCount())
                    .isEqualTo(0);

            assertThat(
                    registry.meter(createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_INVALIDATIONS)).getCount())
                    .isEqualTo(0);
        });
    }

    @Test
    public void evictions() {
        // GIVEN
        final MetricRegistry registry = new MetricRegistry();
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache(registry);

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

            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.EVICTIONS)).getCount())
                    .isGreaterThan(0);
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.EVICTIONS_WEIGHT)).getCount())
                    .isGreaterThan(0);

            // invalidations are no evictions
            assertThat(
                    registry.meter(createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_INVALIDATIONS)).getCount())
                    .isEqualTo(0);
        });
    }

    private static void waitUntilAsserted(final ThrowingRunnable throwingRunnable) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(throwingRunnable);
    }

    @Test
    public void invalidate() {
        // GIVEN
        final MetricRegistry registry = new MetricRegistry();
        final CaffeineCache<Integer, Integer> cache = createCaffeineCache(registry);

        final int knownKey = 0;
        cache.get(knownKey);

        assertThat(getGauge(registry, createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_SIZE)).getValue())
                .isEqualTo(1L);

        // WHEN
        cache.invalidate(knownKey);
        final int nonExistingKey = 42;
        cache.invalidate(nonExistingKey);

        // THEN
        waitUntilAsserted(() -> {
            assertThat(
                    registry.meter(createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_INVALIDATIONS)).getCount())
                    .isEqualTo(1);
            assertThat(getGauge(registry, createMetricName(MetricsStatsCounter.MetricName.ESTIMATED_SIZE)).getValue())
                    .isEqualTo(0L);

            // evictions are no invalidations
            assertThat(registry.meter(createMetricName(MetricsStatsCounter.MetricName.EVICTIONS)).getCount())
                    .isEqualTo(0);
        });
    }

    private CaffeineCache<Integer, Integer> createCaffeineCache(final MetricRegistry registry) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_SIZE);
        final AsyncCacheLoader<Integer, Integer> loader = (key, executor) -> CompletableFuture.completedFuture(key);

        return CaffeineCache.of(caffeine, loader,
                new AbstractMap.SimpleImmutableEntry<>(METRICS_PREFIX, registry));
    }

    private Gauge getGauge(final MetricRegistry registry, final String metricName) {
        final Map<String, Gauge> foundMetrics = registry.getGauges((name, metric) -> Objects.equals(name, metricName));
        if (foundMetrics.isEmpty()) {
            throw new IllegalArgumentException("Not found: " + metricName);
        } else {
            return foundMetrics.values().iterator().next();
        }
    }

    private <K, V> void requestNTimes(final Cache<K, V> cache, final K key, final long requests) {
        for (int i = 0; i < requests; i++) {
            cache.get(key);
        }
    }

    private String createMetricName(final MetricsStatsCounter.MetricName metricName) {
        return name(METRICS_PREFIX, metricName.getValue());
    }
}