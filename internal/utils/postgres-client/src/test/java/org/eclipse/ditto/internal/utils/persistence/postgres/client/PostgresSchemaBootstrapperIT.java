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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link PostgresSchemaBootstrapper} against a real PostgreSQL (Testcontainers) that is unreachable when the bootstrap
 * starts: behind a {@link TcpGate} that first refuses, then resets and finally forwards connections, and as a container
 * that is only started while the bootstrap already retries. Both boots succeed once PostgreSQL becomes reachable.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSchemaBootstrapperIT {

    private static final String COMPONENT = "bootstrapper-it";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return COMPONENT;
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
            return List.of(
                    "CREATE TABLE IF NOT EXISTS bt_items (id TEXT COLLATE \"C\" PRIMARY KEY, val BIGINT NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS bt_items_val_idx ON bt_items (val)");
        }

        @Override
        public List<TableContract> tableContracts() {
            return List.of(new TableContract("bt_items", "PRIMARY KEY (id)", Map.of("id", "text", "val", "bigint"),
                    Set.of("id")));
        }
    };

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();

    private ActorSystem system;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSchemaBootstrapperIT", t);
        }
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void resetDatabaseAndStartSystem() {
        runSql(POSTGRES.newConnectionFactory(), "DROP TABLE IF EXISTS bt_items, schema_version CASCADE");
        system = ActorSystem.create("PostgresSchemaBootstrapperIT",
                ConfigFactory.parseString("pekko.coordinated-shutdown.run-by-jvm-shutdown-hook = off"));
        assertThat(system.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm"))
                .as("a test must never let coordinated shutdown exit the JVM").isFalse();
    }

    @After
    public void stopSystem() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void bootsOncePostgresBecomesReachableWithinTheBudget() throws Exception {
        try (TcpGate gate = TcpGate.to(POSTGRES.getHost(), POSTGRES.getPort())) {   // CLOSED: connection refused
            final AtomicInteger attempts = new AtomicInteger();
            final PostgresConfig config = config(gate.getHost(), gate.getPort(),
                    "attempt-timeout = 10s\nexit-on-failure = false\n"
                            + "retry { max-duration = 30s, min-backoff = 200ms, max-backoff = 1s }");
            final long start = System.nanoTime();
            final CompletableFuture<Void> boot = CompletableFuture.runAsync(() -> PostgresSchemaBootstrapper.bootstrap(
                    system, config, DESCRIPTOR, ddl -> Mono.fromRunnable(attempts::incrementAndGet)));

            awaitAtLeast(attempts, 2);
            gate.setMode(TcpGate.Mode.RESET);
            awaitTrue(() -> gate.acceptedConnections() >= 1);
            gate.setMode(TcpGate.Mode.OPEN);

            boot.get(30, TimeUnit.SECONDS);
            assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(30));
            assertThat(storedVersion(POSTGRES.newConnectionFactory())).isEqualTo(1);
            assertThat(CoordinatedShutdown.get(system).getShutdownReason()).isEmpty();
        }
    }

    @Test
    public void bootsWhenThePostgresContainerStartsLate() throws Exception {
        final int port = TcpGate.freePort();
        final PostgreSQLContainer<?> late = PostgresDbResource.newContainer()
                .withDatabaseName(PostgresDbResource.DATABASE)
                .withUsername(PostgresDbResource.DDL_USER)
                .withPassword(PostgresDbResource.DDL_PASSWORD)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(port), new ExposedPort(5432))));
        final AtomicInteger attempts = new AtomicInteger();
        final PostgresConfig config = config("127.0.0.1", port,
                "exit-on-failure = false\nretry { max-duration = 60s, min-backoff = 500ms, max-backoff = 2s }");
        final CompletableFuture<Void> boot = CompletableFuture.runAsync(() -> PostgresSchemaBootstrapper.bootstrap(
                system, config, DESCRIPTOR, ddl -> Mono.fromRunnable(attempts::incrementAndGet)));
        try {
            awaitAtLeast(attempts, 2);
            late.start();                     // initdb and server start while the bootstrap keeps retrying
            boot.get(60, TimeUnit.SECONDS);
            assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        } finally {
            late.stop();
        }
    }

    private static PostgresConfig config(final String host, final int port, final String bootstrapBlock) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql {\n"
                + "  uri = \"r2dbc:postgresql://" + host + ":" + port + "/" + PostgresDbResource.DATABASE + "\"\n"
                + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "  bootstrap {\n" + bootstrapBlock + "\n  }\n}"));
    }

    private static int storedVersion(final ConnectionFactory connectionFactory) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                                "SELECT version FROM schema_version WHERE component = $1")
                                        .bind(0, COMPONENT)
                                        .execute())
                                .flatMap(result -> result.map((row, meta) -> row.get(0, Integer.class)))
                                .next(),
                        Connection::close)
                .blockOptional(TIMEOUT)
                .orElse(-1);
    }

    private static void runSql(final ConnectionFactory connectionFactory, final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block(TIMEOUT);
    }

    private static void awaitAtLeast(final AtomicInteger counter, final int expected) throws InterruptedException {
        awaitTrue(() -> counter.get() >= expected);
    }

    private static void awaitTrue(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + TIMEOUT);
            }
            Thread.sleep(10L);
        }
    }

}
