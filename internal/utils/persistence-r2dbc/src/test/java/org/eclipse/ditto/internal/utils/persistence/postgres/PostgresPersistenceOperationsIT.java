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

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresEntitiesPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresNamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

/**
 * Integration test for the Postgres persistence-operations collaborators and the {@code SELECT 1} health
 * probe, against a real PostgreSQL (PG 16) via Testcontainers. Runs under failsafe ({@code *IT}); skipped offline / when
 * no Docker daemon is reachable.
 * <p>
 * Proves the purge-ops delete the right journal + journal_seq + snapshot rows for a namespace / entity and
 * <strong>nothing else</strong> (a second namespace / a sibling entity must survive a purge), and that {@code SELECT 1}
 * round-trips against the shared pool.
 */
public final class PostgresPersistenceOperationsIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final EntityType THING_TYPE = EntityType.of("thing");

    private static ActorSystem system;
    private static Materializer mat;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;
    private static PostgresJournalOps journal;
    private static PostgresSnapshotStoreOps snapshots;
    private static PostgresNamespacePersistenceOperations namespaceOps;
    private static PostgresEntitiesPersistenceOperations entityOps;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPersistenceOperationsIT", t);
        }
        final ConnectionFactory factory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresPersistenceOperationsIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("ops-it-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
        journal = PostgresJournalOps.of(operations);
        snapshots = PostgresSnapshotStoreOps.of(operations);
        namespaceOps = PostgresNamespacePersistenceOperations.of(operations, "thing:");
        entityOps = PostgresEntitiesPersistenceOperations.of(operations, "thing:");
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

    @Before
    public void truncate() throws Exception {
        // Start each test from an empty, isolated table set.
        await(client.executeUpdate("TRUNCATE things_journal, things_journal_seq, things_snaps", null)
                .runWith(Sink.ignore(), mat));
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json) {
        return AtomicWrite.apply(PersistentRepr.apply(org.eclipse.ditto.json.JsonFactory.newObject(json), sn, pid,
                "manifest", false, ActorRef.noSender(), "w"));
    }

    private long countJournal(final String pid) throws Exception {
        return await(client.executeSql("SELECT COUNT(*) AS c FROM things_journal WHERE pid = $1",
                        stmt -> stmt.bind(0, pid), (row, meta) -> row.get("c", Long.class))
                .runWith(Sink.head(), mat));
    }

    private long countJournalSeq(final String pid) throws Exception {
        return await(client.executeSql("SELECT COUNT(*) AS c FROM things_journal_seq WHERE pid = $1",
                        stmt -> stmt.bind(0, pid), (row, meta) -> row.get("c", Long.class))
                .runWith(Sink.head(), mat));
    }

    private long countSnaps(final String pid) throws Exception {
        return await(client.executeSql("SELECT COUNT(*) AS c FROM things_snaps WHERE pid = $1",
                        stmt -> stmt.bind(0, pid), (row, meta) -> row.get("c", Long.class))
                .runWith(Sink.head(), mat));
    }

    private static EntityId thingId(final String namespacedId) {
        return EntityId.of(THING_TYPE, namespacedId);
    }

    @Test
    public void namespacePurgeDeletesOnlyThatNamespaceAcrossAllThreeTables() throws Exception {
        // Two pids in namespace "purge.me", one pid in namespace "keep.me".
        final String purgeA = "thing:purge.me:a";
        final String purgeB = "thing:purge.me:b";
        final String keep = "thing:keep.me:c";
        await(journal.writeMessages(List.of(write(purgeA, 1L, "{\"v\":1}"))));
        await(journal.writeMessages(List.of(write(purgeB, 1L, "{\"v\":2}"))));
        await(journal.writeMessages(List.of(write(keep, 1L, "{\"v\":3}"))));
        await(snapshots.saveAsync(new SnapshotMetadata(purgeA, 1L, 1000L), "{\"s\":1}"));
        await(snapshots.saveAsync(new SnapshotMetadata(keep, 1L, 1000L), "{\"s\":3}"));

        final List<Throwable> errors = namespaceOps.purge("purge.me")
                .runWith(Sink.head(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(errors).as("namespace purge reports no errors").isEmpty();

        // The purged namespace is gone from ALL THREE tables.
        assertThat(countJournal(purgeA)).isZero();
        assertThat(countJournal(purgeB)).isZero();
        assertThat(countJournalSeq(purgeA)).isZero();
        assertThat(countJournalSeq(purgeB)).isZero();
        assertThat(countSnaps(purgeA)).isZero();

        // The OTHER namespace survives untouched (deletes the right rows and NOTHING else).
        assertThat(countJournal(keep)).isEqualTo(1L);
        assertThat(countJournalSeq(keep)).isEqualTo(1L);
        assertThat(countSnaps(keep)).isEqualTo(1L);
    }

    @Test
    public void namespacePurgeDoesNotMatchAPrefixSiblingNamespace() throws Exception {
        // "purge.me" must NOT delete "purge.metering" — the trailing ':' anchor prevents a prefix-bleed.
        final String target = "thing:purge.me:x";
        final String sibling = "thing:purge.metering:y";
        await(journal.writeMessages(List.of(write(target, 1L, "{}"))));
        await(journal.writeMessages(List.of(write(sibling, 1L, "{}"))));

        await(toStage(namespaceOps.purge("purge.me")));

        assertThat(countJournal(target)).isZero();
        assertThat(countJournal(sibling)).as("a prefix-sibling namespace must survive").isEqualTo(1L);
    }

    @Test
    public void entityPurgeDeletesOnlyThatEntityAcrossAllThreeTables() throws Exception {
        final String purge = "thing:ns:victim";
        final String sibling = "thing:ns:bystander";
        await(journal.writeMessages(List.of(write(purge, 1L, "{}"))));
        await(journal.writeMessages(List.of(write(purge, 2L, "{}"))));
        await(journal.writeMessages(List.of(write(sibling, 1L, "{}"))));
        await(snapshots.saveAsync(new SnapshotMetadata(purge, 1L, 1000L), "{}"));
        await(snapshots.saveAsync(new SnapshotMetadata(sibling, 1L, 1000L), "{}"));

        final List<Throwable> errors = entityOps.purgeEntity(thingId("ns:victim"))
                .runWith(Sink.head(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(errors).isEmpty();

        assertThat(countJournal(purge)).isZero();
        assertThat(countJournalSeq(purge)).isZero();
        assertThat(countSnaps(purge)).isZero();

        // Sibling entity in the SAME namespace survives (entity purge is an exact pid match).
        assertThat(countJournal(sibling)).isEqualTo(1L);
        assertThat(countJournalSeq(sibling)).isEqualTo(1L);
        assertThat(countSnaps(sibling)).isEqualTo(1L);
    }

    @Test
    public void healthCheckSelectOneSucceedsAgainstThePool() throws Exception {
        final Integer one = await(client.executeSql("SELECT 1 AS one", null,
                        (row, meta) -> row.get("one", Integer.class))
                .runWith(Sink.head(), mat));
        assertThat(one).isEqualTo(1);
    }

    private static CompletionStage<List<Throwable>> toStage(
            final org.apache.pekko.stream.javadsl.Source<List<Throwable>, org.apache.pekko.NotUsed> source) {
        return source.runWith(Sink.head(), mat).toCompletableFuture();
    }
}
