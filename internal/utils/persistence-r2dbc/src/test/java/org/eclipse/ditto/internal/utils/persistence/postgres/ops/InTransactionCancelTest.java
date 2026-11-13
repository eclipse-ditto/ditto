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
package org.eclipse.ditto.internal.utils.persistence.postgres.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.ValidationDepth;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Regression test for {@code r2mlh-m1}: cancelling an in-flight {@code inTransaction(...)} must roll the open
 * transaction back <em>before</em> the connection is closed, so an "idle in transaction" connection is never
 * leaked.
 * <p>
 * The connection here is handed out <em>raw</em> (not wrapped in a real {@code PooledConnection}) by overriding
 * {@link ConnectionPool#create()}. This is deliberate: r2dbc-pool's own {@code PooledConnection.close()} resets the
 * connection (issuing a rollback) on release, which would mask the difference between the buggy 3-arg
 * {@code usingWhen} (close-only cleanup) and the fixed 5-arg form. By skipping that wrapper, the only actor that can
 * roll the transaction back is {@code inTransaction}'s own cancel arm — exactly the contract under test. Against the
 * 3-arg form the close arm runs without a rollback and this test fails (no "rollback" recorded); against the 5-arg
 * form the {@code asyncCancel} arm rolls back before closing and it passes.
 */
public final class InTransactionCancelTest {

    @Test
    public void cancellingInFlightTransactionRollsBackBeforeClose() throws Exception {
        // GIVEN a connection whose INSERT (the transactional work) never completes, recording lifecycle calls.
        final List<String> calls = new CopyOnWriteArrayList<>();
        final AtomicBoolean workSubscribed = new AtomicBoolean(false);
        final StallingConnectionFactory factory = new StallingConnectionFactory(calls, workSubscribed);

        // A ConnectionPool whose create() yields the raw stub directly (no PooledConnection masking, see class doc).
        final ConnectionPool rawPool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("cancel-test-pool")
                .maxSize(1)
                .build()) {
            @Override
            public Mono<Connection> create() {
                return Mono.from(factory.create()).cast(Connection.class);
            }
        };
        final DittoPostgresClient client =
                DittoPostgresClient.forConnectionPool(rawPool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");

        // WHEN a write transaction is started and then cancelled mid-flight (while the INSERT is stalled).
        final Disposable subscription = operations.insertEvents(List.of(
                        new PostgresPersistenceOperations.JournalInsert("thing:1", 1L, "m", List.of(), "{}")))
                .subscribe();

        // wait until the transactional work has actually been entered (BEGIN done, INSERT subscribed).
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!workSubscribed.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(workSubscribed.get())
                .as("transactional work must have been entered before we cancel")
                .isTrue();

        subscription.dispose();

        // give the cancellation cleanup chain a moment to run.
        final long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!calls.contains("close") && System.nanoTime() < cleanupDeadline) {
            Thread.sleep(5);
        }

        // THEN the in-flight transaction was rolled back, was never committed, and the rollback happened strictly
        // before the connection was closed (so an "idle in transaction" connection never returns to the pool).
        assertThat(calls)
                .as("cancelling an in-flight transaction must roll it back; the 3-arg usingWhen leaks an "
                        + "'idle in transaction' connection")
                .contains("rollback");
        assertThat(calls)
                .as("a cancelled transaction must not be committed")
                .doesNotContain("commit");
        assertThat(calls)
                .as("the connection must still be closed on cancel")
                .contains("close");
        assertThat(calls.indexOf("rollback"))
                .as("rollback must precede close so no 'idle in transaction' connection returns to the pool")
                .isLessThan(calls.indexOf("close"));
        assertThat(calls.indexOf("begin"))
                .as("rollback must follow the begin of the transaction we cancelled")
                .isLessThan(calls.indexOf("rollback"));
    }

    /** A {@link ConnectionFactory} whose statements never complete, recording begin/commit/rollback/close order. */
    private static final class StallingConnectionFactory implements ConnectionFactory {

        private final List<String> calls;
        private final AtomicBoolean workSubscribed;

        private StallingConnectionFactory(final List<String> calls, final AtomicBoolean workSubscribed) {
            this.calls = calls;
            this.workSubscribed = workSubscribed;
        }

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.fromCallable(StallingConnection::new);
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "stalling-stub";
        }

        private final class StallingConnection implements Connection {

            @Override
            public Publisher<Void> beginTransaction() {
                return Mono.fromRunnable(() -> calls.add("begin"));
            }

            @Override
            public Publisher<Void> beginTransaction(final TransactionDefinition definition) {
                return beginTransaction();
            }

            @Override
            public Publisher<Void> commitTransaction() {
                return Mono.fromRunnable(() -> calls.add("commit"));
            }

            @Override
            public Publisher<Void> rollbackTransaction() {
                return Mono.fromRunnable(() -> calls.add("rollback"));
            }

            @Override
            public Publisher<Void> close() {
                return Mono.fromRunnable(() -> calls.add("close"));
            }

            @Override
            public Statement createStatement(final String sql) {
                // never-completing execution: signals it has been subscribed, then stalls forever.
                return new StallingStatement();
            }

            private final class StallingStatement implements Statement {

                @Override
                public Statement add() {
                    return this;
                }

                @Override
                public Statement bind(final int index, final Object value) {
                    return this;
                }

                @Override
                public Statement bind(final String name, final Object value) {
                    return this;
                }

                @Override
                public Statement bindNull(final int index, final Class<?> type) {
                    return this;
                }

                @Override
                public Statement bindNull(final String name, final Class<?> type) {
                    return this;
                }

                @Override
                public Publisher<? extends Result> execute() {
                    return Flux.<Result>never()
                            .doOnSubscribe(s -> workSubscribed.set(true));
                }
            }

            @Override
            public Batch createBatch() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Publisher<Void> createSavepoint(final String name) {
                return Mono.empty();
            }

            @Override
            public Publisher<Void> releaseSavepoint(final String name) {
                return Mono.empty();
            }

            @Override
            public Publisher<Void> rollbackTransactionToSavepoint(final String name) {
                return Mono.empty();
            }

            @Override
            public boolean isAutoCommit() {
                return false;
            }

            @Override
            public ConnectionMetadata getMetadata() {
                return new ConnectionMetadata() {
                    @Override
                    public String getDatabaseProductName() {
                        return "stalling-stub";
                    }

                    @Override
                    public String getDatabaseVersion() {
                        return "0";
                    }
                };
            }

            @Override
            public IsolationLevel getTransactionIsolationLevel() {
                return IsolationLevel.READ_COMMITTED;
            }

            @Override
            public Publisher<Void> setAutoCommit(final boolean autoCommit) {
                return Mono.empty();
            }

            @Override
            public Publisher<Void> setLockWaitTimeout(final java.time.Duration timeout) {
                return Mono.empty();
            }

            @Override
            public Publisher<Void> setStatementTimeout(final java.time.Duration timeout) {
                return Mono.empty();
            }

            @Override
            public Publisher<Void> setTransactionIsolationLevel(final IsolationLevel isolationLevel) {
                return Mono.empty();
            }

            @Override
            public Publisher<Boolean> validate(final ValidationDepth depth) {
                return Mono.just(true);
            }
        }
    }
}
