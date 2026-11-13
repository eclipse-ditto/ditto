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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link PostgresDeleteAtReaperActor} — no real database at all, only fake/failing
 * {@link ConnectionFactory}s driving {@link PostgresDeleteAtReaper#tick()} through the actor. Covers the two pieces of
 * logic that live in the actor itself (everything else is exercised DB-free in {@link ReaperDrainLoopTest} and against
 * a real database in {@code PostgresPurgeReaperIT}): (1) the WARN-and-continue resilience contract (brief req 2), and
 * (2) routing a successful, non-empty {@link PostgresDeleteAtReaper.TickResult} into the reaped-rows counter.
 */
public final class PostgresDeleteAtReaperActorTest {

    private ActorSystem system;

    @Before
    public void setUp() {
        system = ActorSystem.create(getClass().getSimpleName());
    }

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void anErroredTickIsSwallowedAndTheActorKeepsSchedulingSubsequentTicks() {
        final AtomicInteger connectionAttempts = new AtomicInteger();
        final ConnectionFactory alwaysFailing = new ConnectionFactory() {
            @Override
            public org.reactivestreams.Publisher<? extends Connection> create() {
                connectionAttempts.incrementAndGet();
                return Mono.error(new RuntimeException("simulated connection failure"));
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return () -> "fake-always-failing";
            }
        };
        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(
                ConfigFactory.parseString("postgresql.search.reaper.interval = 40ms"));
        final PostgresDeleteAtReaper reaper = PostgresDeleteAtReaper.forConnectionFactory(alwaysFailing, config);

        final TestKit probe = new TestKit(system);
        final ActorRef actor = system.actorOf(PostgresDeleteAtReaperActor.props(reaper, config));
        probe.watch(actor);

        // Give it time for several 40ms tick intervals. If a single failed tick crashed/stopped the actor, the watch
        // would see a Terminated message; if it stopped rescheduling after the first failure, connectionAttempts
        // would plateau at 1.
        probe.expectNoMessage(Duration.ofMillis(400));
        assertThat(connectionAttempts.get())
                .as("multiple connection attempts prove the actor rescheduled after earlier failures rather than "
                        + "crash-looping or silently giving up")
                .isGreaterThanOrEqualTo(3);

        system.stop(actor);
    }

    @Test
    public void aSuccessfulNonEmptyTickIncrementsTheReapedRowsCounter() throws Exception {
        // The fake grants the advisory lock, then reports one full batch (5) followed by a partial batch (2) --
        // total 7, drained (not safety-valve-limited) -- then answers every subsequent batch call with 0 (drained
        // instantly), so later ticks contribute nothing further to the counter.
        final ConnectionFactory reapingFake = fakeReapingConnectionFactory(List.of(5L, 2L));
        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(
                ConfigFactory.parseString("postgresql.search.reaper { interval = 30ms, batch-size = 5 }"));
        final PostgresDeleteAtReaper reaper = PostgresDeleteAtReaper.forConnectionFactory(reapingFake, config);

        // PostgresDeleteAtReaperActor increments a fixed, well-known Kamon counter name; reset it first so no other
        // test in this JVM can have polluted the observed value.
        final Counter wellKnownCounter = DittoMetrics.counter(PostgresDeleteAtReaperActor.REAPED_ROWS_COUNTER_NAME);
        wellKnownCounter.reset();

        final ActorRef actor = system.actorOf(PostgresDeleteAtReaperActor.props(reaper, config));
        try {
            final long deadline = System.nanoTime() + Duration.ofSeconds(5L).toNanos();
            long observed = wellKnownCounter.getCount();
            while (observed < 7L && System.nanoTime() < deadline) {
                Thread.sleep(20L);
                observed = wellKnownCounter.getCount();
            }
            assertThat(observed).as("the actor must have incremented the reaped-rows counter by the tick's count")
                    .isEqualTo(7L);
        } finally {
            system.stop(actor);
        }
    }

    /**
     * A minimal fake {@link ConnectionFactory} that answers {@link PostgresDeleteAtReaper#TRY_LOCK_SQL} with
     * {@code true} and {@link PostgresDeleteAtReaper#DELETE_BATCH_SQL} with the given sequence of per-batch deleted
     * counts (accessible here because this test lives in the same package as {@link PostgresDeleteAtReaper}).
     */
    private static ConnectionFactory fakeReapingConnectionFactory(final List<Long> deletedPerBatch) {
        final Deque<Long> batches = new ArrayDeque<>(deletedPerBatch);
        return new ConnectionFactory() {
            @Override
            public org.reactivestreams.Publisher<? extends Connection> create() {
                return Mono.just(new FakeConnection(batches));
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return () -> "fake-reaping";
            }
        };
    }

    private static final class FakeConnection implements Connection {

        private final Deque<Long> batches;

        private FakeConnection(final Deque<Long> batches) {
            this.batches = batches;
        }

        @Override
        public Statement createStatement(final String sql) {
            if (PostgresDeleteAtReaper.TRY_LOCK_SQL.equals(sql)) {
                return new FakeStatement(Mono.just(new SingleValueResult(Boolean.TRUE)));
            }
            if (PostgresDeleteAtReaper.DELETE_BATCH_SQL.equals(sql)) {
                final Long deleted = batches.isEmpty() ? 0L : batches.poll();
                return new FakeStatement(Mono.just(new UpdateCountResult(deleted)));
            }
            throw new UnsupportedOperationException("Unexpected SQL in fake: " + sql);
        }

        @Override
        public org.reactivestreams.Publisher<Void> beginTransaction() {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> beginTransaction(final TransactionDefinition definition) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> commitTransaction() {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> rollbackTransaction() {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> close() {
            return Mono.empty();
        }

        @Override
        public io.r2dbc.spi.Batch createBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.reactivestreams.Publisher<Void> createSavepoint(final String name) {
            return Mono.empty();
        }

        @Override
        public boolean isAutoCommit() {
            return false;
        }

        @Override
        public io.r2dbc.spi.ConnectionMetadata getMetadata() {
            return new io.r2dbc.spi.ConnectionMetadata() {
                @Override
                public String getDatabaseProductName() {
                    return "fake";
                }

                @Override
                public String getDatabaseVersion() {
                    return "0";
                }
            };
        }

        @Override
        public io.r2dbc.spi.IsolationLevel getTransactionIsolationLevel() {
            return io.r2dbc.spi.IsolationLevel.READ_COMMITTED;
        }

        @Override
        public org.reactivestreams.Publisher<Void> releaseSavepoint(final String name) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> rollbackTransactionToSavepoint(final String name) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> setAutoCommit(final boolean autoCommit) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> setLockWaitTimeout(final Duration timeout) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> setStatementTimeout(final Duration timeout) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Void> setTransactionIsolationLevel(
                final io.r2dbc.spi.IsolationLevel isolationLevel) {
            return Mono.empty();
        }

        @Override
        public org.reactivestreams.Publisher<Boolean> validate(final io.r2dbc.spi.ValidationDepth depth) {
            return Mono.just(true);
        }
    }

    private static final class FakeStatement implements Statement {

        private final Mono<? extends Result> result;

        private FakeStatement(final Mono<? extends Result> result) {
            this.result = result;
        }

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
        public org.reactivestreams.Publisher<? extends Result> execute() {
            return result;
        }
    }

    /** A fake {@link Result} whose single row's column 0 is the given value (used for the try-lock boolean). */
    private static final class SingleValueResult implements Result {

        private final Object value;

        private SingleValueResult(final Object value) {
            this.value = value;
        }

        @Override
        public org.reactivestreams.Publisher<Long> getRowsUpdated() {
            return Mono.just(0L);
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> map(final BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
            return Flux.just(mappingFunction.apply(new SingleValueRow(value), new EmptyRowMetadata()));
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> map(final Function<? super Readable, ? extends T> mappingFunction) {
            return Flux.just(mappingFunction.apply(new SingleValueRow(value)));
        }

        @Override
        public Result filter(final java.util.function.Predicate<Segment> filter) {
            return this;
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> flatMap(
                final Function<Segment, ? extends org.reactivestreams.Publisher<? extends T>> mappingFunction) {
            throw new UnsupportedOperationException();
        }
    }

    /** A fake {@link Result} reporting a fixed row-update count (used for the batched DELETE). */
    private static final class UpdateCountResult implements Result {

        private final long rowsUpdated;

        private UpdateCountResult(final long rowsUpdated) {
            this.rowsUpdated = rowsUpdated;
        }

        @Override
        public org.reactivestreams.Publisher<Long> getRowsUpdated() {
            return Mono.just(rowsUpdated);
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> map(final BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
            return Flux.empty();
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> map(final Function<? super Readable, ? extends T> mappingFunction) {
            return Flux.empty();
        }

        @Override
        public Result filter(final java.util.function.Predicate<Segment> filter) {
            return this;
        }

        @Override
        public <T> org.reactivestreams.Publisher<T> flatMap(
                final Function<Segment, ? extends org.reactivestreams.Publisher<? extends T>> mappingFunction) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SingleValueRow implements Row {

        private final Object value;

        private SingleValueRow(final Object value) {
            this.value = value;
        }

        @Override
        public RowMetadata getMetadata() {
            return new EmptyRowMetadata();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final int index, final Class<T> type) {
            return (T) value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final String name, final Class<T> type) {
            return (T) value;
        }
    }

    private static final class EmptyRowMetadata implements RowMetadata {

        @Override
        public io.r2dbc.spi.ColumnMetadata getColumnMetadata(final int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.r2dbc.spi.ColumnMetadata getColumnMetadata(final String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends io.r2dbc.spi.ColumnMetadata> getColumnMetadatas() {
            return List.of();
        }
    }

}
