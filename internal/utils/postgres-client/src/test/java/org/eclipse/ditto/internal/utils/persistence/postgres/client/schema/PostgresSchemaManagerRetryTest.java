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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Unit tests for the retrying bootstrap of {@link PostgresSchemaManager} ({@code bootstrap(Mono, Mono)}): it needs a
 * bootstrap configuration, runs the precondition before every attempt, and checks the server version only once.
 */
public final class PostgresSchemaManagerRetryTest {

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override public String component() { return "manager-retry-test"; }
        @Override public int version() { return 1; }
        @Override public int minCompatibleVersion() { return 1; }
        @Override public List<String> ddlStatements() { return List.of(); }
        @Override public List<TableContract> tableContracts() { return List.of(); }
    };

    @Test
    public void theAbortableBootstrapNeedsABootstrapConfig() {
        final PostgresSchemaManager legacy = PostgresSchemaManager.of(mock(ConnectionFactory.class), DESCRIPTOR);
        assertThatThrownBy(() -> legacy.bootstrap(Mono.empty(), Mono.never()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("manager-retry-test");
    }

    @Test
    public void theServerVersionIsCheckedOnceAcrossRetriedAttemptsAndThePreconditionBeforeEachAttempt() {
        final Connection connection = mock(Connection.class);
        // The bootstrap pipeline creates its statements while it is assembled; they never run here, because the
        // transaction fails to begin.
        final Statement unused = mock(Statement.class, RETURNS_SELF);
        doReturn(Flux.empty()).when(unused).execute();
        when(connection.createStatement(anyString())).thenReturn(unused);
        final Statement show = mock(Statement.class);
        when(connection.createStatement(PostgresServerVersion.SHOW_SERVER_VERSION_NUM)).thenReturn(show);
        doAnswer(invocation -> Flux.just(resultWithValue("150000"))).when(show).execute();
        doReturn(Mono.error(new R2dbcNonTransientResourceException("terminating connection", "57P01")))
                .doReturn(Mono.error(new R2dbcNonTransientResourceException("terminating connection", "57P01")))
                .doReturn(Mono.error(new SchemaBootException("stop")))
                .when(connection).beginTransaction();
        doReturn(Mono.empty()).when(connection).rollbackTransaction();
        doReturn(Mono.empty()).when(connection).close();
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        doAnswer(invocation -> Mono.just(connection)).when(factory).create();
        final AtomicInteger preconditionRuns = new AtomicInteger();

        final Throwable error = catchThrowable(() -> PostgresSchemaManager.of(factory, DESCRIPTOR,
                        bootstrapConfig("retry { min-backoff = 10ms, max-backoff = 20ms }"))
                .bootstrap(Mono.fromRunnable(preconditionRuns::incrementAndGet), Mono.never()));

        assertThat(error).isInstanceOf(SchemaBootException.class).hasMessage("stop");
        assertThat(preconditionRuns).hasValue(3);
        verify(factory, times(3)).create();
        verify(show, times(1)).execute();          // the PostgreSQL < 16 WARN is logged once per bootstrap call
    }

    @Test
    public void aRefusingPreconditionEndsTheBootstrapBeforeAnyConnection() {
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        final Throwable error = catchThrowable(() -> PostgresSchemaManager.of(factory, DESCRIPTOR, bootstrapConfig(""))
                .bootstrap(Mono.error(new SchemaBootException("encoding is SQL_ASCII")), Mono.never()));
        assertThat(error).isInstanceOf(SchemaBootException.class).hasMessageContaining("SQL_ASCII");
        verify(factory, never()).create();
    }

    private static PostgresConfig.BootstrapConfig bootstrapConfig(final String block) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql.bootstrap {\n" + block + "\n}"))
                .getBootstrapConfig();
    }

    @SuppressWarnings("unchecked")
    private static Result resultWithValue(@Nullable final String value) {
        final Row row = mock(Row.class);
        when(row.get(0, String.class)).thenReturn(value);
        final RowMetadata metadata = mock(RowMetadata.class);
        final Result result = mock(Result.class);
        when(result.map(any(BiFunction.class))).thenAnswer(invocation -> {
            final BiFunction<Row, RowMetadata, ?> mapping = invocation.getArgument(0);
            return Flux.just(mapping.apply(row, metadata));
        });
        return result;
    }

}
