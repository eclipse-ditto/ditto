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
package org.eclipse.ditto.internal.utils.persistence.postgres.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * The statements whose whole result a caller reads at once (keyset windows of both orders, and pages) hand
 * their rows to the stream only after the statement has completed: the rows are collected by a single subscriber
 * that requests them all, never streamed to a Pekko subscriber that signals demand from its own thread. That is what
 * keeps r2dbc-postgresql 1.0.7 from delivering a row out of the statement's order (its receive fast path does not
 * take the drain guard), and keyset cursors depend on the order. The race itself is rare (about 1 page in 60,000 to
 * 200,000); these tests pin the reading mode deterministically: the stub emits the first row of a result at once and
 * the rest only after a gate opens, and no row may reach the stream before that.
 */
public final class PostgresPersistenceOperationsPageReadTest {

    private static final Duration NOT_BEFORE_THE_GATE = Duration.ofMillis(500);

    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create("PostgresPersistenceOperationsPageReadTest");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void afterClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void anyTaggedWindowIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("EXISTS (", windowRows(),
                ops -> ops.getPidWindowAbove("", "always-alive", false, 10));
    }

    @Test
    public void newestEventWindowIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("CROSS JOIN LATERAL", windowRows(),
                ops -> ops.getPidWindowAbove("", "always-alive", true, 10));
    }

    @Test
    public void priorityWindowIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("priority-", priorityWindowRows(),
                ops -> ops.getPrioritizedPidWindowAbove("", "always-alive", 10));
    }

    @Test
    public void pidPageIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("WHERE pid > $1 ORDER BY pid LIMIT $2", pidRows(),
                ops -> ops.getPidsAbove("", 10));
    }

    @Test
    public void newestSnapshotPageIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("GROUP BY pid ORDER BY pid LIMIT $5", List.of(
                        snapshotRow("thing:ns:a"), snapshotRow("thing:ns:b"), snapshotRow("thing:ns:c")),
                ops -> ops.getNewestSnapshotsAbove("", 10, Duration.ZERO, ""));
    }

    @Test
    public void newestSnapshotHeadPageIsCollectedBeforeItsRowsAreEmitted() throws Exception {
        assertRowsOnlyAfterTheStatementCompleted("GROUP BY pid ORDER BY pid LIMIT $5", List.of(
                        snapshotRow("thing:ns:a"), snapshotRow("thing:ns:b"), snapshotRow("thing:ns:c")),
                ops -> ops.getNewestSnapshotHeadsAbove("", 10, Duration.ZERO, ""));
    }

    private static <T> void assertRowsOnlyAfterTheStatementCompleted(final String sqlSubstring,
            final List<Map<String, Object>> rows,
            final Function<PostgresPersistenceOperations, Source<T, ?>> query) throws Exception {
        final CountDownLatch gate = new CountDownLatch(1);
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .onSql(sqlSubstring, rows)
                .gateRowsOnSql(sqlSubstring, gate);
        final DittoPostgresClient client = StubClientSupport.clientFor(factory);
        try {
            final SinkQueueWithCancel<T> consumer =
                    query.apply(PostgresPersistenceOperations.of(client, "things")).runWith(Sink.queue(), mat);
            final CompletableFuture<Optional<T>> first = consumer.pull().toCompletableFuture();

            TimeUnit.MILLISECONDS.sleep(NOT_BEFORE_THE_GATE.toMillis());
            assertThat(factory.executedContaining(sqlSubstring)).as("the statement ran").isTrue();
            assertThat(first)
                    .as("no row may reach the stream while the statement has not completed (the driver has "
                            + "delivered only its first row)")
                    .isNotDone();

            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isPresent();
            int received = 1;
            while (consumer.pull().toCompletableFuture().get(10, TimeUnit.SECONDS).isPresent()) {
                received++;
            }
            assertThat(received).isEqualTo(rows.size());
        } finally {
            gate.countDown();
            client.close();
        }
    }

    private static List<Map<String, Object>> windowRows() {
        return List.of(Map.of("pid", "thing:ns:a", "qualifies", true), Map.of("pid", "thing:ns:b", "qualifies", true),
                Map.of("pid", "thing:ns:c", "qualifies", false));
    }

    private static List<Map<String, Object>> priorityWindowRows() {
        return List.of(Map.of("pid", "thing:ns:a", "qualifies", true, "priority", 3),
                Map.of("pid", "thing:ns:b", "qualifies", false, "priority", 0),
                Map.of("pid", "thing:ns:c", "qualifies", true, "priority", 7));
    }

    private static List<Map<String, Object>> pidRows() {
        return List.of(Map.of("pid", "thing:ns:a"), Map.of("pid", "thing:ns:b"), Map.of("pid", "thing:ns:c"));
    }

    private static Map<String, Object> snapshotRow(final String pid) {
        return Map.of("pid", pid, "sn", 1L, "snapshot", "{}", "lifecycle", "ACTIVE", "written_at", Instant.EPOCH);
    }

}
