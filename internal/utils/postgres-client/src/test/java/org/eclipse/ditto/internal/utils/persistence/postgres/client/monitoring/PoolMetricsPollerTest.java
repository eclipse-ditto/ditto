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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Test;

import io.r2dbc.pool.PoolMetrics;

/**
 * Unit test for {@link PoolMetricsPoller}: a single tick reads the supplier and the {@link PoolMetrics} counts without
 * throwing; an empty {@code Optional} (pool exposes no metrics) is a no-op; a throwing poll is swallowed and does not
 * stop subsequent polls; and {@code close()} cancels the schedule.
 */
public final class PoolMetricsPollerTest {

    @Test
    public void pollOnceReadsAllPoolCounts() {
        final AtomicInteger reads = new AtomicInteger();
        final PoolMetrics metrics = new StubPoolMetrics(reads);
        final PoolMetricsPoller poller = PoolMetricsPoller.of(() -> Optional.of(metrics), PostgresMetrics.ROLE_SHARED);

        assertThatCode(poller::pollOnce).doesNotThrowAnyException();

        // acquired + allocated + idle + pending = 4 reads in one tick
        assertThat(reads.get()).isEqualTo(4);
        poller.close();
    }

    @Test
    public void pollOnceIsNoOpWhenPoolExposesNoMetrics() {
        final PoolMetricsPoller poller = PoolMetricsPoller.of(Optional::empty, PostgresMetrics.ROLE_SHARED);
        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        poller.close();
    }

    @Test
    public void throwingPollIsSwallowedSoSubsequentPollsKeepRunning() {
        // A disposed/closed pool throws from the supplier; pollOnce must NOT propagate, otherwise
        // scheduleAtFixedRate would silently abandon the task forever.
        final AtomicInteger calls = new AtomicInteger();
        final Supplier<Optional<PoolMetrics>> throwingSupplier = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("pool disposed");
        };
        final PoolMetricsPoller poller = PoolMetricsPoller.of(throwingSupplier, PostgresMetrics.ROLE_SHARED);

        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        // A second tick still happens (the task was not killed by the first throw).
        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(2);
        poller.close();
    }

    @Test
    public void closeCancelsTheScheduleAndIsIdempotent() {
        final AtomicInteger reads = new AtomicInteger();
        final PoolMetrics metrics = new StubPoolMetrics(reads);
        // A long interval means at most the immediate tick fires; close() must cancel any further scheduled run.
        final PoolMetricsPoller poller =
                PoolMetricsPoller.of(() -> Optional.of(metrics), PostgresMetrics.ROLE_SHARED, Duration.ofHours(1L));

        poller.start();
        // close() cancels the retained future and shuts the scheduler down without throwing; calling it twice is safe.
        assertThatCode(poller::close).doesNotThrowAnyException();
        assertThatCode(poller::close).doesNotThrowAnyException();
    }

    @Test
    public void supplierAssertionErrorIsSwallowedSoSubsequentPollsKeepRunning() {
        // A non-RuntimeException Error from the supplier (e.g. an AssertionError) must NOT propagate, otherwise
        // scheduleAtFixedRate would silently abandon the task forever — the next tick must still run.
        final AtomicInteger calls = new AtomicInteger();
        final Supplier<Optional<PoolMetrics>> assertionThrowingSupplier = () -> {
            calls.incrementAndGet();
            throw new AssertionError("boom");
        };
        final PoolMetricsPoller poller =
                PoolMetricsPoller.of(assertionThrowingSupplier, PostgresMetrics.ROLE_SHARED);

        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        // A second tick still happens (the task was not killed by the first throw).
        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(2);
        poller.close();
    }

    @Test
    public void supplierOutOfMemoryErrorPropagatesAndIsNotSwallowed() {
        // A JVM-fatal VirtualMachineError must propagate untouched — swallowing/logging it would hide a fatal
        // condition. pollOnce must rethrow it rather than treat it like a transient pool error.
        final Supplier<Optional<PoolMetrics>> oomSupplier = () -> {
            throw new OutOfMemoryError("simulated");
        };
        final PoolMetricsPoller poller = PoolMetricsPoller.of(oomSupplier, PostgresMetrics.ROLE_SHARED);

        assertThatThrownBy(poller::pollOnce)
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated");
        poller.close();
    }

    @Test
    public void closeResetsTheFourPoolGaugesToZeroAfterCancelling() {
        final AtomicInteger reads = new AtomicInteger();
        final PoolMetrics metrics = new StubPoolMetrics(reads);
        final List<GaugeEmission> emissions = new ArrayList<>();
        final PoolMetricsPoller poller = PoolMetricsPoller.of(() -> Optional.of(metrics), PostgresMetrics.ROLE_SHARED,
                (name, tags, value) -> emissions.add(new GaugeEmission(name, value)));

        poller.start();
        emissions.clear(); // drop whatever the immediate tick published; we assert only what close() emits.

        poller.close();

        // close() must emit exactly the four pool gauges, all reset to 0.
        final Map<String, Integer> resetValues = emissions.stream()
                .collect(Collectors.toMap(GaugeEmission::name, GaugeEmission::value, (a, b) -> b));
        assertThat(resetValues).containsOnlyKeys(
                PostgresMetrics.POOL_ACQUIRED,
                PostgresMetrics.POOL_ALLOCATED,
                PostgresMetrics.POOL_IDLE,
                PostgresMetrics.POOL_PENDING);
        assertThat(resetValues.values()).allMatch(v -> v == 0);
    }

    @Test
    public void closeCancelsTheScheduleBeforeResettingGauges() {
        // Ordering matters: the scheduled task must be cancelled FIRST so an in-flight tick cannot re-publish a
        // stale value after the reset. We observe the order by checking, from inside the reset sink, that the
        // scheduled future is already cancelled at the moment the gauges are emitted.
        final AtomicInteger reads = new AtomicInteger();
        final PoolMetrics metrics = new StubPoolMetrics(reads);
        final List<Boolean> cancelledAtResetTime = new ArrayList<>();
        final PoolMetricsPoller[] holder = new PoolMetricsPoller[1];
        final PoolMetricsPoller poller = PoolMetricsPoller.of(() -> Optional.of(metrics), PostgresMetrics.ROLE_SHARED,
                (name, tags, value) -> {
                    if (value == 0) {
                        cancelledAtResetTime.add(holder[0].isScheduledTaskCancelled());
                    }
                });
        holder[0] = poller;

        poller.start();

        poller.close();

        // Every reset emission observed the scheduled task as already cancelled — cancel happened before reset.
        assertThat(cancelledAtResetTime).isNotEmpty();
        assertThat(cancelledAtResetTime).allMatch(Boolean::booleanValue);
    }

    private record GaugeEmission(String name, int value) {}

    private record StubPoolMetrics(AtomicInteger reads) implements PoolMetrics {

        @Override
        public int acquiredSize() {
            reads.incrementAndGet();
            return 3;
        }

        @Override
        public int allocatedSize() {
            reads.incrementAndGet();
            return 10;
        }

        @Override
        public int idleSize() {
            reads.incrementAndGet();
            return 7;
        }

        @Override
        public int pendingAcquireSize() {
            reads.incrementAndGet();
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
