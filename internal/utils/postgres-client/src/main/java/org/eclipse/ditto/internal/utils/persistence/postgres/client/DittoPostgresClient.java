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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.reactivestreams.Publisher;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * An R2DBC {@link ConnectionPool} wrapper analogous to {@code DittoMongoClient}.
 * <p>
 * The hard part it solves is the <strong>per-statement connection lifecycle</strong>: acquire a connection from the
 * pool, {@code bind} the {@link Statement}, {@code map} the {@link Result}, and <em>release the connection back to the
 * pool on every terminal path</em> — normal completion, error, <em>and</em> downstream cancellation. The reactive
 * pipeline is then bridged to Pekko Streams via {@link Source#fromPublisher(Publisher)} (the very same
 * {@link Publisher}-based bridge the Mongo reactive-streams driver already uses).
 * </p>
 * <p>
 * Release-on-all-paths is guaranteed by {@link Flux#usingWhen(Publisher, java.util.function.Function,
 * java.util.function.Function)}: the {@code resourceClosure} (statement execution + row mapping) is wrapped so the
 * {@code asyncCleanup} ({@link Connection#close()}) runs on completion, error, and cancel. The connection is therefore
 * never leaked, regardless of how the consumer terminates the stream.
 * </p>
 */
@ThreadSafe
public final class DittoPostgresClient implements AutoCloseable {

    private final ConnectionPool connectionPool;
    private final PostgresConfig postgresConfig;

    /**
     * The optional schema self-heal collaborator. {@code null} = healing off (the test constructors and the raw pool
     * wrapper), so a runtime {@code 42P01} surfaces unchanged; when present and {@link PostgresSchemaHealer#isEnabled()}
     * a missing-table error triggers a schema heal + a single retry.
     */
    @Nullable
    private final PostgresSchemaHealer schemaHealer;

    private DittoPostgresClient(final ConnectionPool connectionPool, final PostgresConfig postgresConfig,
            @Nullable final PostgresSchemaHealer schemaHealer) {
        this.connectionPool = connectionPool;
        this.postgresConfig = postgresConfig;
        this.schemaHealer = schemaHealer;
    }

    /**
     * Builds a {@code DittoPostgresClient} from the given configuration.
     * <p>
     * Applies the SSL boot guard before constructing anything: refuses to boot when a chain-verifying SSL mode is
     * configured without a root cert and system-truststore fallback is disallowed.
     * </p>
     *
     * @param postgresConfig the backend configuration.
     * @return the client.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if the SSL configuration is unsafe.
     * @throws NullPointerException if {@code postgresConfig} is {@code null}.
     */
    public static DittoPostgresClient newInstance(final PostgresConfig postgresConfig) {
        return newInstance(postgresConfig, null);
    }

    /**
     * Builds a {@code DittoPostgresClient} from the given configuration, wired with a schema self-heal collaborator.
     * <p>
     * Same SSL boot guard as {@link #newInstance(PostgresConfig)}. The healer is disposed together with the pool in
     * {@link #close()}.
     * </p>
     *
     * @param postgresConfig the backend configuration.
     * @param schemaHealer the schema self-heal collaborator, or {@code null} to disable healing.
     * @return the client.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if the SSL configuration is unsafe.
     * @throws NullPointerException if {@code postgresConfig} is {@code null}.
     */
    public static DittoPostgresClient newInstance(final PostgresConfig postgresConfig,
            @Nullable final PostgresSchemaHealer schemaHealer) {
        final ConnectionPool pool = ConnectionPoolFactory.createConnectionPool(postgresConfig);
        return forConnectionPool(pool, postgresConfig, schemaHealer);
    }

    /**
     * Builds a {@code DittoPostgresClient} around an already-constructed {@link ConnectionPool}. Primarily for tests
     * that supply a stub/mock pool, but also reusable wherever the pool is built externally. Healing is off (a runtime
     * {@code 42P01} surfaces unchanged).
     *
     * @param connectionPool the pool to wrap.
     * @param postgresConfig the backend configuration (used for accessors only).
     * @return the client.
     */
    public static DittoPostgresClient forConnectionPool(final ConnectionPool connectionPool,
            final PostgresConfig postgresConfig) {
        return forConnectionPool(connectionPool, postgresConfig, null);
    }

    /**
     * Builds a {@code DittoPostgresClient} around an already-constructed {@link ConnectionPool}, wired with a schema
     * self-heal collaborator.
     *
     * @param connectionPool the pool to wrap.
     * @param postgresConfig the backend configuration (used for accessors only).
     * @param schemaHealer the schema self-heal collaborator, or {@code null} to disable healing.
     * @return the client.
     */
    public static DittoPostgresClient forConnectionPool(final ConnectionPool connectionPool,
            final PostgresConfig postgresConfig, @Nullable final PostgresSchemaHealer schemaHealer) {
        return new DittoPostgresClient(connectionPool, postgresConfig, schemaHealer);
    }

    /**
     * Registers a component schema with this client's self-heal collaborator (no-op when healing is not wired).
     * Called by each component's schema bootstrap so a runtime heal knows which descriptor(s) to recreate — the
     * schema manager is descriptor-parameterized and the shared client serves persistence and search alike.
     *
     * @param descriptor the component schema descriptor.
     */
    public void registerSchemaDescriptor(final PostgresSchemaDescriptor descriptor) {
        if (schemaHealer != null) {
            schemaHealer.registerDescriptor(descriptor);
        }
    }

    /**
     * @return the underlying R2DBC connection pool (also a {@code ConnectionFactory}).
     */
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    /**
     * @return the configuration this client was built from.
     */
    public PostgresConfig getPostgresConfig() {
        return postgresConfig;
    }

    /**
     * @return the live pool metrics, if the pool exposes them.
     */
    public java.util.Optional<PoolMetrics> getPoolMetrics() {
        return connectionPool.getMetrics();
    }

    /**
     * Executes a SQL query against a pooled connection and maps each result row, bridging the reactive result to a
     * Pekko {@link Source}.
     * <p>
     * The connection is acquired from the pool when the returned {@code Source} is materialised and run, and released
     * back to the pool on completion, error, <em>and</em> cancel.
     * </p>
     *
     * @param sql the SQL statement (may contain {@code $1, $2, ...} bind markers).
     * @param binder binds parameters onto the prepared {@link Statement}; may be {@code null} for parameter-less SQL.
     * @param rowMapper maps each {@link Row} (+ {@link RowMetadata}) to a result element.
     * @param <T> the mapped element type.
     * @return a {@code Source} emitting one element per result row.
     */
    public <T> Source<T, NotUsed> executeSql(final String sql,
            @Nullable final Consumer<Statement> binder,
            final BiFunction<Row, RowMetadata, T> rowMapper) {
        return Source.fromPublisher(executeSqlPublisher(sql, binder, rowMapper));
    }

    /**
     * Executes an update/DML statement against a pooled connection, bridging the per-result update counts to a Pekko
     * {@link Source}. Same release-on-all-paths guarantees as {@link #executeSql}.
     *
     * @param sql the SQL statement.
     * @param binder binds parameters onto the prepared {@link Statement}; may be {@code null}.
     * @return a {@code Source} emitting the number of rows updated per result segment.
     */
    public Source<Long, NotUsed> executeUpdate(final String sql, @Nullable final Consumer<Statement> binder) {
        return Source.fromPublisher(executeUpdatePublisher(sql, binder));
    }

    /**
     * Reactive variant of {@link #executeUpdate(String, Consumer)} returning the raw reactive-streams {@link Publisher}
     * of per-result update counts (used by the shared persistence operations, which compose with Reactor rather than
     * Pekko).
     *
     * @param sql the SQL statement.
     * @param binder binds parameters onto the prepared {@link Statement}; may be {@code null}.
     * @return a {@link Publisher} emitting the number of rows updated per result segment.
     */
    public Publisher<Long> executeUpdatePublisher(final String sql, @Nullable final Consumer<Statement> binder) {
        return withSelfHeal(() -> rawExecuteUpdatePublisher(sql, binder));
    }

    private Publisher<Long> rawExecuteUpdatePublisher(final String sql, @Nullable final Consumer<Statement> binder) {
        return Flux.usingWhen(
                connectionPool.create(),
                connection -> Flux.from(bind(connection, sql, binder).execute())
                        .flatMap(Result::getRowsUpdated),
                Connection::close);
    }

    /**
     * Reactive variant of {@link #executeSql} returning the raw reactive-streams {@link Publisher} of mapped rows (used
     * by the shared persistence operations, which compose with Reactor rather than Pekko). Same release-on-all-paths
     * guarantees.
     *
     * @param sql the SQL statement.
     * @param binder binds parameters onto the prepared {@link Statement}; may be {@code null}.
     * @param rowMapper maps each {@link Row} (+ {@link RowMetadata}) to a result element.
     * @param <T> the mapped element type.
     * @return a {@link Publisher} emitting one element per result row.
     */
    public <T> Publisher<T> executeSqlPublisher(final String sql,
            @Nullable final Consumer<Statement> binder,
            final BiFunction<Row, RowMetadata, T> rowMapper) {
        return withSelfHeal(() -> rawExecuteSqlPublisher(sql, binder, rowMapper));
    }

    private <T> Publisher<T> rawExecuteSqlPublisher(final String sql,
            @Nullable final Consumer<Statement> binder,
            final BiFunction<Row, RowMetadata, T> rowMapper) {
        // Flux.usingWhen acquires the connection (resource), runs the statement (resourceClosure) and ALWAYS releases
        // the connection (asyncCleanup) on complete / error / cancel -> no connection leak on any path.
        return Flux.usingWhen(
                connectionPool.create(),
                connection -> Flux.from(bind(connection, sql, binder).execute())
                        .flatMap(result -> result.map(rowMapper)),
                Connection::close);
    }

    /**
     * Wraps a runtime SQL operation with the on-demand schema self-heal: runs {@code op}, and if it fails with a
     * missing-table error ({@code 42P01}) while self-heal is enabled, recreates the schema and re-subscribes {@code op}
     * <strong>exactly once</strong> (the retry is NOT itself self-heal-wrapped, so a still-missing table after a heal
     * surfaces the error rather than looping). When self-heal is off this is a straight pass-through.
     */
    private <T> Publisher<T> withSelfHeal(final Supplier<Publisher<T>> op) {
        if (!isSelfHealEnabled()) {
            return op.get();
        }
        return Flux.defer(() -> Flux.from(op.get()))
                .onErrorResume(PostgresSqlStates::isUndefinedTable,
                        // If the heal itself fails (e.g. 42501 insufficient_privilege), surface the ORIGINAL
                        // missing-table error rather than the heal error, so the behaviour is no worse than with
                        // self-heal off (the heal failure is WARN-logged by the healer).
                        original -> healSchema()
                                .onErrorResume(healError -> Mono.error(original))
                                .thenMany(Flux.defer(() -> Flux.from(op.get()))));
    }

    /**
     * @return whether a schema self-heal collaborator is present and enabled (reused by
     * {@code PostgresPersistenceOperations} to guard the transaction heal+retry path).
     */
    public boolean isSelfHealEnabled() {
        return schemaHealer != null && schemaHealer.isEnabled();
    }

    /**
     * Recreates the schema on demand (idempotent, single-flight), for callers that detected a missing-table error
     * themselves (e.g. the multi-statement transaction path). Guard with {@link #isSelfHealEnabled()} first.
     *
     * @return a {@link Mono} completing when the schema has been (re)created; an error {@code Mono} when no healer is
     * wired.
     */
    public Mono<Void> healSchema() {
        return schemaHealer != null
                ? schemaHealer.heal()
                : Mono.error(new IllegalStateException("PostgreSQL schema self-heal is not configured."));
    }

    private static Statement bind(final Connection connection, final String sql,
            @Nullable final Consumer<Statement> binder) {
        final Statement statement = connection.createStatement(sql);
        if (binder != null) {
            binder.accept(statement);
        }
        return statement;
    }

    /**
     * Disposes the underlying connection pool, closing all connections, and the schema self-heal collaborator's cached
     * DDL connection factory (if any). Blocking-free for the pool; returns immediately.
     */
    @Override
    public void close() {
        if (schemaHealer != null) {
            schemaHealer.dispose();
        }
        connectionPool.dispose();
    }

    /**
     * @return a {@link Mono} that completes once the pool has been gracefully disposed.
     */
    public Mono<Void> disposeLater() {
        return connectionPool.disposeLater();
    }

}
