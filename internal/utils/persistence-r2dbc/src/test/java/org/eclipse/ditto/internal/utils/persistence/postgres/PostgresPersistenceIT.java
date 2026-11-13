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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.junit.AfterClass;
import org.junit.Assume;
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
import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Integration test for the journal, snapshot-store and read-journal PostgreSQL plugins against a real PostgreSQL
 * (PG 16) via Testcontainers.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped offline / when no Docker daemon is reachable. Covers the keystone
 * acceptance gates: the high-water-mark survives a physical delete, descending priority-tag ordering tolerates a
 * malformed tag, {@code loadAsync} honours {@code SnapshotSelectionCriteria}, the two
 * {@code deleteAsync} overloads behave distinctly, and a duplicate {@code (pid,sn)} write is idempotent.
 * </p>
 */
public final class PostgresPersistenceIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ActorSystem system;
    private static Materializer mat;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;
    private static PostgresJournalOps journal;
    private static PostgresSnapshotStoreOps snapshots;
    private static org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPersistenceIT", t);
        }
        final ConnectionFactory factory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresPersistenceIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("it-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
        journal = PostgresJournalOps.of(operations);
        snapshots = PostgresSnapshotStoreOps.of(operations);
        readJournal = org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal.of(operations);
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

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json, final String... tags) {
        final Object payload = tags.length == 0
                ? (Object) org.eclipse.ditto.json.JsonFactory.newObject(json)
                : new Tagged(org.eclipse.ditto.json.JsonFactory.newObject(json),
                        CollectionConverters.asScala(java.util.Arrays.asList(tags)).toSet());
        return AtomicWrite.apply(PersistentRepr.apply(payload, sn, pid, "manifest", false, ActorRef.noSender(), "w"));
    }

    @Test
    public void highWaterMarkSurvivesPhysicalDelete() throws Exception {
        final String pid = "thing:ns:hwm";
        await(journal.writeMessages(List.of(write(pid, 1L, "{\"a\":1}"))));
        await(journal.writeMessages(List.of(write(pid, 2L, "{\"a\":2}"))));
        await(journal.writeMessages(List.of(write(pid, 3L, "{\"a\":3}"))));

        await(journal.deleteMessagesTo(pid, 3L)); // physically delete all rows

        // the highest sequence number must NOT regress after the physical delete.
        assertThat(await(journal.readHighestSequenceNr(pid, 0L))).isEqualTo(3L);
    }

    @Test
    public void priorityTagOrderingIsDescendingAndToleratesMalformedTag() throws Exception {
        final String p1 = "thing:ns:p1";
        final String p2 = "thing:ns:p2";
        final String p3 = "thing:ns:p3";
        final String p5 = "thing:ns:p5";
        await(journal.writeMessages(List.of(write(p1, 1L, "{}", "always-alive", "priority-10"))));
        await(journal.writeMessages(List.of(write(p2, 1L, "{}", "always-alive", "priority-2"))));
        await(journal.writeMessages(List.of(write(p3, 1L, "{}", "always-alive", "priority-3"))));
        // p5 carries always-alive but a MALFORMED priority tag -> must not crash the query, kept at default 0.
        await(journal.writeMessages(List.of(write(p5, 1L, "{}", "always-alive", "priority-x"))));

        final List<String> ordered = readJournal.getJournalPidsWithTagOrderedByPriorityTag("always-alive",
                        Duration.ZERO)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);

        // highest priority first: 10 > 3 > 2; p5 present (priority 0) somewhere.
        assertThat(ordered).contains(p1, p2, p3, p5);
        assertThat(ordered.indexOf(p1)).isLessThan(ordered.indexOf(p3));
        assertThat(ordered.indexOf(p3)).isLessThan(ordered.indexOf(p2));
    }

    @Test
    public void loadAsyncHonorsCriteriaAndDeleteOverloadsDiffer() throws Exception {
        final String pid = "thing:ns:snap";
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 5L, 1000L), "{\"v\":5}"));
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 10L, 2000L), "{\"v\":10}"));
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 20L, 3000L), "{\"v\":20}"));

        // maxSequenceNr=10 must return sn=10, not sn=20.
        final Optional<SelectedSnapshot> at10 =
                await(snapshots.loadAsync(pid, SnapshotSelectionCriteria.create(10L, Long.MAX_VALUE)));
        assertThat(at10).isPresent();
        assertThat(at10.get().metadata().sequenceNr()).isEqualTo(10L);

        // metadata-form delete removes exactly one (pid, sn, written_at) row.
        await(snapshots.deleteAsync(new SnapshotMetadata(pid, 10L, 2000L)));
        assertThat(await(snapshots.loadAsync(pid, SnapshotSelectionCriteria.create(10L, Long.MAX_VALUE)))
                .map(s -> s.metadata().sequenceNr())).contains(5L);

        // criteria-form delete is upper-bounded (sn<=20, ts<=3000) -> removes the remaining rows.
        await(snapshots.deleteAsync(pid, SnapshotSelectionCriteria.create(20L, 3000L)));
        assertThat(await(snapshots.loadAsync(pid, SnapshotSelectionCriteria.latest()))).isEmpty();
    }

    @Test
    public void duplicateWriteWithSamePayloadIsIdempotent() throws Exception {
        final String pid = "thing:ns:dup";
        final String json = "{\"same\":true}";
        await(journal.writeMessages(List.of(write(pid, 1L, json))));

        // re-writing the same (pid, sn) with the same payload completes OK (commit-then-blip retry).
        final Iterable<Optional<Exception>> result = await(journal.writeMessages(List.of(write(pid, 1L, json))));
        assertThat(result).containsExactly(Optional.empty());
    }

    @Test
    public void replayReturnsEventsInSequenceOrder() throws Exception {
        final String pid = "thing:ns:replay";
        await(journal.writeMessages(List.of(write(pid, 1L, "{\"n\":1}"))));
        await(journal.writeMessages(List.of(write(pid, 2L, "{\"n\":2}"))));

        final List<Long> replayed = new java.util.ArrayList<>();
        await(journal.replayMessages(pid, 0L, Long.MAX_VALUE, Long.MAX_VALUE,
                repr -> replayed.add(repr.sequenceNr())));
        assertThat(replayed).containsExactly(1L, 2L);

        // read-journal historical recovery path returns the same rows.
        final long count = readJournal.currentEventsByPersistenceId(pid, 0L, Long.MAX_VALUE)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS).size();
        assertThat(count).isEqualTo(2L);
    }

}
