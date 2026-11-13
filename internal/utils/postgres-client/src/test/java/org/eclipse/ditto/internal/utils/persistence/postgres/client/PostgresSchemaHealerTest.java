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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;
import org.junit.After;
import org.junit.Test;
import org.reactivestreams.Publisher;

import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.ValidationDepth;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Unit test for {@link PostgresSchemaHealer}, driving a gated in-memory {@link ConnectionFactory} stub (no live
 * PostgreSQL): proves single-flight coalescing (concurrent heals run the DDL once), the disabled no-op, and that the
 * in-flight reference resets so a later heal can heal again.
 */
public final class PostgresSchemaHealerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** A minimal component schema so an enabled heal has something to recreate through the stub factory. */
    private static final PostgresSchemaDescriptor TEST_DESCRIPTOR = new PostgresSchemaDescriptor() {

        @Override
        public String component() {
            return "healer-test";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public int minCompatibleVersion() {
            return 1;
        }

        @Override
        public List<String> ddlStatements() {
            return List.of("CREATE TABLE IF NOT EXISTS healer_test (id BIGINT PRIMARY KEY)");
        }

        @Override
        public List<TableContract> tableContracts() {
            return List.of();
        }
    };

    private ExecutorService executor;

    @After
    public void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentHealsRunTheDdlExactlyOnce() throws Exception {
        final CountDownLatch firstCreateStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final GatedStubFactory factory = new GatedStubFactory(firstCreateStarted, release);
        final PostgresSchemaHealer healer = PostgresSchemaHealer.forFactory(true, () -> factory);
        healer.registerDescriptor(TEST_DESCRIPTOR);

        executor = Executors.newFixedThreadPool(6);
        final List<Future<?>> heals = new ArrayList<>();

        // Heal A: subscribes, opens the (single) connection and blocks in create() until released -> it is now the
        // in-flight heal.
        heals.add(executor.submit(() -> healer.heal().block(TIMEOUT)));
        assertThat(firstCreateStarted.await(10, TimeUnit.SECONDS))
                .as("the first heal reached the connection factory").isTrue();

        // Heals B..E subscribe while A is in flight -> they must coalesce onto A's shared heal (no new connection).
        for (int i = 0; i < 4; i++) {
            heals.add(executor.submit(() -> healer.heal().block(TIMEOUT)));
        }
        // Give the coalescing subscribers a moment to attach to the in-flight heal before releasing the gate.
        Thread.sleep(300);

        release.countDown();
        for (final Future<?> heal : heals) {
            heal.get(20, TimeUnit.SECONDS);
        }

        assertThat(factory.openedConnections())
                .as("single-flight: five concurrent heals opened exactly one DDL connection").isEqualTo(1);

        // The in-flight reference reset on terminal, so a later heal heals again (a second connection).
        healer.heal().block(TIMEOUT);
        assertThat(factory.openedConnections())
                .as("a later heal re-runs the DDL (in-flight reference was reset)").isEqualTo(2);
    }

    @Test
    public void disabledHealerIsANoOpThatReRaises() {
        final AtomicInteger factoryBuilds = new AtomicInteger();
        final PostgresSchemaHealer healer = PostgresSchemaHealer.forFactory(false, () -> {
            factoryBuilds.incrementAndGet();
            return new GatedStubFactory(new CountDownLatch(0), new CountDownLatch(0));
        });

        assertThat(healer.isEnabled()).isFalse();
        assertThatThrownBy(() -> healer.heal().block(TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        assertThat(factoryBuilds.get()).as("a disabled heal never touches the DDL factory").isZero();
    }

    @Test
    public void ddlFactoryIsBuiltLazilyAndOnlyOnce() {
        final AtomicInteger factoryBuilds = new AtomicInteger();
        final PostgresSchemaHealer healer = PostgresSchemaHealer.forFactory(true, () -> {
            factoryBuilds.incrementAndGet();
            return new GatedStubFactory(new CountDownLatch(0), new CountDownLatch(0));
        });
        healer.registerDescriptor(TEST_DESCRIPTOR);
        assertThat(factoryBuilds.get()).as("constructing a healer builds no DDL factory").isZero();

        healer.heal().block(TIMEOUT);
        healer.heal().block(TIMEOUT);
        assertThat(factoryBuilds.get()).as("the DDL factory is built once and cached across heals").isEqualTo(1);
    }

    /**
     * A minimal in-memory {@link ConnectionFactory}: {@link #create()} counts physical opens and blocks until the gate
     * is released (so a test can hold a heal in flight); every statement execution succeeds with a zero row count so the
     * schema-manager DDL pipeline completes.
     */
    private static final class GatedStubFactory implements ConnectionFactory {

        private final AtomicInteger opened = new AtomicInteger();
        private final CountDownLatch createStarted;
        private final CountDownLatch release;

        private GatedStubFactory(final CountDownLatch createStarted, final CountDownLatch release) {
            this.createStarted = createStarted;
            this.release = release;
        }

        int openedConnections() {
            return opened.get();
        }

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.fromCallable(() -> {
                opened.incrementAndGet();
                createStarted.countDown();
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("heal gate was never released");
                }
                return new StubConnection();
            });
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "gated-stub";
        }
    }

    private static final class StubConnection implements Connection {

        @Override
        public Publisher<Void> close() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> beginTransaction() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> beginTransaction(final TransactionDefinition definition) {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> commitTransaction() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> rollbackTransaction() {
            return Mono.empty();
        }

        @Override
        public Statement createStatement(final String sql) {
            return new StubStatement();
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
                    return "stub";
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
        public Publisher<Void> setLockWaitTimeout(final Duration timeout) {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> setStatementTimeout(final Duration timeout) {
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

    private static final class StubStatement implements Statement {

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
            return Mono.just(new StubResult());
        }
    }

    private static final class StubResult implements Result {

        @Override
        public Publisher<Long> getRowsUpdated() {
            return Mono.just(0L);
        }

        @Override
        public <T> Publisher<T> map(final java.util.function.BiFunction<Row, RowMetadata, ? extends T> f) {
            return Flux.empty();
        }

        @Override
        public <T> Publisher<T> map(final java.util.function.Function<? super Readable, ? extends T> f) {
            return Flux.empty();
        }

        @Override
        public Result filter(final java.util.function.Predicate<Segment> filter) {
            return this;
        }

        @Override
        public <T> Publisher<T> flatMap(final java.util.function.Function<Segment, ? extends Publisher<? extends T>> f) {
            throw new UnsupportedOperationException();
        }
    }

}
