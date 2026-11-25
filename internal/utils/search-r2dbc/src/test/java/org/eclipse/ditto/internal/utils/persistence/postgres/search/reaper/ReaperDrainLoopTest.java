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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.ReaperDrainLoop.DrainResult;
import org.junit.Test;

import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link ReaperDrainLoop} — the pure drain-loop control flow, with a canned {@code batchDeleter}
 * function standing in for the real SQL/connection (no database involved at all).
 */
public final class ReaperDrainLoopTest {

    @Test
    public void singleEmptyBatchDrainsImmediately() {
        final DrainResult result = run(deleterReturning(0L), 100, 50);

        assertThat(result.totalDeleted()).isZero();
        assertThat(result.batchesRun()).isEqualTo(1);
        assertThat(result.safetyValveHit()).isFalse();
    }

    @Test
    public void aPartialFinalBatchProvesFullyDrained() {
        final DrainResult result = run(deleterReturning(100L, 100L, 40L), 100, 50);

        assertThat(result.totalDeleted()).isEqualTo(240L);
        assertThat(result.batchesRun()).isEqualTo(3);
        assertThat(result.safetyValveHit()).isFalse();
    }

    @Test
    public void safetyValveStopsAnEverFullBacklogAtMaxBatches() {
        final AtomicInteger calls = new AtomicInteger();
        final Function<Integer, Mono<Long>> alwaysFull = batchSize -> {
            calls.incrementAndGet();
            return Mono.just((long) batchSize);
        };

        final DrainResult result = run(alwaysFull, 100, 3);

        assertThat(result.totalDeleted()).isEqualTo(300L);
        assertThat(result.batchesRun()).isEqualTo(3);
        assertThat(result.safetyValveHit()).isTrue();
        assertThat(calls.get()).as("the deleter must not be invoked a 4th time past the safety valve").isEqualTo(3);
    }

    @Test
    public void batchSizeIsPassedThroughToTheDeleterEachTime() {
        final List<Integer> observedLimits = new java.util.ArrayList<>();
        final Function<Integer, Mono<Long>> recordingThenEmpty = limit -> {
            observedLimits.add(limit);
            return Mono.just(0L);
        };

        run(recordingThenEmpty, 7, 50);

        assertThat(observedLimits).containsExactly(7);
    }

    @Test
    public void anErroringBatchPropagatesTheErrorRatherThanSwallowingIt() {
        final Deque<Mono<Long>> results = new ArrayDeque<>(List.of(
                Mono.just(10L),
                Mono.error(new IllegalStateException("simulated batch failure"))));
        final Function<Integer, Mono<Long>> flaky = limit -> results.poll();

        assertThatThrownBy(() -> run(flaky, 10, 50))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated batch failure");
    }

    @Test
    public void zeroMaxBatchesStopsImmediatelyWithoutCallingTheDeleterAtAll() {
        final AtomicInteger calls = new AtomicInteger();
        final Function<Integer, Mono<Long>> counting = limit -> {
            calls.incrementAndGet();
            return Mono.just(999L);
        };

        final DrainResult result = run(counting, 100, 0);

        assertThat(result.totalDeleted()).isZero();
        assertThat(result.batchesRun()).isZero();
        assertThat(result.safetyValveHit()).isTrue();
        assertThat(calls.get()).isZero();
    }

    private static Function<Integer, Mono<Long>> deleterReturning(final Long... sequence) {
        final Deque<Long> queue = new ArrayDeque<>(List.of(sequence));
        return limit -> Mono.just(queue.poll());
    }

    private static DrainResult run(final Function<Integer, Mono<Long>> batchDeleter, final int batchSize,
            final int maxBatchesPerTick) {
        return ReaperDrainLoop.run(batchDeleter, batchSize, maxBatchesPerTick).block();
    }

}
