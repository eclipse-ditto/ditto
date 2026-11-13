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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapUnavailableException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.testkit.javadsl.TestKit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for the bootstrap wiring: the persistence schema is bootstrapped through the REAL boot seam — the
 * backend-agnostic {@link PersistenceBackendProvider#bootstrapSchema()} that each service {@code RootActor} calls during
 * boot — and that seam genuinely creates the schema on a fresh database and FAILS BOOT when it cannot.
 * <p>
 * Before this wiring the bootstrap had ZERO production call sites: a fresh database had no tables and the first journal
 * write hit {@code relation does not exist}. The fix added {@code bootstrapSchema()} to the
 * {@link PersistenceBackendProvider} interface (Mongo = no-op default, Postgres overrides) and made every RootActor call
 * it after {@code PersistenceBackendProvider.get(...)} and before {@code getReadJournal()} / before the persistent-actor
 * shard regions start.
 * </p>
 * <p>
 * Crucially this test does NOT call {@code provider.bootstrapSchema()} on a concrete
 * {@code PostgresPersistenceBackendProvider} directly. It resolves the provider through
 * {@link PersistenceBackendProvider#get(ActorSystem, Config)} — the exact extension-loading seam the RootActors use —
 * and invokes {@code bootstrapSchema()} on the returned <em>interface</em>. So it crosses the boot seam where the
 * bug lived: it would fail if the bootstrap were not reachable through that production path.
 * </p>
 * <p>
 * Runs under failsafe ({@code *IT}); skipped offline / when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresProviderBootstrapIT {

    private static final String RETRY_LOGGER =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapRetry";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final PostgresDbResource postgres = new PostgresDbResource();
    private ActorSystem system;
    private ConnectionFactory verifyFactory;
    @Nullable private ListAppender<ILoggingEvent> retryLog;

    @Before
    public void startContainer() {
        try {
            postgres.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresProviderBootstrapIT", t);
        }
        verifyFactory = postgres.newConnectionFactory();
    }

    @After
    public void stop() {
        if (retryLog != null) {
            ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).detachAppender(retryLog);
            retryLog.stop();
            retryLog = null;
        }
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
        postgres.stop();
    }

    /**
     * Boots the Postgres persistence profile and resolves the provider through {@link PersistenceBackendProvider#get},
     * then bootstraps through the interface seam — exactly what every RootActor does. Asserts the schema did not exist
     * before, exists after, and that re-running the same seam is idempotent.
     */
    @Test
    public void bootstrapThroughProviderSeamCreatesAllTables() {
        system = bootSystem(postgres.getHost(), postgres.getPort(), "");

        // Resolve the provider via the SAME extension-loading seam the RootActors use, on the SAME profile config.
        final PersistenceBackendProvider provider = PersistenceBackendProvider.get(system,
                ScopedConfig.dittoExtension(system.settings().config()));

        // Sanity: a fresh DB has no tables before the wired boot path runs.
        for (final String entity : PostgresSchema.ENTITIES) {
            assertThat(tableExists(entity + "_journal"))
                    .as("fresh DB must NOT have <%s_journal> before bootstrap", entity)
                    .isFalse();
        }

        // The wired boot seam: call bootstrapSchema() on the PersistenceBackendProvider *interface*, just like
        // ThingsRootActor / PoliciesRootActor / ConnectivityRootActor do before getReadJournal().
        provider.bootstrapSchema();

        // After the wired bootstrap, every entity's three tables + schema_version exist.
        for (final String entity : PostgresSchema.ENTITIES) {
            assertThat(tableExists(entity + "_journal")).as("<%s_journal> after bootstrap", entity).isTrue();
            assertThat(tableExists(entity + "_journal_seq")).as("<%s_journal_seq> after bootstrap", entity).isTrue();
            assertThat(tableExists(entity + "_snaps")).as("<%s_snaps> after bootstrap", entity).isTrue();
        }
        assertThat(tableExists("schema_version")).isTrue();

        // Re-running through the same seam is idempotent (IF NOT EXISTS + checksum match).
        provider.bootstrapSchema();
        assertThat(tableExists("things_journal")).isTrue();
    }

    /**
     * The 'fail boot if the schema cannot be established' guarantee, as the single-attempt fail-fast guard: with the
     * retry disabled, an unreachable database makes the wired boot seam throw at once (so a RootActor calling it would
     * fail boot) and no tables are silently created. This is the regression guard for the actual finding — the boot
     * path must fail rather than serve traffic against an absent schema.
     */
    @Test
    public void bootstrapThroughProviderSeamFailsBootWhenDatabaseUnreachable() {
        // A port nothing listens on -> connection refused. ssl.mode=disable so no boot guard fires; the failure is the
        // unreachable DB, which is exactly what 'tables absent and no bootstrap possible' looks like at boot.
        final int deadPort = postgres.getPort() == 1 ? 2 : 1;
        system = bootSystem(postgres.getHost(), deadPort, "retry.enabled = false\nexit-on-failure = false");

        final PersistenceBackendProvider provider = PersistenceBackendProvider.get(system,
                ScopedConfig.dittoExtension(system.settings().config()));

        // Boot must FAIL FAST: bootstrapSchema() throws when it cannot reach the DB / establish the schema. A RootActor
        // propagates this and the service refuses to boot, instead of serving traffic against a tableless database.
        assertThatThrownBy(provider::bootstrapSchema).isInstanceOf(R2dbcException.class);

        // And nothing was created on the real container (the dead-port system never touched it).
        assertThat(tableExists("things_journal"))
                .as("no tables may be created when the wired bootstrap fails")
                .isFalse();
    }

    /**
     * While the database stays unreachable, the wired boot seam retries for the configured budget and then gives up
     * with the last connection failure as cause.
     */
    @Test
    public void bootstrapThroughProviderSeamGivesUpAfterTheRetryBudget() {
        final int deadPort = postgres.getPort() == 1 ? 2 : 1;
        system = bootSystem(postgres.getHost(), deadPort,
                "exit-on-failure = false\nretry { max-duration = 2s, min-backoff = 200ms, max-backoff = 400ms }");
        final PersistenceBackendProvider provider = PersistenceBackendProvider.get(system,
                ScopedConfig.dittoExtension(system.settings().config()));
        final long start = System.nanoTime();

        assertThatThrownBy(provider::bootstrapSchema)
                .isInstanceOf(SchemaBootstrapUnavailableException.class)
                .hasMessageContaining("ditto.postgresql.bootstrap.retry.max-duration")
                .hasCauseInstanceOf(R2dbcException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(6));
        assertThat(tableExists("things_journal")).isFalse();
    }

    /**
     * A database that becomes reachable while the wired boot seam retries is bootstrapped by that same call.
     */
    @Test
    public void bootstrapThroughProviderSeamBootsOncePostgresBecomesReachable() throws Exception {
        try (TcpGate gate = TcpGate.to(postgres.getHost(), postgres.getPort())) {
            system = bootSystem(gate.getHost(), gate.getPort(),
                    "exit-on-failure = false\nretry { max-duration = 30s, min-backoff = 200ms, max-backoff = 1s }");
            final PersistenceBackendProvider provider = PersistenceBackendProvider.get(system,
                    ScopedConfig.dittoExtension(system.settings().config()));
            final ListAppender<ILoggingEvent> log = captureRetryLog();
            final CompletableFuture<Void> boot = CompletableFuture.runAsync(provider::bootstrapSchema);

            awaitTrue(() -> warnings(log) >= 2);
            gate.setMode(TcpGate.Mode.OPEN);

            boot.get(30, TimeUnit.SECONDS);
            assertThat(tableExists("things_journal")).isTrue();
            assertThat(CoordinatedShutdown.get(system).getShutdownReason()).isEmpty();
        }
    }

    /**
     * A finally failed bootstrap stops the service through coordinated shutdown (exit-on-failure defaults to true), so
     * that its orchestrator restarts it.
     */
    @Test
    public void aFailedBootstrapStopsTheServiceThroughCoordinatedShutdown() throws Exception {
        final int deadPort = postgres.getPort() == 1 ? 2 : 1;
        system = bootSystem(postgres.getHost(), deadPort, "retry.enabled = false");   // exit-on-failure: default true

        assertThatThrownBy(PersistenceBackendProvider.get(system,
                ScopedConfig.dittoExtension(system.settings().config()))::bootstrapSchema)
                .isInstanceOf(R2dbcException.class);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason())
                .containsInstanceOf(PostgresSchemaBootstrapper.BootstrapFailed.class);
        system.getWhenTerminated().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private ActorSystem bootSystem(final String host, final int port, final String bootstrapBlock) {
        // Load the real Postgres persistence profile (selects PostgresPersistenceBackendProvider + plugin-id mappings +
        // ditto.postgresql.* client defaults) and point ditto.postgresql.* at the container as the DDL role.
        // ssl.mode=disable keeps the boot guard quiet against the plaintext test container. This is the same wiring a
        // service boots with.
        final Config config = ConfigFactory.parseString(""
                + "ditto.postgresql {\n"
                + "  uri = \"r2dbc:postgresql://" + host + ":" + port + "/" + postgres.getDatabaseName() + "\"\n"
                + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "  pool.ddl-credentials {\n"
                + "    username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                + "    password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                + "  }\n"
                + "  bootstrap {\n" + bootstrapBlock + "\n}\n"
                + "}\n")
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.load())
                .resolve();
        final ActorSystem actorSystem = ActorSystem.create("PostgresProviderBootstrapIT", config);
        assertThat(actorSystem.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm"))
                .as("a test must never let coordinated shutdown exit the JVM").isFalse();
        return actorSystem;
    }

    private ListAppender<ILoggingEvent> captureRetryLog() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).addAppender(appender);
        retryLog = appender;
        return appender;
    }

    private static long warnings(final ListAppender<ILoggingEvent> log) {
        return log.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
    }

    private static void awaitTrue(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + WAIT);
            }
            Thread.sleep(10L);
        }
    }

    private boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "')"));
    }

    private String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(verifyFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }
}
