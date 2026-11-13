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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcTimeoutException;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * Regression tests for how the pool that {@link ConnectionPoolFactory} builds behaves under overload: more concurrent
 * borrows than {@code pool.max-size} allows.
 * <p>
 * The contract: the effective acquire budget is exactly {@code max-acquire-time} (no hidden acquire retry), the
 * pending-acquire queue is bounded (default {@code 2 x max-size}), and borrowers beyond that bound fail immediately with
 * {@link PoolAcquirePendingLimitException} instead of queueing behind a deadline their caller has already given up on.
 * </p>
 * <p>
 * Fully offline: the pool configuration comes from the production {@code ditto-postgres-client.conf} (system
 * environment ignored) plus per-test overrides, and is built by the production
 * {@code ConnectionPoolFactory.buildPoolConfiguration} over the in-memory {@link StubConnectionFactory}. No PostgreSQL,
 * no Docker. Originally the 2026-08-21 pool-starvation investigation's {@code PoolStarvationProbeTest}.
 * </p>
 */
public final class ConnectionPoolFactoryOverloadTest {

    private static final Duration BLOCK = Duration.ofSeconds(5);

    private static PostgresConfig productionConfigWith(final String overrides) {
        final Config production = ConfigFactory.parseResources("ditto-postgres-client.conf")
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
        return DefaultPostgresConfig.of(ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n" + overrides)
                .withFallback(production)
                .getConfig("ditto"));
    }

    private static ConnectionPool productionPoolOver(final StubConnectionFactory factory, final String overrides) {
        return new ConnectionPool(
                ConnectionPoolFactory.buildPoolConfiguration(factory, productionConfigWith(overrides)));
    }

    private static PoolMetrics metricsOf(final ConnectionPool pool) {
        return pool.getMetrics().orElseThrow();
    }

    @Test
    public void productionPoolFromProductionConfBoundsThePendingQueueAtTwiceMaxSize() {
        final PostgresConfig config = productionConfigWith(
                "ditto.postgresql.username = \"ditto\"\nditto.postgresql.password = \"ditto\"\n");

        // Building the pool opens nothing (initial-size = 0); getMetrics() is instantaneous.
        final ConnectionPool pool = ConnectionPoolFactory.createConnectionPool(config);
        try {
            assertThat(metricsOf(pool).getMaxPendingAcquireSize())
                    .as("the production pool bounds its pending-acquire queue at 2 x max-size")
                    .isEqualTo(2 * config.getPoolConfig().getMaxSize());
        } finally {
            pool.dispose();
        }
    }

    @Test
    public void pendingQueueIsUnboundedOnlyWhenExplicitlyConfiguredAsMinusOne() {
        final ConnectionPool pool = productionPoolOver(StubConnectionFactory.emitting(),
                "ditto.postgresql.pool.pending-acquire-max-size = -1");
        try {
            assertThat(metricsOf(pool).getMaxPendingAcquireSize())
                    .as("-1 maps to reactor-pool's unbounded queue (reported as Integer.MAX_VALUE)")
                    .isEqualTo(Integer.MAX_VALUE);
        } finally {
            pool.dispose();
        }
    }

    /**
     * r2dbc-pool's default {@code acquireRetry = 1} re-subscribes the whole acquire, timeout included, so an
     * un-overridden pool fails a starved borrow only after TWO {@code max-acquire-time} budgets (see
     * {@code io.r2dbc.pool.R2dbcPoolLibraryDefaultsTest}). The production pool must fail it after one.
     */
    @Test
    public void starvedBorrowFailsAfterOneMaxAcquireTimeNotTwo() {
        final Duration maxAcquireTime = Duration.ofSeconds(1);
        final ConnectionPool pool = productionPoolOver(StubConnectionFactory.emitting(),
                "ditto.postgresql.pool.max-size = 1\n"
                        + "ditto.postgresql.pool.max-acquire-time = " + maxAcquireTime.toMillis() + "ms\n");
        try {
            final Connection held = Mono.from(pool.create()).block(BLOCK);
            assertThat(held).isNotNull();

            final long startNanos = System.nanoTime();
            assertThatThrownBy(() -> Mono.from(pool.create()).block(Duration.ofSeconds(20)))
                    .isInstanceOf(R2dbcTimeoutException.class)
                    .hasMessageContaining("Connection acquisition timed out after 1000ms");
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            // Upper bound just below TWO budgets: a regression (library acquire retry back) takes >= 2 x budget, while a
            // loaded CI machine gets almost a full budget of headroom.
            assertThat(elapsed)
                    .as("time-to-error is one max-acquire-time budget (observed %dms); two would mean the "
                            + "library's default acquire retry is back", elapsed.toMillis())
                    .isGreaterThanOrEqualTo(maxAcquireTime.minusMillis(50))
                    .isLessThan(maxAcquireTime.multipliedBy(2).minusMillis(50));
        } finally {
            pool.dispose();
        }
    }

    @Test
    public void borrowersBeyondThePendingBoundFailImmediatelyWithPoolAcquirePendingLimitException() throws Exception {
        // max-size 2 -> derived pending bound 4. A 30s acquire budget makes "immediately" unambiguous: the excess
        // borrowers must fail without waiting for any timeout.
        final ConnectionPool pool = productionPoolOver(StubConnectionFactory.emitting(),
                "ditto.postgresql.pool.max-size = 2\n"
                        + "ditto.postgresql.pool.max-acquire-time = 30s\n");
        final List<Disposable> borrowers = new ArrayList<>();
        try {
            assertThat(metricsOf(pool).getMaxPendingAcquireSize()).isEqualTo(4);
            assertThat(Mono.from(pool.create()).block(BLOCK)).isNotNull();
            assertThat(Mono.from(pool.create()).block(BLOCK)).isNotNull();

            // Fill the queue exactly to its bound: these wait; nobody fails.
            final List<Throwable> queuedErrors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 4; i++) {
                borrowers.add(Mono.from(pool.create()).subscribe(c -> { }, queuedErrors::add));
            }
            await(() -> metricsOf(pool).pendingAcquireSize() == 4);
            assertThat(metricsOf(pool).pendingAcquireSize()).as("queue filled to its bound").isEqualTo(4);
            assertThat(queuedErrors).as("borrowers within the bound queue").isEmpty();

            // Three more borrowers: over the bound -> shed at once.
            final List<Throwable> excessErrors = Collections.synchronizedList(new ArrayList<>());
            final long startNanos = System.nanoTime();
            for (int i = 0; i < 3; i++) {
                borrowers.add(Mono.from(pool.create()).subscribe(c -> { }, excessErrors::add));
            }
            await(() -> excessErrors.size() == 3);
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            assertThat(excessErrors)
                    .as("every borrower beyond the bound is rejected explicitly")
                    .hasSize(3)
                    .allSatisfy(error -> {
                        assertThat(error).isInstanceOf(PoolAcquirePendingLimitException.class);
                        assertThat(((PoolAcquirePendingLimitException) error).getAcquirePendingLimit()).isEqualTo(4);
                    });
            assertThat(elapsed)
                    .as("rejected immediately (observed %dms), not after the 30s acquire budget", elapsed.toMillis())
                    .isLessThan(Duration.ofSeconds(1));
            assertThat(metricsOf(pool).pendingAcquireSize()).as("the queue never exceeds its bound").isEqualTo(4);
            assertThat(queuedErrors).as("shedding the excess leaves the queued borrowers waiting").isEmpty();
        } finally {
            borrowers.forEach(Disposable::dispose);
            pool.dispose();
        }
    }

    @Test
    public void aQueuedBorrowerGetsTheNextReleasedConnection() throws Exception {
        final StubConnectionFactory factory = StubConnectionFactory.emitting();
        final ConnectionPool pool = productionPoolOver(factory, "ditto.postgresql.pool.max-size = 2\n");
        final List<Disposable> borrowers = new ArrayList<>();
        try {
            final Connection held1 = Mono.from(pool.create()).block(BLOCK);
            final Connection held2 = Mono.from(pool.create()).block(BLOCK);
            assertThat(held1).isNotNull();
            assertThat(held2).isNotNull();

            final AtomicInteger served = new AtomicInteger();
            final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 2; i++) {
                borrowers.add(Mono.from(pool.create()).subscribe(c -> served.incrementAndGet(), errors::add));
            }
            await(() -> metricsOf(pool).pendingAcquireSize() == 2);

            Mono.from(held1.close()).block(BLOCK);
            await(() -> served.get() == 1);

            assertThat(served.get()).as("the released connection went to a queued borrower").isEqualTo(1);
            assertThat(metricsOf(pool).pendingAcquireSize()).isEqualTo(1);
            assertThat(errors).isEmpty();
            assertThat(factory.openedConnections())
                    .as("no connection beyond max-size was opened for the queued borrowers")
                    .isEqualTo(2);
        } finally {
            borrowers.forEach(Disposable::dispose);
            pool.dispose();
        }
    }

    @Test
    public void timedOutBorrowerLeavesTheQueueAndThePoolRecovers() throws Exception {
        final ConnectionPool pool = productionPoolOver(StubConnectionFactory.emitting(),
                "ditto.postgresql.pool.max-size = 1\n"
                        + "ditto.postgresql.pool.max-acquire-time = 200ms\n");
        try {
            final Connection held = Mono.from(pool.create()).block(BLOCK);
            assertThat(held).isNotNull();

            assertThatThrownBy(() -> Mono.from(pool.create()).block(Duration.ofSeconds(20)))
                    .isInstanceOf(R2dbcTimeoutException.class);

            await(() -> metricsOf(pool).pendingAcquireSize() == 0);
            assertThat(metricsOf(pool).pendingAcquireSize())
                    .as("a timed-out borrower is removed from the pending queue")
                    .isZero();

            Mono.from(held.close()).block(BLOCK);
            assertThat(Mono.from(pool.create()).block(BLOCK))
                    .as("the pool is usable again once the contention clears")
                    .isNotNull();
        } finally {
            pool.dispose();
        }
    }

    private static void await(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
    }

}
