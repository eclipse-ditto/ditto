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

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import io.r2dbc.spi.R2dbcBadGrammarException;

/**
 * Unit test for {@link PostgresSqlStates}: the undefined-table ({@code 42P01}) detection that triggers the schema
 * self-heal, including unwrapping of {@code CompletionException}/{@code ExecutionException} wrappers and nested causes.
 */
public final class PostgresSqlStatesTest {

    @Test
    public void detectsDirectUndefinedTable() {
        assertThat(PostgresSqlStates.isUndefinedTable(
                new R2dbcBadGrammarException("relation does not exist", PostgresSqlStates.UNDEFINED_TABLE))).isTrue();
    }

    @Test
    public void detectsUndefinedTableThroughCompletionException() {
        final Throwable wrapped = new CompletionException(
                new R2dbcBadGrammarException("relation does not exist", PostgresSqlStates.UNDEFINED_TABLE));
        assertThat(PostgresSqlStates.isUndefinedTable(wrapped)).isTrue();
    }

    @Test
    public void detectsUndefinedTableThroughExecutionAndNestedCauses() {
        final Throwable wrapped = new ExecutionException("boom",
                new IllegalStateException("wrapper",
                        new R2dbcBadGrammarException("relation does not exist", PostgresSqlStates.UNDEFINED_TABLE)));
        assertThat(PostgresSqlStates.isUndefinedTable(wrapped)).isTrue();
    }

    @Test
    public void rejectsOtherSqlState() {
        assertThat(PostgresSqlStates.isUndefinedTable(
                new R2dbcBadGrammarException("syntax error", "42601"))).isFalse();
    }

    @Test
    public void rejectsNullSqlState() {
        // The single-arg R2dbcBadGrammarException constructor leaves the SQLSTATE null.
        assertThat(PostgresSqlStates.isUndefinedTable(new R2dbcBadGrammarException("no sqlstate"))).isFalse();
    }

    @Test
    public void rejectsNonR2dbcError() {
        assertThat(PostgresSqlStates.isUndefinedTable(new IllegalStateException("unrelated"))).isFalse();
    }

}
