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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;

import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;

import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Helpers shared by the pid-stream integration tests: capturing the SQL the production code issues, timing every
 * statement of a walk, consuming streams with timeouts, and describing failures by SQLSTATE.
 */
public final class PidStreamTestSupport {

    private PidStreamTestSupport() {
        throw new AssertionError();
    }

    /**
     * The SQL the production code issues for the first statement of a read-journal call, captured offline with a
     * recording stub (which returns no rows, so a paged stream stops after its first statement).
     *
     * @param entity the entity table set, e.g. {@code policies}.
     * @param call the read-journal call.
     * @param mat the materializer.
     * @return the SQL text.
     */
    public static String productionSql(final String entity,
            final Function<PostgresReadJournal, Source<String, ?>> call, final Materializer mat) {
        final RecordingConnectionFactory recorder = new RecordingConnectionFactory();
        final DittoPostgresClient recordingClient = StubClientSupport.clientFor(recorder);
        try {
            run(call.apply(PostgresReadJournal.of(PostgresPersistenceOperations.of(recordingClient, entity))), mat,
                    Duration.ofSeconds(30));
        } finally {
            recordingClient.close();
        }
        assertThat(recorder.executedSql()).hasSize(1);
        return recorder.executedSql().get(0);
    }

    /**
     * Runs a source to completion and returns its elements.
     *
     * @throws AssertionError if the stream fails or does not complete in time.
     */
    public static <T> List<T> run(final Source<T, ?> source, final Materializer mat, final Duration timeout) {
        try {
            return source.runWith(Sink.seq(), mat).toCompletableFuture().get(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed: " + describe(e), e);
        }
    }

    /** Pulls one element (empty at completion), failing on a timeout. */
    public static <T> Optional<T> pull(final SinkQueueWithCancel<T> queue, final Duration timeout) throws Exception {
        return queue.pull().toCompletableFuture().get(timeout.toSeconds(), TimeUnit.SECONDS);
    }

    /** Pulls until the stream completes. */
    public static <T> void drain(final SinkQueueWithCancel<T> queue, final List<T> into, final Duration timeout)
            throws Exception {
        Optional<T> next = pull(queue, timeout);
        while (next.isPresent()) {
            into.add(next.get());
            next = pull(queue, timeout);
        }
    }

    /**
     * Consumes a stream as the throttled persistence ping does: pulls {@code pullsBeforePause} elements, pauses for
     * {@code pause} (running {@code duringPause} at its start, e.g. to sample the server), then pulls to the end.
     * Elements go to {@code into}, so a caller sees what arrived before a failure; a stream failure is thrown.
     */
    public static <T> void pullPauseDrain(final Source<T, ?> stream, final int pullsBeforePause,
            final Duration pause, final Runnable duringPause, final List<T> into, final Materializer mat,
            final Duration pullTimeout) throws Exception {
        final SinkQueueWithCancel<T> queue = stream.runWith(Sink.queue(), mat);
        try {
            for (int i = 0; i < pullsBeforePause; i++) {
                pull(queue, pullTimeout).ifPresent(into::add);
            }
            final long pauseEnd = System.nanoTime() + pause.toNanos();
            duringPause.run();
            final long left = pauseEnd - System.nanoTime();
            if (left > 0) {
                TimeUnit.NANOSECONDS.sleep(left);
            }
            drain(queue, into, pullTimeout);
        } finally {
            queue.cancel();
        }
    }

    /** The cause chain with the SQLSTATE of every r2dbc exception in it. */
    public static String describe(@Nullable final Throwable failure) {
        if (failure == null) {
            return "none";
        }
        final StringBuilder sb = new StringBuilder();
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            sb.append(t.getClass().getSimpleName());
            if (t instanceof R2dbcException r2dbc) {
                sb.append("[sqlstate=").append(r2dbc.getSqlState()).append(']');
            }
            sb.append(": ").append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    /** The SQLSTATEs of the r2dbc exceptions in the cause chain. */
    public static List<String> sqlStates(@Nullable final Throwable failure) {
        final List<String> sqlStates = new ArrayList<>();
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                sqlStates.add(r2dbc.getSqlState());
            }
        }
        return sqlStates;
    }

    /**
     * Records every executed statement whose SQL contains a marker: its duration (until its last row was read) and
     * its row count, through an r2dbc-proxy listener in front of a connection factory.
     */
    public static final class StatementRecorder implements ProxyExecutionListener {

        private final String sqlMarker;
        private final List<Long> nanos = new CopyOnWriteArrayList<>();
        private final List<Integer> rows = new CopyOnWriteArrayList<>();

        public StatementRecorder(final String sqlMarker) {
            this.sqlMarker = sqlMarker;
        }

        /** Wraps a connection factory so this recorder sees its statements. */
        public ConnectionFactory decorate(final ConnectionFactory factory) {
            return ProxyConnectionFactory.builder(factory).listener(this).build();
        }

        @Override
        public void afterQuery(final QueryExecutionInfo execInfo) {
            if (execInfo.getQueries().stream().anyMatch(query -> query.getQuery().contains(sqlMarker))) {
                nanos.add(execInfo.getExecuteDuration().toNanos());
                rows.add(execInfo.getCurrentResultCount());
            }
        }

        public List<Long> nanos() {
            return List.copyOf(nanos);
        }

        public List<Integer> rows() {
            return List.copyOf(rows);
        }

        /** The slowest statement in milliseconds. */
        public double slowestMillis() {
            return nanos.stream().mapToLong(Long::longValue).max().orElse(0L) / 1_000_000.0;
        }

        /** A percentile of the statement durations in milliseconds (nearest rank). */
        public double percentileMillis(final int percentile) {
            return PidStreamTestSupport.percentile(nanos.stream().sorted().toList(), percentile) / 1_000_000.0;
        }
    }

    /** Nearest-rank percentile of sorted values. */
    public static long percentile(final List<Long> sorted, final int percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        final int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    /** Median of unsorted values. */
    public static long median(final List<Long> values) {
        return percentile(values.stream().sorted().toList(), 50);
    }

}
