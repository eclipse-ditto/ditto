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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;

/**
 * Unit tests for the server-version check the bootstrap runs on its connection before the schema transaction: it warns
 * below PostgreSQL 16 and never fails, whatever the server answers.
 */
public final class PostgresSchemaManagerServerVersionCheckTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    @Nullable private Level originalLevel;

    @Before
    public void captureLog() {
        final Logger logger = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        log.start();
        logger.addAppender(log);
    }

    @After
    public void releaseLog() {
        final Logger logger = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        logger.detachAppender(log);
        logger.setLevel(originalLevel);
        log.stop();
    }

    @Test
    public void issuesShowServerVersionNumOnTheGivenConnection() {
        final Connection connection = connectionAnswering(Flux.just(resultWithValue("160004")));

        PostgresSchemaManager.checkServerVersion(connection, "things").block(TIMEOUT);

        verify(connection).createStatement("SHOW server_version_num");
    }

    @Test
    public void warnsOnceBelowSixteenAndCompletes() {
        final Connection connection = connectionAnswering(Flux.just(resultWithValue("150013")));

        assertThatCode(() -> PostgresSchemaManager.checkServerVersion(connection, "things").block(TIMEOUT))
                .doesNotThrowAnyException();

        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .isEqualTo(PostgresServerVersion.warning("things", 150013).orElseThrow()));
    }

    @Test
    public void staysQuietFromSixteenOn() {
        for (final String version : List.of("160000", "170006", "180000")) {
            final Connection connection = connectionAnswering(Flux.just(resultWithValue(version)));

            PostgresSchemaManager.checkServerVersion(connection, "search").block(TIMEOUT);
        }

        assertThat(warnings()).isEmpty();
    }

    @Test
    public void failingStatementNeverFailsTheCheck() {
        final Connection connection = connectionAnswering(
                Flux.error(new R2dbcNonTransientResourceException("connection reset")));

        assertThatCode(() -> PostgresSchemaManager.checkServerVersion(connection, "things").block(TIMEOUT))
                .doesNotThrowAnyException();

        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("could not be determined")
                .contains("connection reset"));
    }

    @Test
    public void statementThatCannotBeCreatedNeverFailsTheCheck() {
        final Connection connection = mock(Connection.class);
        when(connection.createStatement(anyString())).thenThrow(new IllegalStateException("closed"));

        assertThatCode(() -> PostgresSchemaManager.checkServerVersion(connection, "things").block(TIMEOUT))
                .doesNotThrowAnyException();

        assertThat(warnings()).singleElement().satisfies(message -> assertThat(message)
                .contains("could not be determined"));
    }

    @Test
    public void unparsableOrMissingValueNeverFailsTheCheck() {
        PostgresSchemaManager.checkServerVersion(connectionAnswering(Flux.just(resultWithValue("sixteen"))), "things")
                .block(TIMEOUT);
        PostgresSchemaManager.checkServerVersion(connectionAnswering(Flux.just(resultWithValue(null))), "things")
                .block(TIMEOUT);
        PostgresSchemaManager.checkServerVersion(connectionAnswering(Flux.empty()), "things").block(TIMEOUT);

        assertThat(warnings()).hasSize(3)
                .allSatisfy(message -> assertThat(message).contains("could not be determined"));
    }

    private List<String> warnings() {
        return log.list.stream()
                .filter(event -> event.getLevel().equals(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static Connection connectionAnswering(final Publisher<? extends Result> results) {
        final Connection connection = mock(Connection.class);
        final Statement statement = mock(Statement.class);
        when(connection.createStatement(anyString())).thenReturn(statement);
        doReturn(results).when(statement).execute();
        return connection;
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
