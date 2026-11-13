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
package org.eclipse.ditto.internal.utils.persistence.postgres.readjournal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcTimeoutException;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Offline tests of how the PostgreSQL read journal reads the tag pid streams: the cursor of each window, windows
 * without a qualifying pid, the retry of a failed window from the same cursor, the failure once retries are
 * exhausted, the stop of retries when the stream is cancelled, and the priority order: the same windows, the
 * qualifying pids sorted by priority (ties in pid order) once the last window is read. A bind-aware stub answers
 * each statement from an in-memory "journal", so the tests see exactly which cursor every statement was bound with.
 */
public final class PostgresReadJournalKeysetPagingTest {

    /** Both window variants: the window over all pids after the cursor. */
    private static final String WINDOW = "SELECT DISTINCT pid FROM things_journal WHERE pid > $1 ORDER BY pid LIMIT $3";
    private static final String PRIORITY = "priority-";
    private static final String TAG = "always-alive";
    private static final int BATCH_SIZE = 10;
    /** {@code maxIdleTime} with one retry (log2 of 2 seconds). */
    private static final Duration ONE_RETRY = Duration.ofSeconds(2);
    /** {@code maxIdleTime} of the shipped pings (10 retries). */
    private static final Duration PING_INTERVAL = Duration.ofMinutes(10);

    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create("PostgresReadJournalKeysetPagingTest");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void afterClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void idOrderStreamReadsWindowsAfterTheLastPidOfThePreviousWindow() {
        final List<String> journal = pids(25);
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(WINDOW, binds -> windowRows(journal, binds, pid -> true));

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTag(TAG, BATCH_SIZE, Duration.ZERO, mat, false));

        assertThat(streamed).containsExactlyElementsOf(journal);
        assertThat(cursors(factory))
                .as("first window from the start, then after the last pid of each window, until an empty window")
                .containsExactly("", journal.get(9), journal.get(19), journal.get(24));
        assertThat(factory.executions()).allSatisfy(execution -> {
            assertThat(execution.binds().get(1)).isEqualTo(TAG);
            assertThat(execution.binds().get(2)).as("batchSize is the window size").isEqualTo(BATCH_SIZE);
        });
    }

    @Test
    public void windowsWithoutAQualifyingPidStillMoveTheWalkOn() {
        // 40 pids; only the last 5 qualify: three windows deliver nothing.
        final List<String> journal = pids(40);
        for (final boolean considerOnlyLatest : List.of(false, true)) {
            final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                    .respondTo(WINDOW, binds -> windowRows(journal, binds, pid -> journal.indexOf(pid) >= 35));

            final List<String> streamed = run(readJournal(factory)
                    .getJournalPidsWithTag(TAG, BATCH_SIZE, Duration.ZERO, mat, considerOnlyLatest));

            assertThat(streamed).as("considerOnlyLatest=%s", considerOnlyLatest)
                    .containsExactlyElementsOf(journal.subList(35, 40));
            assertThat(cursors(factory)).as("considerOnlyLatest=%s", considerOnlyLatest)
                    .containsExactly("", journal.get(9), journal.get(19), journal.get(29), journal.get(39));
        }
    }

    @Test
    public void failedWindowIsRetriedFromItsCursorWithoutRepeatingOrSkippingPids() {
        final List<String> journal = pids(25);
        final AtomicBoolean failedOnce = new AtomicBoolean();
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(WINDOW, binds -> {
                    if (journal.get(9).equals(binds.get(0)) && failedOnce.compareAndSet(false, true)) {
                        throw statementTimeout();
                    }
                    return windowRows(journal, binds, pid -> true);
                });

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTag(TAG, BATCH_SIZE, ONE_RETRY, mat, false));

        assertThat(streamed).containsExactlyElementsOf(journal);
        assertThat(cursors(factory))
                .as("the failed second window is read again after the first window's last pid, not from the start")
                .containsExactly("", journal.get(9), journal.get(9), journal.get(19), journal.get(24));
    }

    @Test
    public void streamFailsWithTheWindowErrorOnceTheRetriesAreExhausted() {
        final List<String> journal = pids(25);
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(WINDOW, binds -> {
                    if (journal.get(9).equals(binds.get(0))) {
                        throw statementTimeout();
                    }
                    return windowRows(journal, binds, pid -> true);
                });
        final List<String> delivered = new CopyOnWriteArrayList<>();

        final Throwable failure = catchThrowable(() -> readJournal(factory)
                .getJournalPidsWithTag(TAG, BATCH_SIZE, ONE_RETRY, mat, false)
                .runForeach(delivered::add, mat)
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS));

        assertThat(sqlStates(failure)).as("the window's own error: %s", failure).contains("57014");
        assertThat(delivered).as("the first window was delivered before the failure")
                .containsExactlyElementsOf(journal.subList(0, 10));
        assertThat(cursors(factory))
                .as("one attempt plus one retry of the failing window, then no further window")
                .containsExactly("", journal.get(9), journal.get(9));
    }

    @Test
    public void zeroMaxIdleTimeMeansNoRetry() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(WINDOW, binds -> {
                    throw statementTimeout();
                });

        final Throwable failure = catchThrowable(() -> run(readJournal(factory)
                .getJournalPidsWithTag(TAG, BATCH_SIZE, Duration.ZERO, mat, false)));

        assertThat(sqlStates(failure)).contains("57014");
        assertThat(factory.executions()).hasSize(1);
    }

    @Test
    public void cancellingTheStreamStopsTheRetriesOfAWindowInBackOff() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(WINDOW, binds -> {
                    throw statementTimeout();
                });
        final SinkQueueWithCancel<String> consumer = readJournal(factory)
                .getJournalPidsWithTag(TAG, BATCH_SIZE, PING_INTERVAL, mat, false)
                .runWith(Sink.queue(), mat);
        awaitExecutions(factory, 1);

        // the first attempt failed; the window waits 1 s before its first retry, then 2 s, ...
        consumer.cancel();
        TimeUnit.MILLISECONDS.sleep(3_500L);

        assertThat(factory.executions())
                .as("no retry after the stream was cancelled (without the stop: attempts at 0 s, ~1 s and ~3 s)")
                .hasSize(1);
    }

    @Test
    public void priorityOrderReadsWindowsAfterTheLastPidOfEachWindowAndSortsHighestFirstTiesInPidOrder() {
        final List<String> journal = pids(25);
        // every odd pid qualifies, with priority i % 4: ties at every level, spread over all windows
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 25).filter(i -> i % 2 == 1).forEach(i -> priorities.put(pid(i), i % 4));
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> priorityRows(journal, binds, priorities));

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, Duration.ZERO));

        assertThat(streamed).containsExactlyElementsOf(byPriorityThenPid(priorities));
        assertThat(cursors(factory))
                .as("first window from the start, then after the last pid of each window, qualifying or not")
                .containsExactly("", journal.get(9), journal.get(19), journal.get(24));
        assertThat(factory.executions()).allSatisfy(execution -> {
            assertThat(execution.sql()).contains(WINDOW).doesNotContain("ORDER BY COALESCE");
            assertThat(execution.binds()).containsEntry(1, TAG).containsEntry(2, BATCH_SIZE);
        });
    }

    @Test
    public void priorityOrderWalksOnThroughWindowsWithoutAQualifyingPid() {
        final List<String> journal = pids(40);
        final Map<String, Integer> priorities = Map.of(pid(36), 1, pid(40), 3);
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> priorityRows(journal, binds, priorities));

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, Duration.ZERO));

        assertThat(streamed).containsExactly(pid(40), pid(36));
        assertThat(cursors(factory))
                .containsExactly("", journal.get(9), journal.get(19), journal.get(29), journal.get(39));
    }

    @Test
    public void aFailedPriorityWindowIsRetriedFromItsCursorAndTheWindowsBeforeItAreKept() {
        final List<String> journal = pids(25);
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        journal.forEach(pid -> priorities.put(pid, journal.indexOf(pid) % 3));
        final AtomicBoolean failedOnce = new AtomicBoolean();
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> {
                    if (journal.get(9).equals(binds.get(0)) && failedOnce.compareAndSet(false, true)) {
                        throw statementTimeout();
                    }
                    return priorityRows(journal, binds, priorities);
                });

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, ONE_RETRY));

        assertThat(streamed).as("every pid once, in priority order, although the second window failed once")
                .containsExactlyElementsOf(byPriorityThenPid(priorities));
        assertThat(cursors(factory))
                .as("the failed second window is read again from its own cursor; the first is not read again")
                .containsExactly("", journal.get(9), journal.get(9), journal.get(19), journal.get(24));
    }

    @Test
    public void aPriorityWalkWhoseWindowExhaustsItsRetriesFailsWithoutDeliveringAPid() {
        final List<String> journal = pids(25);
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        journal.forEach(pid -> priorities.put(pid, 1));
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> {
                    if (journal.get(9).equals(binds.get(0))) {
                        throw statementTimeout();
                    }
                    return priorityRows(journal, binds, priorities);
                });
        final List<String> delivered = new CopyOnWriteArrayList<>();

        final Throwable failure = catchThrowable(() -> readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, ONE_RETRY)
                .runForeach(delivered::add, mat)
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS));

        assertThat(sqlStates(failure)).as("the window's own error: %s", failure).contains("57014");
        assertThat(delivered).as("nothing is delivered before the last window is read").isEmpty();
        assertThat(cursors(factory)).containsExactly("", journal.get(9), journal.get(9));
    }

    @Test
    public void thePriorityOrderSourceCanBeRunTwice() {
        final List<String> journal = pids(25);
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        journal.forEach(pid -> priorities.put(pid, journal.indexOf(pid) % 5));
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> priorityRows(journal, binds, priorities));
        final Source<String, NotUsed> walk = readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, Duration.ZERO);

        assertThat(run(walk)).containsExactlyElementsOf(byPriorityThenPid(priorities));
        assertThat(run(walk)).as("a second run of the same source collects afresh, nothing carried over")
                .containsExactlyElementsOf(byPriorityThenPid(priorities));
    }

    @Test
    public void aPriorityWalkSeesPidsAddedAheadOfItsCursorButNotBehindIt() {
        final List<String> journal = new CopyOnWriteArrayList<>(pids(25));
        final Map<String, Integer> priorities = new ConcurrentHashMap<>();
        journal.forEach(pid -> priorities.put(pid, 1));
        final String ahead = "thing:ns:030";    // after every pid of the journal: in a window not read yet
        final String behind = "thing:ns:005a";  // between pid 5 and pid 6: in the first window, already read
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .respondTo(PRIORITY, binds -> {
                    final List<Map<String, Object>> rows = priorityRows(journal, binds, priorities);
                    if ("".equals(binds.get(0))) {     // once the first window has been read
                        for (final String added : List.of(ahead, behind)) {
                            priorities.put(added, 9);
                            journal.add(added);
                        }
                        journal.sort(null);
                    }
                    return rows;
                });

        final List<String> streamed = run(readJournal(factory)
                .getJournalPidsWithTagOrderedByPriorityTag(TAG, BATCH_SIZE, Duration.ZERO));

        assertThat(streamed).hasSize(26).startsWith(ahead).doesNotContain(behind).doesNotHaveDuplicates();
    }

    @Test
    public void theInterfaceVariantReadsWindowsOfTheShippedBatchSize() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();

        assertThat(run(readJournal(factory).getJournalPidsWithTagOrderedByPriorityTag(TAG, Duration.ZERO))).isEmpty();

        assertThat(PostgresReadJournal.PRIORITY_WALK_WINDOW).as("the shipped read-journal-batch-size").isEqualTo(500);
        assertThat(factory.executions()).singleElement().satisfies(execution -> assertThat(execution.binds())
                .containsEntry(0, "").containsEntry(1, TAG)
                .containsEntry(2, PostgresReadJournal.PRIORITY_WALK_WINDOW));
    }

    // ---------------------------------------------------------------------------------------------------------

    private static List<Map<String, Object>> windowRows(final List<String> journal, final Map<Integer, Object> binds,
            final Predicate<String> qualifies) {
        final String after = (String) binds.get(0);
        final int window = (Integer) binds.get(2);
        return journal.stream()
                .filter(pid -> pid.compareTo(after) > 0)
                .limit(window)
                .map(pid -> Map.<String, Object>of("pid", pid, "qualifies", qualifies.test(pid)))
                .toList();
    }

    /** A priority window: the pids after the cursor (window size), whether they qualify, and their priority. */
    private static List<Map<String, Object>> priorityRows(final List<String> journal,
            final Map<Integer, Object> binds, final Map<String, Integer> priorityOfQualifying) {
        final String after = (String) binds.get(0);
        final int window = (Integer) binds.get(2);
        return journal.stream()
                .filter(pid -> pid.compareTo(after) > 0)
                .limit(window)
                .map(pid -> Map.<String, Object>of("pid", pid,
                        "qualifies", priorityOfQualifying.containsKey(pid),
                        "priority", priorityOfQualifying.getOrDefault(pid, 0)))
                .toList();
    }

    /** Highest priority first, equal priorities in pid order (the pids here are ASCII: String order = byte order). */
    private static List<String> byPriorityThenPid(final Map<String, Integer> priorities) {
        return priorities.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static PostgresReadJournal readJournal(final RecordingConnectionFactory factory) {
        final DittoPostgresClient client = StubClientSupport.clientFor(factory);
        return PostgresReadJournal.of(PostgresPersistenceOperations.of(client, "things"));
    }

    private static List<String> pids(final int count) {
        return IntStream.rangeClosed(1, count).mapToObj(PostgresReadJournalKeysetPagingTest::pid).toList();
    }

    private static String pid(final int i) {
        return String.format("thing:ns:%03d", i);
    }

    private static List<Object> cursors(final RecordingConnectionFactory factory) {
        final List<Object> cursors = new ArrayList<>();
        factory.executions().stream()
                .filter(execution -> execution.sql().contains(WINDOW))
                .forEach(execution -> cursors.add(execution.binds().get(0)));
        return cursors;
    }

    private static void awaitExecutions(final RecordingConnectionFactory factory, final int count)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (factory.executions().size() < count && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(factory.executions()).hasSizeGreaterThanOrEqualTo(count);
    }

    private static R2dbcTimeoutException statementTimeout() {
        return new R2dbcTimeoutException("canceling statement due to statement timeout", "57014");
    }

    private static List<String> sqlStates(final Throwable failure) {
        final List<String> sqlStates = new ArrayList<>();
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                sqlStates.add(r2dbc.getSqlState());
            }
        }
        return sqlStates;
    }

    private static <T> List<T> run(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.seq(), mat).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException("stream failed", e);
        }
    }

}
