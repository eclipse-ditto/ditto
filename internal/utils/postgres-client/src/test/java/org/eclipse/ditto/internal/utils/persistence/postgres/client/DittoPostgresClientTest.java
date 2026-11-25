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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Row;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Unit test for {@link DittoPostgresClient}, focused on the per-statement connection release-on-all-paths contract.
 * <p>
 * Drives a real {@link ConnectionPool} (max-size = 1) backed by a {@link StubConnectionFactory}, so a leaked connection
 * would manifest as either a non-zero {@code acquiredSize()} or a starved second acquire — no live PostgreSQL needed.
 * </p>
 */
public final class DittoPostgresClientTest {

    private static ActorSystem actorSystem;
    private static PostgresConfig postgresConfig;

    private StubConnectionFactory factory;
    private ConnectionPool pool;
    private DittoPostgresClient client;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("DittoPostgresClientTest");
        postgresConfig = DefaultPostgresConfig.of(ConfigFactory.empty());
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @Before
    public void setUp() {
        // factory + pool are created per-test so each test picks its own success/failure behaviour.
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    private void givenPool(final StubConnectionFactory aFactory) {
        factory = aFactory;
        pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .maxSize(1)
                .initialSize(0)
                .maxAcquireTime(Duration.ofSeconds(5))
                .build());
        client = DittoPostgresClient.forConnectionPool(pool, postgresConfig);
    }

    @Test
    public void successPathMapsRowsAndReleasesConnection() throws Exception {
        givenPool(StubConnectionFactory.emitting("a", "b", "c"));

        final List<String> result = run(client.executeSql("SELECT v FROM t",
                stmt -> stmt.bind("$1", 1),
                (row, meta) -> row.get(0, String.class)));

        assertThat(result).containsExactly("a", "b", "c");
        assertConnectionReleased();
    }

    @Test
    public void connectionIsReusableAfterSuccessProvingNoLeak() throws Exception {
        givenPool(StubConnectionFactory.emitting("x"));

        // With maxSize=1 a leaked connection on the first run would make the second run time out on acquire.
        run(client.executeSql("SELECT 1", null, mapFirstColumn()));
        final List<String> second = run(client.executeSql("SELECT 1", null, mapFirstColumn()));

        assertThat(second).containsExactly("x");
        assertConnectionReleased();
    }

    @Test
    public void failurePathReleasesConnection() {
        givenPool(StubConnectionFactory.failingOnExecute(new IllegalStateException("boom")));

        final CompletionStage<List<String>> stage = client.executeSql("SELECT 1", null, mapFirstColumn())
                .runWith(Sink.seq(), actorSystem);

        assertThat(awaitFailure(stage)).hasRootCauseMessage("boom");
        assertConnectionReleased();
    }

    @Test
    public void connectionIsReusableAfterFailureProvingNoLeakOnError() throws Exception {
        givenPool(StubConnectionFactory.failingOnExecute(new IllegalStateException("boom")));

        awaitFailure(client.executeSql("SELECT 1", null, mapFirstColumn()).runWith(Sink.seq(), actorSystem));
        // A leaked connection on the error path would starve this second acquire (maxSize=1).
        awaitFailure(client.executeSql("SELECT 1", null, mapFirstColumn()).runWith(Sink.seq(), actorSystem));

        assertConnectionReleased();
        assertThat(factory.openedConnections())
                .as("each acquire opened a fresh connection (previous one was released and reused/closed)")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    public void cancelPathReleasesConnection() throws Exception {
        givenPool(StubConnectionFactory.emitting("a", "b", "c", "d", "e"));

        // Take only the first element -> downstream cancels the stream before completion.
        final List<String> taken = run(client.executeSql("SELECT v FROM t", null, mapFirstColumn()).take(1));
        assertThat(taken).containsExactly("a");

        // After a cancel the connection must be released so a fresh acquire succeeds on a maxSize=1 pool.
        assertConnectionReleased();
        final List<String> after = run(client.executeSql("SELECT v FROM t", null, mapFirstColumn()).take(1));
        assertThat(after).containsExactly("a");
    }

    @Test
    public void executeUpdateEmitsRowCountAndReleasesConnection() throws Exception {
        givenPool(StubConnectionFactory.emitting("a", "b"));

        final List<Long> counts = run(client.executeUpdate("DELETE FROM t WHERE pid=$1",
                stmt -> stmt.bind("$1", "p")));

        assertThat(counts).containsExactly(2L);
        assertConnectionReleased();
    }

    private static java.util.function.BiFunction<Row, io.r2dbc.spi.RowMetadata, String> mapFirstColumn() {
        return (row, meta) -> row.get(0, String.class);
    }

    private <T> List<T> run(final Source<T, ?> source) throws Exception {
        return source.runWith(Sink.seq(), actorSystem).toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private Throwable awaitFailure(final CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            throw new AssertionError("Expected the stream to fail, but it completed normally");
        } catch (final java.util.concurrent.ExecutionException e) {
            return e;
        } catch (final InterruptedException | java.util.concurrent.TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private void assertConnectionReleased() {
        // The pool releases asynchronously after the terminal signal; give it a brief moment.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(pool.getMetrics().orElseThrow().acquiredSize())
                        .as("no connection still checked out of the pool")
                        .isZero());
    }

}
