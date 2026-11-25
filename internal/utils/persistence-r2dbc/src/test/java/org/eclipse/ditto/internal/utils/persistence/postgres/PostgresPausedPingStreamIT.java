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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * The persistence ping throttles its pid stream (by default to one pid per second), so the stream is consumed far more
 * slowly than the database produces it. This test pauses a consumer for longer than {@code statement_timeout} after
 * the first page, with the production connection wiring: {@code statement_timeout} as an r2dbc {@code OPTIONS}
 * startup parameter (as {@code ConnectionPoolFactory} binds it, here 3 s instead of the 60 s default), unnamed
 * statements and the pool-wide {@code fetch-size 0}.
 * <p>
 * A pid stream that keeps one statement open across the pause (a server-side portal) is cancelled with SQLSTATE
 * {@code 57014} and keeps its snapshot, holding back VACUUM, while the consumer waits. A stream that reads one short,
 * fully drained statement per page runs nothing on the server while the consumer waits and delivers every pid.
 * </p>
 * <p>
 * The pause tests run the streams with {@code maxIdleTime = 0}, i.e. without page retries, so a cancelled page fails
 * the stream instead of being retried out of sight. Two more tests make a page fail on purpose (blocked behind a table
 * lock until {@code statement_timeout} cancels it) and show the retry from the page's cursor and the failure once the
 * retries are exhausted.
 * </p>
 */
public final class PostgresPausedPingStreamIT {

    private static final String ENTITY = "policies";
    private static final String TAG = "always-alive";
    private static final String PID_PREFIX = "policy:ping-pause:";
    private static final int PIDS = 1_000;
    private static final int BATCH_SIZE = 100;
    private static final long STATEMENT_TIMEOUT_MS = 3_000L;
    /** Longer than the statement timeout, as a throttled ping's wait for the next page is. */
    private static final Duration PAUSE = Duration.ofSeconds(5);
    private static final Duration NO_RETRIES = Duration.ZERO;
    private static final Duration PULL_TIMEOUT = Duration.ofSeconds(60);

    private static PostgresDbResource postgres;
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresReadJournal readJournal;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void start() {
        postgres = new PostgresDbResource();
        try {
            postgres.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPausedPingStreamIT", t);
        }
        ddlFactory = postgres.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();
        execute("INSERT INTO " + ENTITY + "_journal (pid, sn, manifest, tags, event) "
                + "SELECT '" + PID_PREFIX + "' || lpad(i::text, 6, '0'), 1, 'manifest', "
                + "ARRAY['" + TAG + "', 'priority-' || (i % 10)], '{}'::jsonb "
                + "FROM generate_series(1, " + PIDS + ") AS i");

        final ConnectionFactory production = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, postgres.getHost())
                .option(ConnectionFactoryOptions.PORT, postgres.getPort())
                .option(ConnectionFactoryOptions.DATABASE, PostgresDbResource.DATABASE)
                .option(ConnectionFactoryOptions.USER, PostgresDbResource.DDL_USER)
                .option(ConnectionFactoryOptions.PASSWORD, PostgresDbResource.DDL_PASSWORD)
                .option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 0)
                .option(PostgresqlConnectionFactoryProvider.FETCH_SIZE, 0)
                .option(PostgresqlConnectionFactoryProvider.OPTIONS,
                        Map.of("statement_timeout", Long.toString(STATEMENT_TIMEOUT_MS)))
                .build());
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(production)
                .name("paused-ping-stream-pool").maxSize(4).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        readJournal = PostgresReadJournal.of(PostgresPersistenceOperations.of(client, ENTITY));
        system = ActorSystem.create("PostgresPausedPingStreamIT");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        if (client != null) {
            client.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    /** The policies ping: ID order, any event carrying the tag. */
    @Test
    public void idOrderPingStreamDeliversEveryPidAcrossAPauseLongerThanTheStatementTimeout() throws Exception {
        final List<String> pids = pauseAfterFirstPageThenDrain("ID order",
                () -> readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, NO_RETRIES, mat, false));

        assertThat(pids).hasSize(PIDS).doesNotHaveDuplicates().isSorted();
    }

    /** Connection-id retrieval's variant (newest event carries the tag), consumed the same way. */
    @Test
    public void idOrderNewestEventStreamDeliversEveryPidAcrossAPauseLongerThanTheStatementTimeout()
            throws Exception {
        final List<String> pids = pauseAfterFirstPageThenDrain("ID order, newest event only",
                () -> readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, NO_RETRIES, mat, true));

        assertThat(pids).hasSize(PIDS).doesNotHaveDuplicates().isSorted();
    }

    /** The connectivity ping: priority order. */
    @Test
    public void priorityOrderPingStreamDeliversEveryPidAcrossAPauseLongerThanTheStatementTimeout()
            throws Exception {
        final List<String> pids = pauseAfterFirstPageThenDrain("priority order",
                () -> readJournal.getJournalPidsWithTagOrderedByPriorityTag(TAG, NO_RETRIES));

        assertThat(pids).hasSize(PIDS).doesNotHaveDuplicates();
        assertThat(pids.stream().map(PostgresPausedPingStreamIT::priority).toList())
                .isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    /**
     * A page cancelled by {@code statement_timeout} (here: blocked behind a table lock that another session holds for
     * longer than the timeout) is retried after a back-off from the same cursor: the walk completes with every pid,
     * in order, once.
     */
    @Test
    public void pageCancelledByTheStatementTimeoutIsRetriedFromItsCursor() throws Exception {
        final ListAppender<ILoggingEvent> pageWarnings = capturePageWarnings();
        // 16 s maxIdleTime: 4 retries (back-off 1, 2, 4, 8 s), more than the lock below needs.
        final SinkQueueWithCancel<String> consumer =
                readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, Duration.ofSeconds(16), mat, false)
                        .runWith(Sink.<String>queue().withAttributes(Attributes.inputBuffer(1, 1)), mat);
        final List<String> received = new ArrayList<>();
        CompletableFuture<Void> lockReleased = CompletableFuture.completedFuture(null);
        try {
            for (int i = 0; i < BATCH_SIZE; i++) {
                pull(consumer).ifPresent(received::add);
            }
            // the next page that is not read ahead yet runs into the lock and is cancelled after 3 s.
            lockReleased = holdJournalLock(Duration.ofMillis(STATEMENT_TIMEOUT_MS + 2_000L));
            Optional<String> next = pull(consumer);
            while (next.isPresent()) {
                received.add(next.get());
                next = pull(consumer);
            }
        } finally {
            consumer.cancel();
            stopCapturing(pageWarnings);
            lockReleased.get(PULL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        assertThat(received).hasSize(PIDS).doesNotHaveDuplicates().isSorted();
        assertThat(pageWarnings.list)
                .as("the cancelled page was logged at WARN with its SQLSTATE")
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("SQLSTATE <57014>"));
    }

    /** Once a page's retries are exhausted, the stream fails with the page's error (here 57014). */
    @Test
    public void streamFailsWithThePageErrorOnceItsRetriesAreExhausted() throws Exception {
        // 2 s maxIdleTime: one retry (back-off 1 s); the lock outlasts both attempts.
        final SinkQueueWithCancel<String> consumer =
                readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, Duration.ofSeconds(2), mat, false)
                        .runWith(Sink.<String>queue().withAttributes(Attributes.inputBuffer(1, 1)), mat);
        int received = 0;
        @Nullable Throwable failure = null;
        CompletableFuture<Void> lockReleased = CompletableFuture.completedFuture(null);
        try {
            for (int i = 0; i < BATCH_SIZE; i++) {
                if (pull(consumer).isPresent()) {
                    received++;
                }
            }
            // held for 4 x statement_timeout: both attempts (cancelled at ~3 s and ~7 s) fail well before it ends.
            lockReleased = holdJournalLock(Duration.ofMillis(4 * STATEMENT_TIMEOUT_MS));
            while (pull(consumer).isPresent()) {
                received++;
            }
        } catch (final Exception e) {
            failure = e;
        } finally {
            consumer.cancel();
            // the next test must not run into the lock.
            lockReleased.get(PULL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        assertThat(describe(failure)).as("failed after %s pids", received).contains("sqlstate=57014");
        assertThat(received).isLessThan(PIDS);
    }

    /**
     * Takes an ACCESS EXCLUSIVE lock on the journal in another session and releases it after {@code duration}.
     *
     * @return completes once the lock is released.
     */
    private static CompletableFuture<Void> holdJournalLock(final Duration duration) {
        final Connection lockHolder = Mono.from(ddlFactory.create()).block(PULL_TIMEOUT);
        assertThat(lockHolder).isNotNull();
        Mono.from(lockHolder.beginTransaction())
                .thenMany(Flux.from(lockHolder.createStatement("LOCK TABLE " + ENTITY + "_journal IN ACCESS EXCLUSIVE "
                        + "MODE").execute()).flatMap(Result::getRowsUpdated))
                .then()
                .block(PULL_TIMEOUT);
        return Mono.delay(duration)
                .then(Mono.from(lockHolder.commitTransaction()))
                .then(Mono.from(lockHolder.close()))
                .toFuture();
    }

    private static ListAppender<ILoggingEvent> capturePageWarnings() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(PostgresReadJournal.class)).addAppender(appender);
        return appender;
    }

    private static void stopCapturing(final ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(PostgresReadJournal.class)).detachAppender(appender);
        appender.stop();
    }

    private static List<String> pauseAfterFirstPageThenDrain(final String label,
            final Supplier<Source<String, NotUsed>> stream) throws Exception {
        final List<String> received = new ArrayList<>();
        final List<Map<String, String>> duringPause = new ArrayList<>();
        @Nullable Throwable failure = null;
        try {
            // The consumer waits longer than statement_timeout, as the throttled ping does between statements.
            // Nothing may run on the server meanwhile: no statement, no open transaction, no snapshot holding back
            // VACUUM (sampled 1 s into the pause).
            PidStreamTestSupport.pullPauseDrain(stream.get(), BATCH_SIZE, PAUSE, () -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(1_000L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                duringPause.addAll(busyBackends());
            }, received, mat, PULL_TIMEOUT);
        } catch (final Exception e) {
            failure = e;
        }
        System.out.printf("[%s] delivered %d of %d pids; failure: %s; busy backends during the pause: %s%n",
                label, received.size(), PIDS, describe(failure), duringPause);

        assertThat(failure)
                .as("[%s] stream failed after %d of %d pids: %s", label, received.size(), PIDS, describe(failure))
                .isNull();
        assertThat(duringPause)
                .as("[%s] backends running a statement, in a transaction or holding a snapshot while the "
                        + "consumer paused", label)
                .isEmpty();
        return received;
    }

    /** Client backends of the test database, other than this query's, that are not plainly idle. */
    private static List<Map<String, String>> busyBackends() {
        return Flux.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement("SELECT pid::text AS pid, state, "
                                                + "backend_xmin::text AS backend_xmin, "
                                                + "xact_start::text AS xact_start, left(query, 60) AS query "
                                                + "FROM pg_stat_activity WHERE datname = current_database() "
                                                + "AND pid <> pg_backend_pid() AND backend_type = 'client backend' "
                                                + "AND (state <> 'idle' OR backend_xmin IS NOT NULL "
                                                + "OR xact_start IS NOT NULL)")
                                        .execute())
                                .flatMap(result -> result.map((row, meta) -> Map.of(
                                        "pid", String.valueOf(row.get("pid", String.class)),
                                        "state", String.valueOf(row.get("state", String.class)),
                                        "backend_xmin", String.valueOf(row.get("backend_xmin", String.class)),
                                        "xact_start", String.valueOf(row.get("xact_start", String.class)),
                                        "query", String.valueOf(row.get("query", String.class))))),
                        conn -> Mono.from(conn.close()))
                .collectList()
                .block(PULL_TIMEOUT);
    }

    private static int priority(final String pid) {
        return Integer.parseInt(pid.substring(PID_PREFIX.length())) % 10;
    }

    private static Optional<String> pull(final SinkQueueWithCancel<String> consumer) throws Exception {
        return PidStreamTestSupport.pull(consumer, PULL_TIMEOUT);
    }

    private static String describe(@Nullable final Throwable failure) {
        return PidStreamTestSupport.describe(failure);
    }

    private static void execute(final String sql) {
        Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then(),
                        conn -> Mono.from(conn.close()))
                .block(PULL_TIMEOUT);
    }

}
