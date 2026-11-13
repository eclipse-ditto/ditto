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

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations.JournalInsert;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Testcontainers IT pinning that the journal {@code INSERT} binds {@code written_at} via
 * {@code clock_timestamp()} (per-row wall-clock), NOT {@code now()} (transaction-start time) — ticket
 * {@code r2mlh-l15a} / {@code ditto-postgres-8t8}.
 * <p>
 * {@link PostgresPersistenceOperations#insertEvents(java.util.List)} writes all events of a single
 * {@code AtomicWrite} inside ONE transaction. Under PostgreSQL semantics {@code now()} returns the
 * <em>transaction-start</em> instant and is therefore identical for every row of that transaction, whereas
 * {@code clock_timestamp()} is re-evaluated per statement. Ditto's own actors produce no multi-event
 * {@code AtomicWrite}s, but Pekko's remember-entities shard store does (up to {@code max-updates-per-write} events),
 * and under {@code now()} every event's {@code written_at} would collapse to one instant. This IT synthesises a
 * 2-event {@code AtomicWrite} by calling
 * {@code insertEvents} directly with two rows sharing one pid and asserts the two persisted {@code written_at}
 * values are <em>distinct</em>:
 * </p>
 * <ul>
 *     <li>with {@code now()} the two rows share one txn-start instant → the assertion fails;</li>
 *     <li>with {@code clock_timestamp()} each row gets its own wall-clock instant → the assertion passes.</li>
 * </ul>
 * <p>
 * The two inserts run via {@code concatMap} (serialised, each a separate round-trip), so wall-clock advances by
 * far more than the microsecond resolution of {@code timestamptz} between them; a same-microsecond collision is
 * not realistically reachable here.
 * </p>
 * <p>
 * Docker unreachability turns into a JUnit skip via the {@link #startContainer() Assume} guard, not a failure.
 * </p>
 */
public final class JournalWrittenAtClockTimestampIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping JournalWrittenAtClockTimestampIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("written-at-pool").maxSize(4).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        POSTGRES.stop();
    }

    @Test
    public void twoEventAtomicWriteGetsDistinctWrittenAt() {
        final String pid = "thing:writtenat:atomicwrite";
        // One AtomicWrite carrying two events (sn 1 and 2) for the same pid — both inserted in ONE transaction.
        final List<JournalInsert> events = List.of(
                new JournalInsert(pid, 1L, "manifest", List.of(), "{\"v\":1}"),
                new JournalInsert(pid, 2L, "manifest", List.of(), "{\"v\":2}"));

        operations.insertEvents(events).block(Duration.ofSeconds(30));

        final List<Instant> writtenAt = readWrittenAt(pid);

        assertThat(writtenAt).hasSize(2);
        // now() (txn-start) would make these identical; clock_timestamp() makes them distinct per row.
        assertThat(writtenAt.get(0)).isNotEqualTo(writtenAt.get(1));
    }

    private static List<Instant> readWrittenAt(final String pid) {
        return Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                                "SELECT written_at FROM things_journal WHERE pid = $1 ORDER BY sn ASC")
                                        .bind(0, pid)
                                        .execute())
                                .concatMap(result -> result.map((row, meta) -> row.get("written_at", Instant.class)))
                                .collect(ArrayList<Instant>::new, ArrayList::add),
                        Connection::close)
                .block(Duration.ofSeconds(30));
    }
}
