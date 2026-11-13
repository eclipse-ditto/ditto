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
package org.eclipse.ditto.internal.utils.persistence.postgres.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.R2dbcTimeoutException;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * What pool overload does to a journal write: production {@link DittoPostgresClient} -&gt;
 * {@link PostgresPersistenceOperations} -&gt; {@link PostgresJournalOps} over a genuinely saturated r2dbc
 * {@link ConnectionPool} configured with the production overload settings ({@code acquireRetry(0)}, bounded pending
 * queue; that {@code ConnectionPoolFactory} really builds the pool this way is pinned by postgres-client's
 * {@code ConnectionPoolFactoryOverloadTest}).
 * <ul>
 *     <li>A starved write fails after ONE acquire budget, as a failed future (lost write), never a rejection.</li>
 *     <li>A write beyond the pending bound fails immediately with a classified {@link PostgresPoolOverloadException}.
 *     </li>
 * </ul>
 * What a failed write future then does to the persistent actor is Pekko behaviour, characterised separately in
 * {@link PersistentActorWriteOutcomeCharacterisationTest}. Fully offline, production durations scaled down.
 * Originally part of the 2026-08-21 pool-starvation investigation's {@code PoolStarvationConsequenceProbeTest}.
 */
public final class PostgresJournalPoolOverloadTest {

    @Test
    public void starvedWriteFailsAfterOneAcquireBudgetAsALostWrite() {
        final Duration maxAcquireTime = Duration.ofMillis(500);
        final ConnectionPool pool = singleConnectionPool(maxAcquireTime, 10);
        try {
            final Connection held = Mono.from(pool.create()).block(Duration.ofSeconds(5));
            assertThat(held).isNotNull();
            // Built BEFORE the clock starts, so config parsing / class loading don't count against the budget.
            final PostgresJournalOps journalOps = journalOver(pool);
            final List<AtomicWrite> writes = List.of(atomicWrite("thing:starved:1", 1L));

            final long startNanos = System.nanoTime();
            final CompletionStage<Iterable<Optional<Exception>>> write = journalOps.writeMessages(writes);
            assertThatThrownBy(() -> write.toCompletableFuture().get(30, TimeUnit.SECONDS))
                    .as("a starved acquire fails the write future — the Pekko SPI's 'lost write' signal, never a "
                            + "per-write rejection")
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(R2dbcTimeoutException.class);
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            // Upper bound just below TWO budgets: a second (retried) budget is what this test guards against.
            assertThat(elapsed)
                    .as("the write is held for ONE acquire budget (observed %dms), not two", elapsed.toMillis())
                    .isGreaterThanOrEqualTo(maxAcquireTime.minusMillis(50))
                    .isLessThan(maxAcquireTime.multipliedBy(2).minusMillis(50));
        } finally {
            pool.dispose();
        }
    }

    @Test
    public void writeBeyondThePendingBoundFailsImmediatelyAsClassifiedPoolOverload() throws Exception {
        // A 30s acquire budget, so an immediate failure can only be the pending bound, never a timeout.
        final ConnectionPool pool = singleConnectionPool(Duration.ofSeconds(30), 1);
        final AtomicReference<Disposable> queued = new AtomicReference<>();
        try {
            final Connection held = Mono.from(pool.create()).block(Duration.ofSeconds(5));
            assertThat(held).isNotNull();
            // Fill the one pending slot.
            queued.set(Mono.from(pool.create()).subscribe(c -> { }, e -> { }));
            awaitPending(pool, 1);
            final PostgresJournalOps journalOps = journalOver(pool);
            final List<AtomicWrite> writes = List.of(atomicWrite("thing:overload:1", 1L));

            final long startNanos = System.nanoTime();
            final CompletionStage<Iterable<Optional<Exception>>> write = journalOps.writeMessages(writes);
            assertThatThrownBy(() -> write.toCompletableFuture().get(10, TimeUnit.SECONDS))
                    .as("over the pending bound: a failed future (lost write -> actor stop) with a classified cause")
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(PostgresPoolOverloadException.class)
                    .hasCauseInstanceOf(PoolAcquirePendingLimitException.class);
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            assertThat(elapsed)
                    .as("shed immediately (observed %dms), not after the 30s acquire budget", elapsed.toMillis())
                    .isLessThan(Duration.ofSeconds(5));
        } finally {
            if (queued.get() != null) {
                queued.get().dispose();
            }
            pool.dispose();
        }
    }

    /**
     * A pool of ONE inert connection with the production overload settings: no acquire retry, bounded pending queue.
     */
    private static ConnectionPool singleConnectionPool(final Duration maxAcquireTime, final int pendingAcquireMaxSize) {
        return new ConnectionPool(ConnectionPoolConfiguration.builder(INERT_FACTORY)
                .name("journal-pool-overload-test")
                .initialSize(0)
                .maxSize(1)
                .maxAcquireTime(maxAcquireTime)
                .acquireRetry(0)
                .customizer(poolBuilder -> poolBuilder.maxPendingAcquire(pendingAcquireMaxSize))
                .build());
    }

    private static PostgresJournalOps journalOver(final ConnectionPool pool) {
        final PostgresConfig postgresConfig = DefaultPostgresConfig.of(ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n").getConfig("ditto"));
        final DittoPostgresClient client = DittoPostgresClient.forConnectionPool(pool, postgresConfig);
        return PostgresJournalOps.of(PostgresPersistenceOperations.of(client, "things"));
    }

    private static void awaitPending(final ConnectionPool pool, final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (pool.getMetrics().orElseThrow().pendingAcquireSize() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("pool never reached " + expected + " pending borrower(s)");
    }

    private static AtomicWrite atomicWrite(final String pid, final long sequenceNr) {
        return AtomicWrite.apply(PersistentRepr.apply("{\"k\":1}", sequenceNr, pid, "", false,
                ActorRef.noSender(), "writer"));
    }

    /**
     * Hands out inert connections. With {@code maxSize=1} the pool only ever opens one, and the test holds it, so every
     * further borrow can only queue (or be shed).
     */
    private static final ConnectionFactory INERT_FACTORY = new ConnectionFactory() {
        @Override
        public Publisher<? extends Connection> create() {
            return Mono.fromCallable(PostgresJournalPoolOverloadTest::inertConnection);
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "inert";
        }
    };

    private static Connection inertConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("validate".equals(method.getName())) {
                        return Mono.just(Boolean.TRUE);
                    }
                    if (Publisher.class.isAssignableFrom(method.getReturnType())) {
                        return Mono.empty();
                    }
                    if (boolean.class == method.getReturnType()) {
                        return Boolean.TRUE;
                    }
                    if ("toString".equals(method.getName())) {
                        return "inert-connection";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }

}
