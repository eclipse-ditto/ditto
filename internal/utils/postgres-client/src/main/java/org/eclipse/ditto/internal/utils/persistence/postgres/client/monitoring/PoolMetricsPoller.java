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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.pool.PoolMetrics;

/**
 * Polls {@link io.r2dbc.pool.ConnectionPool#getMetrics()} on a fixed cadence (~1s) and publishes the instantaneous pool
 * counts as Kamon gauges: r2dbc-proxy has no pool-event hooks, so a poll is the only way to surface pool occupancy.
 * {@link PoolMetrics} exposes only instantaneous counts (never acquire wait-time — that comes from
 * {@link R2dbcMetricsListener} instead).
 * <p>
 * Every poller is bound to a pool <em>role</em> ({@link PostgresMetrics#TAG_POOL}, e.g.
 * {@value PostgresMetrics#ROLE_SHARED}) which is applied as a low-cardinality discriminating tag to all four gauges, so
 * that more than one pool in a single JVM cannot collapse onto the same Kamon instrument (the gauge {@code set} is
 * last-writer-wins). Its coordinated-shutdown task name and poll thread name are likewise derived from the role, so two
 * pollers never clash on a fixed string.
 */
public final class PoolMetricsPoller implements AutoCloseable {

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1L);
    private static final Logger LOGGER = LoggerFactory.getLogger(PoolMetricsPoller.class);

    private static final String SHUTDOWN_TASK_PREFIX = "stop-postgres-pool-metrics-poller-";

    /** Production gauge sink: publish each gauge as a tagged Kamon gauge {@code set}. */
    private static final GaugePublisher DEFAULT_SINK =
            (name, tags, value) -> DittoMetrics.gauge(name).tags(tags).set((long) value);

    /**
     * Sink for a single gauge emission: production publishes to Kamon, tests capture {@code (name, tags, value)}.
     */
    @FunctionalInterface
    interface GaugePublisher {
        void publish(String name, TagSet tags, int value);
    }

    private final Supplier<Optional<PoolMetrics>> metricsSupplier;
    private final Duration interval;
    private final String poolRole;
    private final TagSet gaugeTags;
    private final GaugePublisher gaugeSink;
    private final ScheduledExecutorService scheduler;

    @Nullable
    private ScheduledFuture<?> scheduledTask;

    private PoolMetricsPoller(final Supplier<Optional<PoolMetrics>> metricsSupplier, final Duration interval,
            final String poolRole, final GaugePublisher gaugeSink) {
        this.metricsSupplier = metricsSupplier;
        this.interval = interval;
        this.poolRole = poolRole;
        this.gaugeTags = TagSet.empty()
                .putTag(Tag.of(PostgresMetrics.TAG_ENGINE, PostgresMetrics.ENGINE_POSTGRES))
                .putTag(Tag.of(PostgresMetrics.TAG_POOL, poolRole));
        this.gaugeSink = gaugeSink;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "ditto-postgres-pool-metrics-" + poolRole);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * @param metricsSupplier supplies the live pool metrics (e.g. {@code client::getPoolMetrics}).
     * @param poolRole the pool role tag value (a member of {@link PostgresMetrics#POOL_ROLE_DOMAIN}); discriminates the
     * gauges and the shutdown-task name so multiple pools never collide.
     * @return a poller with the default ~1s cadence (not yet started).
     */
    public static PoolMetricsPoller of(final Supplier<Optional<PoolMetrics>> metricsSupplier, final String poolRole) {
        return new PoolMetricsPoller(metricsSupplier, DEFAULT_INTERVAL, poolRole, DEFAULT_SINK);
    }

    /**
     * @param metricsSupplier supplies the live pool metrics.
     * @param poolRole the pool role tag value.
     * @param interval the poll cadence.
     * @return a poller with the given cadence (not yet started).
     */
    public static PoolMetricsPoller of(final Supplier<Optional<PoolMetrics>> metricsSupplier, final String poolRole,
            final Duration interval) {
        return new PoolMetricsPoller(metricsSupplier, interval, poolRole, DEFAULT_SINK);
    }

    /**
     * Test seam: a poller whose gauge emissions are routed to the given sink instead of Kamon, so a test can assert the
     * (name, tags) of every emitted gauge without a metrics backend.
     *
     * @param metricsSupplier supplies the live pool metrics.
     * @param poolRole the pool role tag value.
     * @param gaugeSink receives every emitted gauge as {@code (metricName, tagSet, value)}.
     * @return a poller publishing to {@code gaugeSink} at the default cadence (not yet started).
     */
    static PoolMetricsPoller of(final Supplier<Optional<PoolMetrics>> metricsSupplier, final String poolRole,
            final GaugePublisher gaugeSink) {
        return new PoolMetricsPoller(metricsSupplier, DEFAULT_INTERVAL, poolRole, gaugeSink);
    }

    /**
     * @param poolRole the pool role tag value.
     * @return the coordinated-shutdown task name for a poller of this role — unique per role so two pollers never
     * register the same fixed task name (the clash).
     */
    public static String shutdownTaskName(final String poolRole) {
        return SHUTDOWN_TASK_PREFIX + poolRole;
    }

    /**
     * @return the tags applied to every gauge this poller publishes (engine + pool/role).
     */
    TagSet gaugeTags() {
        return gaugeTags;
    }

    /**
     * Starts periodic polling. The returned schedule is retained so {@link #close()} can cancel it.
     */
    public void start() {
        scheduledTask =
                scheduler.scheduleAtFixedRate(this::pollOnce, 0L, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Reads the pool metrics once and publishes the gauges. Package-visible so tests can drive a single tick
     * deterministically without the scheduler.
     * <p>
     * Never propagates a recoverable failure: a throw from a disposed/closing pool (or an {@link Error} such as an
     * {@link AssertionError} from a misbehaving supplier) is swallowed (logged at DEBUG), because
     * {@code scheduleAtFixedRate} silently abandons the task forever on an uncaught exception. JVM-fatal
     * {@link VirtualMachineError}s ({@link OutOfMemoryError}, {@link StackOverflowError}, …) are NOT swallowed —
     * they propagate so the fatal condition is not hidden behind a DEBUG log line.
     * </p>
     */
    void pollOnce() {
        try {
            metricsSupplier.get().ifPresent(metrics -> {
                gauge(PostgresMetrics.POOL_ACQUIRED, metrics.acquiredSize());
                gauge(PostgresMetrics.POOL_ALLOCATED, metrics.allocatedSize());
                gauge(PostgresMetrics.POOL_IDLE, metrics.idleSize());
                gauge(PostgresMetrics.POOL_PENDING, metrics.pendingAcquireSize());
            });
        } catch (final VirtualMachineError fatal) {
            // OutOfMemoryError / StackOverflowError / InternalError: JVM-fatal, must propagate — never swallow.
            throw fatal;
        } catch (final Exception | LinkageError e) {
            // Typically a disposed pool during shutdown (or an AssertionError-style supplier fault); keep the
            // scheduled task alive for any future ticks.
            LOGGER.debug("Polling Postgres pool metrics (role={}) failed; skipping this tick.", poolRole, e);
        } catch (final Error e) {
            // Non-VM Errors (e.g. AssertionError) are recoverable for our purposes: log and let the next tick run.
            LOGGER.debug("Polling Postgres pool metrics (role={}) failed with an Error; skipping this tick.",
                    poolRole, e);
        }
    }

    private void gauge(final String name, final int value) {
        gaugeSink.publish(name, gaugeTags, value);
    }

    /**
     * Cancels the scheduled poll (if started), resets the four pool gauges to {@code 0}, and shuts the scheduler
     * down. Idempotent.
     * <p>
     * Ordering matters: the scheduled task is cancelled <em>before</em> the gauges are reset. Resetting first would
     * leave a race where a tick firing between the reset and the cancel re-publishes the stale last value, so a
     * disposed pool would appear to hold connections forever. Cancelling first (with {@code cancel(true)}, letting
     * any in-flight tick drain) guarantees no further tick can re-publish after the reset.
     * </p>
     */
    @Override
    public void close() {
        // 1. Cancel first so no in-flight/future tick can re-publish a stale value after the reset below.
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        // 2. Then reset the four pool gauges to 0; otherwise they'd keep the last polled value forever.
        gauge(PostgresMetrics.POOL_ACQUIRED, 0);
        gauge(PostgresMetrics.POOL_ALLOCATED, 0);
        gauge(PostgresMetrics.POOL_IDLE, 0);
        gauge(PostgresMetrics.POOL_PENDING, 0);
        scheduledTask = null;
        scheduler.shutdownNow();
    }

    /**
     * Test seam: whether the retained scheduled task has been cancelled. Used to assert the cancel-before-reset
     * ordering in {@link #close()}.
     *
     * @return {@code true} if a scheduled task exists and has been cancelled; {@code false} if none was started.
     */
    boolean isScheduledTaskCancelled() {
        return scheduledTask != null && scheduledTask.isCancelled();
    }

}
