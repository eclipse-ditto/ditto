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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The search provider's {@link PostgresSearchPersistenceProvider#bootstrapSchema()} against a real PostgreSQL
 * (Testcontainers) behind a {@link TcpGate} that refuses connections first: the bootstrap, whose first database contact
 * on every attempt is the database prerequisites check, waits and boots once the gate opens.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSearchProviderBootstrapIT {

    private static final String RETRY_LOGGER =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapRetry";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private final ListAppender<ILoggingEvent> retryLog = new ListAppender<>();

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchProviderBootstrapIT",
                    t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void captureRetryLog() {
        retryLog.start();
        ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).addAppender(retryLog);
    }

    @After
    public void releaseRetryLog() {
        ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).detachAppender(retryLog);
        retryLog.stop();
    }

    @Test
    public void bootsOncePostgresBecomesReachable() throws Exception {
        try (TcpGate gate = TcpGate.to(POSTGRES.getHost(), POSTGRES.getPort())) {   // CLOSED: connection refused
            final Config config = ConfigFactory.parseString(
                            "ditto.postgresql {\n"
                                    + "  uri = \"r2dbc:postgresql://" + gate.getHost() + ":" + gate.getPort() + "/"
                                    + POSTGRES.getDatabaseName() + "\"\n"
                                    + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                                    + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                                    + "  ssl.mode = disable\n"
                                    + "  bootstrap { retry { max-duration = 30s, min-backoff = 200ms, "
                                    + "max-backoff = 1s } }\n"
                                    + "}\n")
                    .withFallback(ConfigFactory.load("postgres-search-test.conf"));
            final ActorSystem system = ActorSystem.create("PostgresSearchProviderBootstrapIT", config);
            try {
                final PostgresSearchPersistenceProvider provider =
                        new PostgresSearchPersistenceProvider(system, ConfigFactory.empty());
                final CompletableFuture<Void> boot = CompletableFuture.runAsync(provider::bootstrapSchema);

                awaitTrue(() -> warnings() >= 2);
                gate.setMode(TcpGate.Mode.OPEN);

                boot.get(30, TimeUnit.SECONDS);
                assertThat(query("SELECT component FROM schema_version WHERE component = '"
                        + PostgresSearchSchema.COMPONENT + "'")).containsExactly(PostgresSearchSchema.COMPONENT);
            } finally {
                TestKit.shutdownActorSystem(system);
            }
        }
    }

    private long warnings() {
        return retryLog.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
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

    private static List<String> query(final String sql) {
        final List<String> result = new ArrayList<>();
        Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(r -> r.map((row, meta) -> {
                                    final Object value = row.get(0);
                                    return value == null ? "" : value.toString();
                                })),
                        Connection::close)
                .doOnNext(result::add)
                .blockLast();
        return result;
    }

}
