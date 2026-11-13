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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
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
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Testcontainers IT pinning {@code getNewestSnapshotsAbove}'s tie-break behaviour on rows that share the
 * same {@code (pid, sn)} primary-key prefix (the snaps PK is {@code (pid, sn, written_at)}, so a re-snapshot
 * at the same sequence number is allowed and produces multiple rows that differ only in {@code written_at}).
 * <p>
 * {@code r2mlh-m7}: the per-pid winner is picked by {@code ORDER BY sn DESC, written_at DESC LIMIT 1} (loose index scan).
 * Without the {@code written_at DESC} tiebreaker the row returned for a {@code (pid, sn)} tie is arbitrary;
 * with it, the row with the newest {@code written_at} must win. This IT seeds two rows for one
 * {@code (pid, sn)} (deliberately inserting the OLDER {@code written_at} last, so a naive
 * physical/insertion-order scan would pick the wrong row) and asserts the newer payload is returned.
 * </p>
 * <p>
 * Docker unreachability turns into a JUnit skip via the {@link #startContainer() Assume} guard, not a failure.
 * </p>
 */
public final class SnapshotTieBreakIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ActorSystem system;
    private static Materializer mat;
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping SnapshotTieBreakIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("SnapshotTieBreakIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("tiebreak-pool").maxSize(4).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");
        readJournal = PostgresReadJournal.of(operations);
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    @Test
    public void getNewestSnapshotsAbovePicksNewestWrittenAtOnPidSnTie() throws Exception {
        final String pid = "thing:tiebreak:samepidsn";
        // Two rows, SAME (pid, sn) but distinct written_at. The PK (pid, sn, written_at) permits both.
        // Insert the OLDER written_at FIRST on purpose: among rows tied on the (pid, sn DESC) sort key, a query
        // WITHOUT the written_at DESC tiebreaker resolves the newest-per-pid pick arbitrarily — in practice physical
        // (heap/insertion) order — so it would surface this older (wrong) row. The written_at DESC tiebreaker is
        // what forces the newer row to win deterministically.
        runDdl("INSERT INTO things_snaps (pid, sn, snapshot, lifecycle, written_at) VALUES ('" + pid
                + "', 5, '{\"v\":\"older\"}'::jsonb, NULL, TIMESTAMPTZ '2026-06-15 10:00:00+00')");
        runDdl("INSERT INTO things_snaps (pid, sn, snapshot, lifecycle, written_at) VALUES ('" + pid
                + "', 5, '{\"v\":\"newer\"}'::jsonb, NULL, TIMESTAMPTZ '2026-06-15 12:00:00+00')");

        // request the field this test reads: a stream without fields carries no payload
        final List<SnapshotEntry> entries =
                readJournal.getNewestSnapshotsAbove("thing:tiebreak:", 100, false, Duration.ZERO, mat, "v")
                        .filter(e -> e.getPid().filter(pid::equals).isPresent())
                        .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);

        // The newest-per-pid pick collapses to exactly one row per pid, and on the (pid, sn) tie the newer written_at wins.
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSequenceNumber().getAsLong()).isEqualTo(5L);
        assertThat(entries.get(0).getJson().getValue("v").map(org.eclipse.ditto.json.JsonValue::asString))
                .contains("newer");
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then(),
                        Connection::close)
                .block(Duration.ofSeconds(30));
    }
}
