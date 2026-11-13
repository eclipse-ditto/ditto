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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * An in-memory {@link ConnectionFactory} stub used to drive a real {@link io.r2dbc.pool.ConnectionPool} in unit tests
 * without a live PostgreSQL. It records how many connections were physically opened/closed and lets a test choose
 * whether statement execution succeeds, emits rows, or fails.
 */
final class StubConnectionFactory implements ConnectionFactory {

    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();
    private final List<String> rowsToEmit;
    private final RuntimeException executeFailure;

    private StubConnectionFactory(final List<String> rowsToEmit, final RuntimeException executeFailure) {
        this.rowsToEmit = rowsToEmit;
        this.executeFailure = executeFailure;
    }

    static StubConnectionFactory emitting(final String... rows) {
        return new StubConnectionFactory(List.of(rows), null);
    }

    static StubConnectionFactory failingOnExecute(final RuntimeException failure) {
        return new StubConnectionFactory(List.of(), failure);
    }

    int openedConnections() {
        return opened.get();
    }

    int closedConnections() {
        return closed.get();
    }

    @Override
    public Publisher<? extends Connection> create() {
        return Mono.fromCallable(() -> {
            opened.incrementAndGet();
            return new StubConnection();
        });
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() {
        return () -> "stub";
    }

    private final class StubConnection implements Connection {

        @Override
        public Publisher<Void> close() {
            return Mono.fromRunnable(closed::incrementAndGet);
        }

        @Override
        public Statement createStatement(final String sql) {
            return new StubStatement();
        }

        @Override
        public Publisher<Void> beginTransaction() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> beginTransaction(final io.r2dbc.spi.TransactionDefinition definition) {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> commitTransaction() {
            return Mono.empty();
        }

        @Override
        public io.r2dbc.spi.Batch createBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<Void> createSavepoint(final String name) {
            return Mono.empty();
        }

        @Override
        public boolean isAutoCommit() {
            return true;
        }

        @Override
        public io.r2dbc.spi.ConnectionMetadata getMetadata() {
            return new io.r2dbc.spi.ConnectionMetadata() {
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
        public io.r2dbc.spi.IsolationLevel getTransactionIsolationLevel() {
            return io.r2dbc.spi.IsolationLevel.READ_COMMITTED;
        }

        @Override
        public Publisher<Void> releaseSavepoint(final String name) {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> rollbackTransaction() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> rollbackTransactionToSavepoint(final String name) {
            return Mono.empty();
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
        public Publisher<Void> setTransactionIsolationLevel(final io.r2dbc.spi.IsolationLevel isolationLevel) {
            return Mono.empty();
        }

        @Override
        public Publisher<Boolean> validate(final io.r2dbc.spi.ValidationDepth depth) {
            return Mono.just(true);
        }
    }

    private final class StubStatement implements Statement {

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
            if (executeFailure != null) {
                return Mono.error(executeFailure);
            }
            return Mono.just(new StubResult(rowsToEmit));
        }
    }

    private static final class StubResult implements Result {

        private final List<String> rows;

        private StubResult(final List<String> rows) {
            this.rows = rows;
        }

        @Override
        public Publisher<Long> getRowsUpdated() {
            return Mono.just((long) rows.size());
        }

        @Override
        public <T> Publisher<T> map(final BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
            return Flux.fromIterable(rows)
                    .map(value -> mappingFunction.apply(new StubRow(value), new StubRowMetadata()));
        }

        @Override
        public <T> Publisher<T> map(final Function<? super Readable, ? extends T> mappingFunction) {
            return Flux.fromIterable(rows).map(value -> mappingFunction.apply(new StubRow(value)));
        }

        @Override
        public Result filter(final java.util.function.Predicate<Segment> filter) {
            return this;
        }

        @Override
        public <T> Publisher<T> flatMap(final Function<Segment, ? extends Publisher<? extends T>> mappingFunction) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubRow implements Row {

        private final String value;

        private StubRow(final String value) {
            this.value = value;
        }

        @Override
        public RowMetadata getMetadata() {
            return new StubRowMetadata();
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

    private static final class StubRowMetadata implements RowMetadata {

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
