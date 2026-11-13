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

import java.time.Duration;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaper.TickResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;

/**
 * Thin scheduling/logging/metrics wrapper around {@link PostgresDeleteAtReaper} (plan §3.6). All actual SQL/lock/drain
 * logic lives in {@link PostgresDeleteAtReaper} and {@link ReaperDrainLoop} (unit-testable without an actor system);
 * this actor only owns the tick-scheduling loop and turns a {@link TickResult} (or a failure) into logs and a metric.
 * <p>
 * <strong>Scheduling — self-rescheduling, not a fixed-rate timer.</strong> Each tick is scheduled only after the
 * previous one (including its logging) has completed, via a plain {@code scheduler.scheduleOnce} from
 * {@link #preStart()} and again at the end of every tick's handling. This guarantees ticks never overlap in this actor
 * — a fixed-rate/fixed-delay timer could fire a second {@code Tick} message while a slow tick is still in flight,
 * pointlessly racing its own advisory-lock attempt (harmlessly rejected, but wasted work) against itself.
 * </p>
 * <p>
 * <strong>Resilience (brief req 2).</strong> A tick that errors (a lost connection, a transient DB error, …) is caught
 * here — never rethrown, never handed to Pekko's supervision — and logged at WARN; the next tick is scheduled exactly
 * as if the tick had succeeded with zero rows reaped. A crash-looping reaper is worse than a reaper that quietly skips
 * a tick and tries again on the next interval.
 * </p>
 *
 * @since 4.0.0
 */
public final class PostgresDeleteAtReaperActor extends AbstractActor {

    /**
     * The actor's name under the actor system it is started in (one per JVM — see
     * {@code PostgresSearchPersistenceProvider}'s idempotent start guard).
     */
    public static final String ACTOR_NAME = "postgresSearchDeleteAtReaper";

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDeleteAtReaperActor.class);

    /** Package-private (not private) so tests can look up the same Kamon counter by name. */
    static final String REAPED_ROWS_COUNTER_NAME = "postgres_search_reaper_reaped_things";

    private final PostgresDeleteAtReaper reaper;
    private final Duration interval;
    private final Counter reapedRowsCounter;

    @SuppressWarnings("unused")
    private PostgresDeleteAtReaperActor(final PostgresDeleteAtReaper reaper, final Duration interval) {
        this.reaper = reaper;
        this.interval = interval;
        this.reapedRowsCounter = DittoMetrics.counter(REAPED_ROWS_COUNTER_NAME);
    }

    /**
     * @param reaper the reaper the actor drives (holds the connection factory + batch/max-batches config).
     * @param config supplies the tick interval.
     * @return the actor {@link Props}.
     */
    public static Props props(final PostgresDeleteAtReaper reaper, final PostgresDeleteAtReaperConfig config) {
        Objects.requireNonNull(reaper, "reaper");
        Objects.requireNonNull(config, "config");
        return Props.create(PostgresDeleteAtReaperActor.class, reaper, config.getInterval());
    }

    @Override
    public void preStart() {
        scheduleNextTick();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(Tick.INSTANCE, tick -> runTick())
                .match(TickResult.class, this::handleResult)
                .match(TickFailed.class, this::handleFailure)
                .build();
    }

    private void runTick() {
        reaper.tick().subscribe(
                result -> getSelf().tell(result, ActorRef.noSender()),
                error -> getSelf().tell(new TickFailed(error), ActorRef.noSender()));
    }

    private void handleResult(final TickResult result) {
        if (!result.lockAcquired()) {
            LOGGER.debug("Skipping delete-at reap tick: another reaper instance holds the advisory lock.");
        } else if (result.reapedCount() > 0L) {
            reapedRowsCounter.increment(result.reapedCount());
            if (result.safetyValveHit()) {
                LOGGER.info("Reaped {} delete-at-marked row(s) in {} batch(es); max-batches-per-tick safety valve "
                        + "reached, backlog continues on the next tick.", result.reapedCount(), result.batchesRun());
            } else {
                LOGGER.info("Reaped {} delete-at-marked row(s) in {} batch(es).", result.reapedCount(),
                        result.batchesRun());
            }
        } else {
            LOGGER.debug("Delete-at reap tick found no marked rows to reap.");
        }
        scheduleNextTick();
    }

    private void handleFailure(final TickFailed failed) {
        LOGGER.warn("PostgreSQL search delete-at reap tick failed; will retry on the next scheduled tick in {}.",
                interval, failed.error());
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        getContext().getSystem()
                .scheduler()
                .scheduleOnce(interval, getSelf(), Tick.INSTANCE, getContext().getDispatcher(), ActorRef.noSender());
    }

    private enum Tick {
        INSTANCE
    }

    private record TickFailed(Throwable error) {}

}
