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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Testcontainers IT pinning that {@code PostgresSnapshotStoreOps.deleteAsync(pid, criteria)} honours all four
 * {@link SnapshotSelectionCriteria} bounds (inclusive), with {@code written_at} taken from the snapshot metadata
 * timestamp, and touches only the given persistence id.
 * <p>
 * Each test seeds the snapshots {@code sn = 10, 20, …, 100} with timestamp {@code 1000 * sn} under its own pid,
 * because the container is shared by the class.
 * </p>
 * <p>
 * Docker unreachability turns into a JUnit skip via the {@link #startContainer() Assume} guard, not a failure.
 * </p>
 */
public final class PostgresSnapshotDeleteByCriteriaIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final List<Long> ALL = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);

    private static ActorSystem system;
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresSnapshotStoreOps snapshots;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException(
                    "Docker/Testcontainers unavailable — skipping PostgresSnapshotDeleteByCriteriaIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresSnapshotDeleteByCriteriaIT");
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("delete-by-criteria-pool").maxSize(4).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");
        snapshots = PostgresSnapshotStoreOps.of(operations);
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
    public void lowerSequenceBoundIsHonoured() throws Exception {
        final String pid = "thing:delcrit:lowerSeq";
        seed(pid);

        await(snapshots.deleteAsync(pid, SnapshotSelectionCriteria.create(80L, Long.MAX_VALUE, 50L, 0L)));

        assertThat(remaining(pid)).containsExactly(10L, 20L, 30L, 40L, 90L, 100L);
    }

    @Test
    public void equalBoundsDeleteOneSnapshot() throws Exception {
        final String pid = "thing:delcrit:equalBounds";
        seed(pid);

        await(snapshots.deleteAsync(pid, SnapshotSelectionCriteria.create(50L, Long.MAX_VALUE, 50L, 0L)));

        assertThat(remaining(pid)).containsExactly(10L, 20L, 30L, 40L, 60L, 70L, 80L, 90L, 100L);
    }

    @Test
    public void defaultLowerBoundsDeleteEverythingUpToTheUpperBound() throws Exception {
        final String pid = "thing:delcrit:defaultLower";
        seed(pid);

        await(snapshots.deleteAsync(pid, SnapshotSelectionCriteria.create(80L, Long.MAX_VALUE)));

        assertThat(remaining(pid)).containsExactly(90L, 100L);
    }

    @Test
    public void lowerTimestampBoundIsHonoured() throws Exception {
        final String pid = "thing:delcrit:lowerTs";
        seed(pid);

        await(snapshots.deleteAsync(pid,
                SnapshotSelectionCriteria.create(Long.MAX_VALUE, Long.MAX_VALUE, 0L, 55_000L)));

        assertThat(remaining(pid)).containsExactly(10L, 20L, 30L, 40L, 50L);
    }

    @Test
    public void otherPersistenceIdsAreNotTouched() throws Exception {
        final String pidA = "thing:delcrit:scopedA";
        final String pidB = "thing:delcrit:scopedB";
        seed(pidA);
        seed(pidB);

        await(snapshots.deleteAsync(pidA, SnapshotSelectionCriteria.create(Long.MAX_VALUE, Long.MAX_VALUE)));

        assertThat(remaining(pidA)).isEmpty();
        assertThat(remaining(pidB)).containsExactlyElementsOf(ALL);
    }

    private static void seed(final String pid) throws Exception {
        for (final long sn : ALL) {
            await(snapshots.saveAsync(new SnapshotMetadata(pid, sn, 1000L * sn), "{\"sn\":" + sn + "}"));
        }
    }

    private static List<Long> remaining(final String pid) {
        return Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                                "SELECT sn FROM things_snaps WHERE pid = $1 ORDER BY sn")
                                        .bind(0, pid)
                                        .execute())
                                .flatMap(result -> result.map((row, meta) -> row.get("sn", Long.class)))
                                .collectList(),
                        Connection::close)
                .block(Duration.ofSeconds(30));
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

}
