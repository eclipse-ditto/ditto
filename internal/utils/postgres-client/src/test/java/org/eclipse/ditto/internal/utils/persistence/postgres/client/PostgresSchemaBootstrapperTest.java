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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapAbortedException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapUnavailableException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigUtil;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import reactor.core.publisher.Mono;

/**
 * Unit test for {@link PostgresSchemaBootstrapper}: a coordinated shutdown ends the wait for PostgreSQL, a final
 * failure stops the service through coordinated shutdown (unless {@code exit-on-failure = false}), and the DDL
 * connection factory is disposed on every path. The "database" is a refused loopback port; no Docker needed.
 */
public final class PostgresSchemaBootstrapperTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override public String component() { return "bootstrapper-test"; }
        @Override public int version() { return 1; }
        @Override public int minCompatibleVersion() { return 1; }
        @Override public List<String> ddlStatements() { return List.of(); }
        @Override public List<TableContract> tableContracts() { return List.of(); }
    };

    private enum TestReason implements CoordinatedShutdown.Reason { INSTANCE }

    private ActorSystem system;

    @Before
    public void startSystem() {
        system = ActorSystem.create("PostgresSchemaBootstrapperTest",
                ConfigFactory.parseString("pekko.coordinated-shutdown.run-by-jvm-shutdown-hook = off"));
        assertThat(system.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm"))
                .as("a test must never let coordinated shutdown exit the JVM").isFalse();
    }

    @After
    public void stopSystem() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void coordinatedShutdownEndsAWaitWithoutLimit() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        final ClosingRecorder ddl = new ClosingRecorder();
        final CompletableFuture<Throwable> outcome = CompletableFuture.supplyAsync(() -> catchThrowable(() ->
                PostgresSchemaBootstrapper.bootstrap(system, refusedPortConfig(
                                "retry { max-duration = 0s, min-backoff = 50ms, max-backoff = 100ms }"),
                        DESCRIPTOR, counting(attempts), ddl.creator())));
        awaitAtLeast(attempts, 2);

        CoordinatedShutdown.get(system).run(TestReason.INSTANCE);

        assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason()).contains(TestReason.INSTANCE);
        assertThat(ddl.closed).hasValue(1);
        system.getWhenTerminated().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    @Test
    public void aShutdownThatAlreadyStartedEndsTheBootstrapBeforeTheFirstAttempt() throws Exception {
        TestKit.shutdownActorSystem(system);
        system = ActorSystem.create("PostgresSchemaBootstrapperTest", ConfigFactory.parseString(
                "pekko.coordinated-shutdown { run-by-jvm-shutdown-hook = off, terminate-actor-system = off, "
                        + "run-by-actor-system-terminate = off }"));
        CoordinatedShutdown.get(system).run(TestReason.INSTANCE, Optional.empty())
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        final AtomicInteger attempts = new AtomicInteger();
        final ClosingRecorder ddl = new ClosingRecorder();

        assertThatThrownBy(() -> PostgresSchemaBootstrapper.bootstrap(system, refusedPortConfig(""), DESCRIPTOR,
                counting(attempts), ddl.creator()))
                .isInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(attempts).hasValue(0);
        assertThat(ddl.closed).hasValue(1);
    }

    @Test
    public void aFatalFailureStopsTheServiceThroughCoordinatedShutdown() throws Exception {
        final SchemaBootException refusal = new SchemaBootException("checksum mismatch");
        final ClosingRecorder ddl = new ClosingRecorder();
        assertThatThrownBy(() -> PostgresSchemaBootstrapper.bootstrap(system, refusedPortConfig(""), DESCRIPTOR,
                factory -> Mono.error(refusal), ddl.creator()))
                .isSameAs(refusal);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .isEqualTo(new PostgresSchemaBootstrapper.BootstrapFailed("bootstrapper-test")));
        assertThat(ddl.closed).hasValue(1);
        system.getWhenTerminated().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    @Test
    public void exhaustedRetriesStopTheService() {
        assertThatThrownBy(() -> PostgresSchemaBootstrapper.bootstrap(system, refusedPortConfig(
                        "retry { max-duration = 300ms, min-backoff = 50ms, max-backoff = 100ms }"), DESCRIPTOR))
                .isInstanceOf(SchemaBootstrapUnavailableException.class);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason())
                .containsInstanceOf(PostgresSchemaBootstrapper.BootstrapFailed.class);
    }

    @Test
    public void anInvalidConfigurationAlsoStopsTheService() {
        final DittoConfigError invalid = new DittoConfigError("unsafe SSL configuration");
        assertThatThrownBy(() -> PostgresSchemaBootstrapper.bootstrap(system, refusedPortConfig(""), DESCRIPTOR,
                factory -> Mono.empty(), config -> {
                    throw invalid;
                }))
                .isSameAs(invalid);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason())
                .containsInstanceOf(PostgresSchemaBootstrapper.BootstrapFailed.class);
    }

    @Test
    public void exitOnFailureFalseKeepsTheServiceRunning() {
        final ClosingRecorder ddl = new ClosingRecorder();
        assertThatThrownBy(() -> PostgresSchemaBootstrapper.bootstrap(system,
                refusedPortConfig("exit-on-failure = false"), DESCRIPTOR,
                factory -> Mono.error(new SchemaBootException("drift")), ddl.creator()))
                .isInstanceOf(SchemaBootException.class);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason()).isEmpty();
        assertThat(system.whenTerminated().isCompleted()).isFalse();
        assertThat(ddl.closed).hasValue(1);
    }

    @Test
    public void theClientConfGivesThisShutdownReasonExitCodeOne() {
        final Config conf = ConfigFactory.parseResources("ditto-postgres-client.conf").resolve();
        assertThat(conf.getInt(ConfigUtil.joinPath("pekko", "coordinated-shutdown", "reason-overrides",
                PostgresSchemaBootstrapper.BootstrapFailed.class.getName(), "exit-code"))).isEqualTo(1);
    }

    private static PostgresConfig refusedPortConfig(final String bootstrapBlock) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql {\n"
                + "  uri = \"r2dbc:postgresql://127.0.0.1:" + TcpGate.freePort() + "/ditto\"\n"
                + "  username = \"u\"\n  password = \"p\"\n  ssl.mode = \"disable\"\n  connect-timeout = 1s\n"
                + "  bootstrap {\n" + bootstrapBlock + "\n  }\n}"));
    }

    private static Function<ConnectionFactory, Mono<Void>> counting(final AtomicInteger attempts) {
        return factory -> Mono.fromRunnable(attempts::incrementAndGet);
    }

    private static void awaitAtLeast(final AtomicInteger counter, final int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (counter.get() < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("fewer than " + expected + " attempts within " + WAIT);
            }
            Thread.sleep(10L);
        }
    }

    /** Wraps the real DDL factory and records close(). */
    private static final class ClosingRecorder {

        final AtomicInteger closed = new AtomicInteger();

        Function<PostgresConfig, ConnectionFactory> creator() {
            return config -> new RecordingFactory(ConnectionPoolFactory.createDdlConnectionFactory(config), closed);
        }

    }

    private record RecordingFactory(ConnectionFactory delegate, AtomicInteger closed)
            implements ConnectionFactory, io.r2dbc.spi.Closeable {

        @Override
        public Publisher<? extends Connection> create() {
            return delegate.create();
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public Publisher<Void> close() {
            return Mono.fromRunnable(closed::incrementAndGet);
        }

    }

}
