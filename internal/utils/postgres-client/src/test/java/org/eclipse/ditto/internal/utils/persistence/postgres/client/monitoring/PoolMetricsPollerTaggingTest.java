/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.junit.Test;

import io.r2dbc.pool.PoolMetrics;

/**
 * Each polled pool must publish its gauges under a low-cardinality {@code pool}/{@code role}
 * discriminating tag (NOT {@code pid}), so that more than one pool in a JVM cannot collapse onto the same Kamon
 * instrument (last-writer-wins). It also asserts the per-pool coordinated-shutdown task name is derived from the role
 * so two pollers never register the same fixed task name.
 */
public final class PoolMetricsPollerTaggingTest {

    @Test
    public void everyGaugeCarriesTheEngineAndRoleTagForThatPool() {
        final List<EmittedGauge> emitted = new ArrayList<>();
        final PoolMetrics metrics = new StubPoolMetrics();
        final PoolMetricsPoller poller = PoolMetricsPoller.of(() -> Optional.of(metrics),
                PostgresMetrics.ROLE_SHARED, (name, tags, value) -> emitted.add(new EmittedGauge(name, tags)));

        poller.pollOnce();

        // All four pool gauges must be emitted, each tagged engine=postgres AND pool=<role>.
        assertThat(emitted).hasSize(4);
        assertThat(emitted).allSatisfy(gauge -> {
            assertThat(gauge.tags().getTagValue(PostgresMetrics.TAG_ENGINE))
                    .contains(PostgresMetrics.ENGINE_POSTGRES);
            assertThat(gauge.tags().getTagValue(PostgresMetrics.TAG_POOL))
                    .as("gauge <%s> must carry the discriminating pool/role tag", gauge.name())
                    .contains(PostgresMetrics.ROLE_SHARED);
        });
        assertThat(emitted.stream().map(EmittedGauge::name).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(PostgresMetrics.POOL_ACQUIRED, PostgresMetrics.POOL_ALLOCATED,
                        PostgresMetrics.POOL_IDLE, PostgresMetrics.POOL_PENDING);
        poller.close();
    }

    @Test
    public void distinctRolesProduceDistinctGaugeTagsAndDistinctShutdownTaskNames() {
        // Two pollers for two distinct roles must NOT collide: their gauges differ by the pool tag value and their
        // coordinated-shutdown task names are distinct (the fixed-string-clash regression).
        final List<EmittedGauge> journalGauges = new ArrayList<>();
        final List<EmittedGauge> snapshotGauges = new ArrayList<>();
        final PoolMetrics metrics = new StubPoolMetrics();
        final PoolMetricsPoller journalPoller = PoolMetricsPoller.of(() -> Optional.of(metrics),
                PostgresMetrics.ROLE_JOURNAL, (name, tags, value) -> journalGauges.add(new EmittedGauge(name, tags)));
        final PoolMetricsPoller snapshotPoller = PoolMetricsPoller.of(() -> Optional.of(metrics),
                PostgresMetrics.ROLE_SNAPSHOT, (name, tags, value) -> snapshotGauges.add(new EmittedGauge(name, tags)));

        journalPoller.pollOnce();
        snapshotPoller.pollOnce();

        // Same metric names but a different discriminating pool tag value -> the two pools never overwrite each other.
        assertThat(tagValues(journalGauges, PostgresMetrics.TAG_POOL)).containsExactly(PostgresMetrics.ROLE_JOURNAL);
        assertThat(tagValues(snapshotGauges, PostgresMetrics.TAG_POOL)).containsExactly(PostgresMetrics.ROLE_SNAPSHOT);

        // The coordinated-shutdown task name must be unique per role, not the old fixed string.
        final String journalTask = PoolMetricsPoller.shutdownTaskName(PostgresMetrics.ROLE_JOURNAL);
        final String snapshotTask = PoolMetricsPoller.shutdownTaskName(PostgresMetrics.ROLE_SNAPSHOT);
        assertThat(journalTask).isNotEqualTo(snapshotTask);
        assertThat(journalTask).contains(PostgresMetrics.ROLE_JOURNAL);
        assertThat(snapshotTask).contains(PostgresMetrics.ROLE_SNAPSHOT);

        journalPoller.close();
        snapshotPoller.close();
    }

    private static java.util.Set<String> tagValues(final List<EmittedGauge> gauges, final String key) {
        return gauges.stream()
                .map(gauge -> gauge.tags().getTagValue(key).orElseThrow())
                .collect(Collectors.toSet());
    }

    private record EmittedGauge(String name, TagSet tags) { }

    private static final class StubPoolMetrics implements PoolMetrics {

        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public int acquiredSize() {
            reads.incrementAndGet();
            return 3;
        }

        @Override
        public int allocatedSize() {
            return 10;
        }

        @Override
        public int idleSize() {
            return 7;
        }

        @Override
        public int pendingAcquireSize() {
            return 1;
        }

        @Override
        public int getMaxAllocatedSize() {
            return 100;
        }

        @Override
        public int getMaxPendingAcquireSize() {
            return 100;
        }
    }
}
