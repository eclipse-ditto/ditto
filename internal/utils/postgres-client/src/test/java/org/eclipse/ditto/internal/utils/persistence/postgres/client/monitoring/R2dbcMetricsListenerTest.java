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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import io.r2dbc.proxy.core.MethodExecutionInfo;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;

/**
 * Unit test for {@link R2dbcMetricsListener}: records the command timer on success AND error, classifies the op-kind,
 * and records acquire latency — all into a capturing {@link PostgresMetricsRecorder} fake (no Kamon backend).
 */
public final class R2dbcMetricsListenerTest {

    private final CapturingRecorder recorder = new CapturingRecorder();
    private final R2dbcMetricsListener listener = R2dbcMetricsListener.of(recorder);

    @Test
    public void recordsSuccessfulQueryWithOpKindAndDuration() {
        listener.afterQuery(query("INSERT INTO things_journal VALUES (1)", true, Duration.ofMillis(7)));

        assertThat(recorder.commands).hasSize(1);
        final Command command = recorder.commands.get(0);
        assertThat(command.opKind).isEqualTo(PostgresMetrics.OP_INSERT);
        assertThat(command.status).isEqualTo(PostgresMetrics.STATUS_SUCCESS);
        assertThat(command.nanos).isEqualTo(Duration.ofMillis(7).toNanos());
    }

    @Test
    public void recordsFailedQueryWithErrorStatus() {
        listener.afterQuery(query("SELECT * FROM things_journal", false, Duration.ofMillis(3)));

        assertThat(recorder.commands).hasSize(1);
        assertThat(recorder.commands.get(0).status).isEqualTo(PostgresMetrics.STATUS_ERROR);
        assertThat(recorder.commands.get(0).opKind).isEqualTo(PostgresMetrics.OP_SELECT);
    }

    @Test
    public void treatsThrowablePresentAsError() {
        final QueryExecutionInfo info = query("DELETE FROM things_journal", true, Duration.ofMillis(1));
        when(info.getThrowable()).thenReturn(new RuntimeException("boom"));

        listener.afterQuery(info);

        assertThat(recorder.commands.get(0).status).isEqualTo(PostgresMetrics.STATUS_ERROR);
    }

    @Test
    public void recordsAcquireLatencyOnConnectionFactoryCreate() {
        final MethodExecutionInfo methodInfo = Mockito.mock(MethodExecutionInfo.class);
        when(methodInfo.getExecuteDuration()).thenReturn(Duration.ofMillis(12));

        listener.afterCreateOnConnectionFactory(methodInfo);

        assertThat(recorder.acquireNanos).containsExactly(Duration.ofMillis(12).toNanos());
    }

    @Test
    public void afterQuerySwallowsRecorderException() {
        final R2dbcMetricsListener throwingListener = R2dbcMetricsListener.of(new ThrowingRecorder());

        assertThatCode(() ->
                throwingListener.afterQuery(query("INSERT INTO things_journal VALUES (1)", true, Duration.ofMillis(7))))
                .doesNotThrowAnyException();
    }

    @Test
    public void afterCreateOnConnectionFactorySwallowsRecorderException() {
        final R2dbcMetricsListener throwingListener = R2dbcMetricsListener.of(new ThrowingRecorder());
        final MethodExecutionInfo methodInfo = Mockito.mock(MethodExecutionInfo.class);
        when(methodInfo.getExecuteDuration()).thenReturn(Duration.ofMillis(12));

        assertThatCode(() -> throwingListener.afterCreateOnConnectionFactory(methodInfo))
                .doesNotThrowAnyException();
    }

    private static QueryExecutionInfo query(final String sql, final boolean success, final Duration duration) {
        final QueryExecutionInfo info = Mockito.mock(QueryExecutionInfo.class);
        final QueryInfo queryInfo = new QueryInfo(sql);
        when(info.getQueries()).thenReturn(List.of(queryInfo));
        when(info.isSuccess()).thenReturn(success);
        when(info.getExecuteDuration()).thenReturn(duration);
        return info;
    }

    private static final class CapturingRecorder implements PostgresMetricsRecorder {

        private final List<Command> commands = new ArrayList<>();
        private final List<Long> acquireNanos = new ArrayList<>();

        @Override
        public void recordCommand(final String opKind, final String status, final long nanos) {
            commands.add(new Command(opKind, status, nanos));
        }

        @Override
        public void recordAcquire(final long nanos) {
            acquireNanos.add(nanos);
        }
    }

    private static final class ThrowingRecorder implements PostgresMetricsRecorder {

        @Override
        public void recordCommand(final String opKind, final String status, final long nanos) {
            throw new IllegalStateException("registry blew up");
        }

        @Override
        public void recordAcquire(final long nanos) {
            throw new IllegalStateException("registry blew up");
        }
    }

    private record Command(String opKind, String status, long nanos) {}

}
