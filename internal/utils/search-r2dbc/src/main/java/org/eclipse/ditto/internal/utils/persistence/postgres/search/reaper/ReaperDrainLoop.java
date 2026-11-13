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

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import reactor.core.publisher.Mono;

/**
 * The pure, backend-agnostic batched-drain loop at the heart of the {@code delete_at} reaper (plan §3.6). Knows nothing
 * about PostgreSQL, r2dbc, connections or advisory locks — it only repeats a caller-supplied "delete one batch, tell me
 * how many rows it deleted" function until either:
 * <ul>
 *     <li>a batch deletes <strong>fewer</strong> rows than {@code batchSize} — proof the backlog is fully drained
 *     (the mandated SQL shape's {@code LIMIT batchSize} means a short batch cannot be followed by a longer one), or</li>
 *     <li>{@code maxBatchesPerTick} batches have run — the safety valve, so one tick's unit of work is bounded even
 *     against an arbitrarily large backlog (e.g. right after a large namespace purge). The remaining backlog is picked
 *     up by the next scheduled tick, never blocking the current one indefinitely.</li>
 * </ul>
 * Kept separate from {@link PostgresDeleteAtReaper} (which supplies the real {@code batchDeleter} bound to a live
 * connection/transaction) precisely so this control-flow logic is unit-testable with a canned {@link Function} and no
 * database at all.
 */
@Immutable
final class ReaperDrainLoop {

    private ReaperDrainLoop() {
        throw new AssertionError();
    }

    /**
     * Runs the drain loop.
     *
     * @param batchDeleter deletes (up to) one batch and reports how many rows it actually deleted; invoked with
     * {@code batchSize} as the batch's row-count limit each time.
     * @param batchSize the per-batch row-count limit (also the threshold that signals "fully drained" when a batch
     * deletes fewer rows than this).
     * @param maxBatchesPerTick the safety valve — at most this many batches run before the loop stops regardless of
     * whether the backlog is fully drained.
     * @return a {@link Mono} of the {@link DrainResult} once the loop stops (drained or safety-valve-limited); errors
     * from {@code batchDeleter} propagate unchanged (the caller — {@link PostgresDeleteAtReaper} — rolls back the whole
     * tick's transaction on any error, so a failed batch never leaves a partial deletion committed).
     */
    static Mono<DrainResult> run(final Function<Integer, Mono<Long>> batchDeleter, final int batchSize,
            final int maxBatchesPerTick) {
        return drain(batchDeleter, batchSize, maxBatchesPerTick, 0, 0L);
    }

    private static Mono<DrainResult> drain(final Function<Integer, Mono<Long>> batchDeleter, final int batchSize,
            final int maxBatchesPerTick, final int batchesRunSoFar, final long totalDeletedSoFar) {
        if (batchesRunSoFar >= maxBatchesPerTick) {
            return Mono.just(new DrainResult(totalDeletedSoFar, batchesRunSoFar, true));
        }
        return batchDeleter.apply(batchSize).flatMap(deletedThisBatch -> {
            final int batchesRun = batchesRunSoFar + 1;
            final long totalDeleted = totalDeletedSoFar + deletedThisBatch;
            if (deletedThisBatch < batchSize) {
                // a short batch proves nothing more matches right now -- fully drained, no safety valve involved.
                return Mono.just(new DrainResult(totalDeleted, batchesRun, false));
            }
            return drain(batchDeleter, batchSize, maxBatchesPerTick, batchesRun, totalDeleted);
        });
    }

    /**
     * The outcome of one drain loop.
     *
     * @param totalDeleted the total number of rows deleted across every batch run in this loop.
     * @param batchesRun how many batches actually ran.
     * @param safetyValveHit {@code true} if the loop stopped because {@code maxBatchesPerTick} was reached (backlog
     * may remain), {@code false} if it stopped because a batch deleted fewer than {@code batchSize} rows (drained).
     */
    record DrainResult(long totalDeleted, int batchesRun, boolean safetyValveHit) {}

}
