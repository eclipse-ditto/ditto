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

import io.r2dbc.spi.R2dbcException;

/**
 * PostgreSQL SQLSTATE constants + detection helpers shared by the runtime execution paths (mirroring the SQLSTATE
 * constants defined on {@code PostgresJournalOps}). Kept here in the {@code ...postgres} package so both
 * {@link DittoPostgresClient} (this package) and {@code PostgresPersistenceOperations} (the {@code ...postgres.ops}
 * package) can reuse the same undefined-table detection that drives the schema self-heal.
 */
public final class PostgresSqlStates {

    /**
     * PostgreSQL SQLSTATE {@code 42P01} = {@code undefined_table}: a statement referenced a table that does not exist.
     * The r2dbc-postgresql driver maps this (SQLSTATE class 42) to {@code io.r2dbc.spi.R2dbcBadGrammarException}. This is
     * the trigger for the on-demand schema self-heal — recreate the schema and retry the statement once.
     */
    public static final String UNDEFINED_TABLE = "42P01";

    private PostgresSqlStates() {
        throw new AssertionError();
    }

    /**
     * @param throwable the error to inspect (may be wrapped in {@code CompletionException}/{@code ExecutionException} or
     * carried as a cause of a reactive-pipeline error).
     * @return {@code true} if the throwable, or any exception in its cause chain, is an {@link R2dbcException} whose
     * SQLSTATE is {@value #UNDEFINED_TABLE} (missing table).
     */
    public static boolean isUndefinedTable(final Throwable throwable) {
        Throwable current = throwable;
        // Walk the cause chain: this naturally unwraps CompletionException / ExecutionException wrappers and finds a
        // nested R2dbcBadGrammarException carried as a cause under a reactive-pipeline error.
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException
                    && UNDEFINED_TABLE.equals(r2dbcException.getSqlState())) {
                return true;
            }
            final Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

}
