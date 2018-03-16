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

import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * Basic test for {@link MetricsStatsCounter}.
 */
public final class MetricsStatsCounterTest {

    private static final String METRICS_PREFIX = "myPrefix";

    @Test
    public void stats() {
        // GIVEN
        final MetricRegistry registry = new MetricRegistry();
        final LoadingCache<Integer, Integer> cache = Caffeine.newBuilder()
                .recordStats(() -> MetricsStatsCounter.of(METRICS_PREFIX, registry))
                .build(key -> key);

        // WHEN
        for (int i = 0; i < 3; i++) {
            cache.get(0);
        }

        // THEN
        // first call misses cache, then result is cached
        assertThat(cache.stats().missCount()).isEqualTo(1);
        assertThat(cache.stats().hitCount()).isEqualTo(2);
        assertThat(registry.meter(name(METRICS_PREFIX, MetricsStatsCounter.MetricName.MISSES.getValue())).getCount())
                .isEqualTo(1);
        assertThat(registry.meter(name(METRICS_PREFIX, MetricsStatsCounter.MetricName.HITS.getValue())).getCount())
                .isEqualTo(2);
    }
}