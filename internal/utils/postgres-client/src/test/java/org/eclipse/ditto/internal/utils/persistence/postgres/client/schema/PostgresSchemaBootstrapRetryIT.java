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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcPermissionDeniedException;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The retrying bootstrap of {@link PostgresSchemaManager} against a real PostgreSQL (Testcontainers): every failure
 * that does not mean "PostgreSQL is not reachable yet" ends the bootstrap on its first attempt — schema refusals, a DDL
 * lock timeout, authentication, an unknown database and an SSL policy mismatch — and a bootstrap that connects but
 * does not finish (a held schema advisory lock, a blackholed endpoint) ends as an attempt timeout, also without a
 * second attempt. Every test configures a 5 s backoff, so a second attempt would show up in the elapsed time too.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSchemaBootstrapRetryIT {

    private static final String COMPONENT = "bootstrap-retry-it";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String NO_RETRY_WITHIN_THE_TEST = "retry { min-backoff = 5s, max-backoff = 5s }";

    private static final List<String> V1_DDL = List.of(
            "CREATE TABLE IF NOT EXISTS bt_items (id TEXT COLLATE \"C\" PRIMARY KEY, val BIGINT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS bt_items_val_idx ON bt_items (val)");

    private static final PostgresSchemaDescriptor V1 = descriptor(1, V1_DDL);

    /** A backward-compatible bump whose additional index needs a SHARE lock on bt_items. */
    private static final PostgresSchemaDescriptor V2 = descriptor(2, List.of(V1_DDL.get(0), V1_DDL.get(1),
            "CREATE INDEX IF NOT EXISTS bt_items_val2_idx ON bt_items (val)"));

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSchemaBootstrapRetryIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void resetDatabase() {
        runSql("DROP TABLE IF EXISTS bt_items, schema_version CASCADE");
    }

    // ---- refusals and the DDL lock timeout -------------------------------------------------------------------------

    @Test
    public void checksumDriftIsRefusedOnTheFirstAttempt() {
        bootOnce(V1);
        runSql("UPDATE schema_version SET checksum = 'drift' WHERE component = '" + COMPONENT + "'");

        assertRefusedOnTheFirstAttempt(V1, connectionFactory, SchemaBootException.class, "checksum mismatch");
    }

    @Test
    public void aDivergentPrimaryKeyIsRefusedOnTheFirstAttempt() {
        runSql("CREATE TABLE bt_items (id TEXT COLLATE \"C\", val BIGINT NOT NULL, PRIMARY KEY (id, val))");

        assertRefusedOnTheFirstAttempt(V1, connectionFactory, SchemaBootException.class, "divergent primary key");
    }

    @Test
    public void anIncompatibleNewerSchemaIsRefusedOnTheFirstAttempt() {
        bootOnce(V1);
        runSql("UPDATE schema_version SET version = 3, min_compatible_version = 2 WHERE component = '" + COMPONENT
                + "'");

        assertRefusedOnTheFirstAttempt(V1, connectionFactory, SchemaBootException.class, "min_compatible_version");
    }

    @Test
    public void aDdlLockTimeoutIsNotRetried() {
        bootOnce(V1);
        final Connection side = Mono.from(connectionFactory.create()).block(TIMEOUT);
        try {
            Mono.from(side.beginTransaction())
                    .thenMany(Flux.from(side.createStatement("LOCK TABLE bt_items IN SHARE UPDATE EXCLUSIVE MODE")
                            .execute()).flatMap(Result::getRowsUpdated))
                    .then()
                    .block(TIMEOUT);

            final Outcome outcome = bootstrapWithRetry(V2, connectionFactory, NO_RETRY_WITHIN_THE_TEST);

            assertThat(sqlStates(outcome.error())).as("SQLSTATEs in the cause chain of %s", outcome.error())
                    .contains("55P03");
            assertThat(outcome.creates()).as("attempts").isEqualTo(1);
            assertThat(outcome.elapsed()).isLessThan(Duration.ofSeconds(3));
        } finally {
            Mono.from(side.rollbackTransaction())
                    .onErrorResume(error -> Mono.empty())
                    .then(Mono.from(side.close()))
                    .block(TIMEOUT);
        }
    }

    // ---- authentication, unknown database, SSL policy --------------------------------------------------------------

    @Test
    public void aWrongPasswordIsNotRetried() {
        final Outcome outcome = bootstrapWithRetry(V1, POSTGRES.newConnectionFactory(PostgresDbResource.DDL_USER,
                "wrong"), NO_RETRY_WITHIN_THE_TEST);

        assertThat(sqlStates(outcome.error())).as("SQLSTATEs in the cause chain of %s", outcome.error())
                .contains("28P01");
        assertThat(outcome.creates()).as("attempts").isEqualTo(1);
    }

    @Test
    public void anUnknownDatabaseIsNotRetried() {
        final Outcome outcome = bootstrapWithRetry(V1, ConnectionFactories.get(
                "r2dbc:postgresql://ditto_ddl:ddl_secret@" + POSTGRES.getHost() + ":" + POSTGRES.getPort()
                        + "/no_such_db"), NO_RETRY_WITHIN_THE_TEST);

        assertThat(sqlStates(outcome.error())).as("SQLSTATEs in the cause chain of %s", outcome.error())
                .contains("3D000");
        assertThat(outcome.creates()).as("attempts").isEqualTo(1);
    }

    @Test
    public void sslRequiredAgainstAPlaintextServerIsNotRetried() {
        final Outcome outcome = bootstrapWithRetry(V1, ConnectionFactories.get(
                "r2dbc:postgresql://ditto_ddl:ddl_secret@" + POSTGRES.getHost() + ":" + POSTGRES.getPort()
                        + "/" + PostgresDbResource.DATABASE + "?sslMode=require"), NO_RETRY_WITHIN_THE_TEST);

        assertThat(causeChain(outcome.error())).as("cause chain of %s", outcome.error())
                .anySatisfy(cause -> assertThat(cause).isInstanceOf(R2dbcPermissionDeniedException.class));
        assertThat(outcome.creates()).as("attempts").isEqualTo(1);
    }

    // ---- attempts that connect but do not finish -------------------------------------------------------------------

    @Test
    public void aHeldAdvisoryLockEndsAsAnAttemptTimeout() {
        final Connection holder = Mono.from(connectionFactory.create()).block(TIMEOUT);
        try {
            Flux.from(holder.createStatement("SELECT pg_advisory_lock($1)")
                            .bind(0, PostgresSchemaManager.ADVISORY_LOCK_KEY)
                            .execute())
                    .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                    .then()
                    .block(TIMEOUT);

            final Outcome outcome = bootstrapWithRetry(V1, connectionFactory,
                    "attempt-timeout = 2s\n" + NO_RETRY_WITHIN_THE_TEST);

            assertThat(outcome.error()).isInstanceOf(SchemaBootstrapTimeoutException.class)
                    .hasMessageContaining("pg_locks");
            assertThat(outcome.creates()).as("attempts").isEqualTo(1);
            assertThat(outcome.elapsed()).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(4));
            assertThat(rows(SchemaBootstrapTimeoutException.advisoryLockQuery()))
                    .as("the hint's query shows the session holding the schema advisory lock")
                    .anySatisfy(row -> assertThat(row).endsWith("/true"));
        } finally {
            Mono.from(holder.close()).block(TIMEOUT);
        }
    }

    @Test
    public void aBlackholedEndpointEndsAsAnAttemptTimeout() {
        try (TcpGate gate = TcpGate.to(POSTGRES.getHost(), POSTGRES.getPort())) {
            gate.setMode(TcpGate.Mode.BLACKHOLE);
            final Outcome outcome = bootstrapWithRetry(V1, ConnectionFactories.get(
                    "r2dbc:postgresql://ditto_ddl:ddl_secret@" + gate.getHost() + ":" + gate.getPort() + "/"
                            + PostgresDbResource.DATABASE + "?sslMode=disable"),
                    "attempt-timeout = 2s\n" + NO_RETRY_WITHIN_THE_TEST);

            assertThat(outcome.error()).isInstanceOf(SchemaBootstrapTimeoutException.class);
            assertThat(outcome.creates()).as("attempts").isEqualTo(1);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private record Outcome(@Nullable Throwable error, int creates, Duration elapsed) {}

    private static void assertRefusedOnTheFirstAttempt(final PostgresSchemaDescriptor descriptor,
            final ConnectionFactory factory, final Class<? extends Throwable> type, final String messagePart) {
        final Outcome outcome = bootstrapWithRetry(descriptor, factory, NO_RETRY_WITHIN_THE_TEST);
        assertThat(outcome.error()).isInstanceOf(type).hasMessageContaining(messagePart);
        assertThat(outcome.creates()).as("attempts").isEqualTo(1);
        assertThat(outcome.elapsed()).isLessThan(Duration.ofSeconds(2));
    }

    private static Outcome bootstrapWithRetry(final PostgresSchemaDescriptor descriptor,
            final ConnectionFactory factory, final String bootstrapBlock) {
        final CountingConnectionFactory counting = new CountingConnectionFactory(factory);
        final long start = System.nanoTime();
        final Throwable error = catchThrowable(() -> PostgresSchemaManager.of(counting, descriptor,
                        bootstrapConfig(bootstrapBlock))
                .bootstrap(Mono.empty(), Mono.never()));
        return new Outcome(error, counting.creates(), Duration.ofNanos(System.nanoTime() - start));
    }

    private static void bootOnce(final PostgresSchemaDescriptor descriptor) {
        PostgresSchemaManager.of(connectionFactory, descriptor, bootstrapConfig("")).bootstrap();
    }

    private static PostgresConfig.BootstrapConfig bootstrapConfig(final String block) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql.bootstrap {\n" + block + "\n}"))
                .getBootstrapConfig();
    }

    private static PostgresSchemaDescriptor descriptor(final int version, final List<String> ddl) {
        final List<TableContract> contracts = List.of(new TableContract("bt_items", "PRIMARY KEY (id)",
                Map.of("id", "text", "val", "bigint"), Set.of("id")));
        return new PostgresSchemaDescriptor() {
            @Override
            public String component() {
                return COMPONENT;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public int minCompatibleVersion() {
                return 1;
            }

            @Override
            public List<String> ddlStatements() {
                return ddl;
            }

            @Override
            public List<TableContract> tableContracts() {
                return contracts;
            }
        };
    }

    private static List<Throwable> causeChain(@Nullable final Throwable error) {
        final List<Throwable> chain = new ArrayList<>();
        for (Throwable t = error; t != null && !chain.contains(t); t = t.getCause()) {
            chain.add(t);
        }
        return chain;
    }

    private static List<String> sqlStates(@Nullable final Throwable error) {
        final List<String> sqlStates = new ArrayList<>();
        for (final Throwable cause : causeChain(error)) {
            if (cause instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                sqlStates.add(r2dbc.getSqlState());
            }
        }
        return sqlStates;
    }

    private static List<String> rows(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> row.get(0) + "/" + row.get(1)))
                                .collectList(),
                        Connection::close)
                .block(TIMEOUT);
    }

    private static void runSql(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block(TIMEOUT);
    }

    /** Counts {@code create()} calls: one per bootstrap attempt. */
    private static final class CountingConnectionFactory implements ConnectionFactory {

        private final ConnectionFactory delegate;
        private final AtomicInteger creates = new AtomicInteger();

        private CountingConnectionFactory(final ConnectionFactory delegate) {
            this.delegate = delegate;
        }

        int creates() {
            return creates.get();
        }

        @Override
        public Publisher<? extends Connection> create() {
            creates.incrementAndGet();
            return delegate.create();
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return delegate.getMetadata();
        }

    }

}
