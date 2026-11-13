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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteError;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;

import io.r2dbc.spi.R2dbcException;

/**
 * Pure (DB-free, side-effect-free) builders that translate the outcome of a per-thing PostgreSQL search write into the
 * backend-neutral {@link SearchWriteResult} consumed by {@code BulkWriteResultAckFlow} via {@link
 * SearchWriteResult#classify()}. Kept separate from {@code PostgresSearchUpdaterFlow} so the whole result-mapping matrix
 * (incl. the SQLSTATE → outcome rules) is unit-testable without a database.
 * <p>
 * The counts are shaped to reproduce the Mongo bulk-write acknowledgement rules exactly, honouring
 * {@link SearchWriteResult#classify()}'s fixed decision precedence
 * (UNACKNOWLEDGED &gt; CONSISTENCY_ERROR &gt; INCORRECT_PATCH &gt; WRITE_ERROR &gt; OK — the A4 contract):
 * </p>
 * <ul>
 *     <li><strong>Unique violation ({@value #SQLSTATE_UNIQUE_VIOLATION}) ⇒ SUCCESS</strong> — the Mongo duplicate-key
 *     rule ({@code BulkWriteResultAckFlow} treats a {@code DUPLICATE_KEY} error as success). Emitted as a
 *     {@link SearchWriteError.Category#DUPLICATE_KEY} error so it exercises the same classify() path;
 *     {@code classify() == OK}.</li>
 *     <li><strong>FK violation ({@value #SQLSTATE_FK_VIOLATION}) and every other SQLSTATE ⇒ per-thing WRITE_ERROR</strong>
 *     — a {@link SearchWriteError.Category#OTHER} error; {@code classify() == WRITE_ERROR}; the thing is NAcked and
 *     retried (an FK violation is a delete racing a flat insert, which a retry resolves).</li>
 *     <li><strong>Any non-SQL failure ⇒ unexpected error</strong> — pool acquire timeout, connection reset, etc.: mirrors
 *     the Mongo non-{@code BulkWriteException} path ({@code classify() == UNACKNOWLEDGED}). Never a stream failure.</li>
 * </ul>
 * <p>
 * For every ERROR result the {@code matchedCount} is set to {@code nonDeleteWriteModelCount} so the
 * INCORRECT_PATCH count-check (which classify() evaluates BEFORE the write-error check) is satisfied and the error
 * surfaces as a WRITE_ERROR (or, for a duplicate key, as OK) rather than being masked as an incorrect patch. The
 * matched/upserted counts are consumed only by classify() and the log line — the per-thing transaction has already
 * rolled back on any error.
 * </p>
 */
final class PostgresSearchWriteResults {

    /** PostgreSQL SQLSTATE {@code 23505} = {@code unique_violation} — the Mongo duplicate-key ⇒ success rule. */
    static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /**
     * PostgreSQL SQLSTATE {@code 23503} = {@code foreign_key_violation} — a thing-delete cascade racing a flat-row
     * insert; a per-thing WRITE_ERROR that a retry resolves.
     */
    static final String SQLSTATE_FK_VIOLATION = "23503";

    private PostgresSearchWriteResults() {
        throw new AssertionError();
    }

    /**
     * Success of an unconditional doc-row upsert (full write or emptied-out tombstone), which always affects exactly
     * one row.
     *
     * @param inserted whether the upsert inserted a new row ({@code true}) or updated an existing one ({@code false}),
     * as reported by the {@code RETURNING (xmax = 0)} idiom.
     * @return an acknowledged result classifying as OK.
     */
    static SearchWriteResult writeSuccess(final boolean inserted) {
        return SearchWriteResult.acknowledged(1, 1, inserted ? 0 : 1, 0, inserted ? 1 : 0, List.of());
    }

    /**
     * Success of a delete (including deleting a non-existent thing — Mongo delete semantics: acknowledged, 0 matched).
     *
     * @return an acknowledged result classifying as OK ({@code nonDeleteWriteModelCount == 0}, so the missing
     * matched/upserted counts are not read as an incorrect patch).
     */
    static SearchWriteResult deleteSuccess() {
        return SearchWriteResult.acknowledged(1, 0, 0, 0, 0, List.of());
    }

    /**
     * Success of a no-op write model — no DB operation, the search index is already in the desired state (reported as
     * one matched row so classify() sees no missing update).
     *
     * @return an acknowledged result classifying as OK.
     */
    static SearchWriteResult noopSuccess() {
        return SearchWriteResult.acknowledged(1, 1, 1, 0, 0, List.of());
    }

    /**
     * Translate a per-thing transaction failure into the neutral result, applying the SQLSTATE rules above.
     *
     * @param throwable the failure that rolled the transaction back.
     * @param delete whether the failed write model was a delete (drives {@code nonDeleteWriteModelCount}).
     * @return the neutral result (duplicate-key ⇒ OK, other SQLSTATE ⇒ WRITE_ERROR, non-SQL ⇒ UNACKNOWLEDGED).
     */
    static SearchWriteResult fromThrowable(final Throwable throwable, final boolean delete) {
        final int nonDeleteWriteModelCount = delete ? 0 : 1;
        final String sqlState = extractSqlState(throwable);
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            return SearchWriteResult.acknowledged(1, nonDeleteWriteModelCount, nonDeleteWriteModelCount, 0, 0,
                    List.of(new SearchWriteError(0, SearchWriteError.Category.DUPLICATE_KEY, message(throwable))));
        }
        if (sqlState != null) {
            return SearchWriteResult.acknowledged(1, nonDeleteWriteModelCount, nonDeleteWriteModelCount, 0, 0,
                    List.of(new SearchWriteError(0, SearchWriteError.Category.OTHER, message(throwable))));
        }
        return SearchWriteResult.unexpectedError(1, throwable);
    }

    /**
     * @param throwable a failure.
     * @return the SQLSTATE of the first {@link R2dbcException} in the cause chain that carries one, or {@code null} if
     * the failure is not an R2DBC error (or carries no SQLSTATE — e.g. a connection-loss resource error).
     */
    @Nullable
    static String extractSqlState(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof R2dbcException r2dbcException) {
                final String sqlState = r2dbcException.getSqlState();
                if (sqlState != null) {
                    return sqlState;
                }
            }
        }
        return null;
    }

    private static String message(final Throwable throwable) {
        final String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getName();
    }

}
