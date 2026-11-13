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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Proves the plan §3.2 shared-database coordination requirement: the event-sourcing persistence schema manager and the
 * search schema manager can bootstrap CONCURRENTLY against ONE fresh database with no {@code pg_type} duplicate-key
 * race, both {@code schema_version} component rows landing, and neither component's live-catalog verification tripping
 * over the other's tables.
 * <p>
 * The coordination mechanism under test lives entirely in {@code postgres-client}: the single shared advisory-lock key
 * and the single shared {@code schema_version} DDL/contract, applied by the generic {@link PostgresSchemaManager} for
 * every descriptor. This IT depends on {@code persistence-r2dbc} in TEST scope ONLY, purely to obtain the real
 * {@link PostgresSchema#descriptor() persistence descriptor} — the production search backend has no dependency on it.
 * </p>
 * <p>
 * Runs under the failsafe phase ({@code *IT}); skipped offline via the {@link #startContainer() Assume} guard.
 * </p>
 */
public final class ConcurrentSchemaBootstrapIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping ConcurrentSchemaBootstrapIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    public void persistenceAndSearchManagersBootstrapConcurrentlyOnAFreshDatabase() throws InterruptedException {
        // fresh database: drop everything both descriptors might create + the shared schema_version bookkeeping.
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync CASCADE");
        for (final String entity : PostgresSchema.ENTITIES) {
            runDdl("DROP TABLE IF EXISTS " + entity + "_journal, " + entity + "_journal_seq, "
                    + entity + "_snaps CASCADE");
        }
        runDdl("DROP TABLE IF EXISTS schema_version CASCADE");

        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);

        final Thread persistence = bootstrapThread(PostgresSchema.descriptor(), startGate, done, failures);
        final Thread search = bootstrapThread(PostgresSearchSchema.descriptor(), startGate, done, failures);
        persistence.start();
        search.start();

        // release both simultaneously to genuinely race the first-boot DDL against one fresh database.
        startGate.countDown();
        assertThat(done.await(120, TimeUnit.SECONDS))
                .as("both concurrent bootstraps must complete within the timeout").isTrue();

        assertThat(failures)
                .as("no concurrent-bootstrap failure (pg_type race / cross-component contract trip)")
                .isEmpty();

        // both component rows present
        assertThat(scalar("SELECT count(*) FROM schema_version WHERE component = '"
                + PostgresSchema.COMPONENT + "'")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM schema_version WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'")).isEqualTo("1");

        // both components' tables exist
        assertThat(tableExists("things_journal")).isTrue();
        assertThat(tableExists("search_things")).isTrue();
        assertThat(tableExists("search_flat")).isTrue();
        assertThat(tableExists("search_sync")).isTrue();
    }

    private static Thread bootstrapThread(final PostgresSchemaDescriptor descriptor, final CountDownLatch startGate,
            final CountDownLatch done, final List<Throwable> failures) {
        return new Thread(() -> {
            try {
                startGate.await();
                PostgresSchemaManager.of(connectionFactory, descriptor).bootstrap();
            } catch (final Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        }, "bootstrap-" + descriptor.component());
    }

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "')"));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(io.r2dbc.spi.Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
