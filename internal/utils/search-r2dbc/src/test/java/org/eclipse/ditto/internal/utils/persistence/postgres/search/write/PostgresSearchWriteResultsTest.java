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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteError;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResultStatus;
import org.junit.Test;

import io.r2dbc.spi.R2dbcException;

/**
 * Unit tests for the pure result-mapping matrix ({@link PostgresSearchWriteResults}) — constructible without a
 * database. Every row asserts the neutral {@link SearchWriteResult#classify()} outcome the A4 contract requires:
 * duplicate-key (23505) ⇒ OK, FK (23503) / other SQLSTATE ⇒ WRITE_ERROR, non-SQL ⇒ UNACKNOWLEDGED, and the
 * success/delete/noop shapes.
 */
public final class PostgresSearchWriteResultsTest {

    @Test
    public void fullWriteInsertSuccessClassifiesOkAsUpsert() {
        final SearchWriteResult result = PostgresSearchWriteResults.writeSuccess(true);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(result.isAcknowledged()).isTrue();
        assertThat(result.getUpsertedCount()).isEqualTo(1);
        assertThat(result.getMatchedCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void fullWriteUpdateSuccessClassifiesOkAsMatch() {
        final SearchWriteResult result = PostgresSearchWriteResults.writeSuccess(false);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getUpsertedCount()).isEqualTo(0);
    }

    @Test
    public void deleteSuccessClassifiesOkWithZeroNonDeleteCount() {
        final SearchWriteResult result = PostgresSearchWriteResults.deleteSuccess();
        // nonDeleteWriteModelCount == 0 so matched+upserted (0) is NOT read as a missing patch.
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(result.getNonDeleteWriteModelCount()).isEqualTo(0);
        assertThat(result.getMatchedCount()).isEqualTo(0);
    }

    @Test
    public void noopSuccessClassifiesOk() {
        final SearchWriteResult result = PostgresSearchWriteResults.noopSuccess();
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(result.getMatchedCount()).isEqualTo(1);
    }

    @Test
    public void uniqueViolationOnWriteIsSuccessViaDuplicateKeyRule() {
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(
                sqlException(PostgresSearchWriteResults.SQLSTATE_UNIQUE_VIOLATION), false);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(result.getErrors()).singleElement()
                .extracting(SearchWriteError::category)
                .isEqualTo(SearchWriteError.Category.DUPLICATE_KEY);
    }

    @Test
    public void uniqueViolationOnDeleteIsSuccess() {
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(
                sqlException(PostgresSearchWriteResults.SQLSTATE_UNIQUE_VIOLATION), true);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.OK);
    }

    @Test
    public void foreignKeyViolationOnWriteIsWriteError() {
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(
                sqlException(PostgresSearchWriteResults.SQLSTATE_FK_VIOLATION), false);
        // matched == nonDeleteWriteModelCount so the INCORRECT_PATCH count-check (evaluated first) passes and the
        // real error surfaces as WRITE_ERROR, per the classify() precedence.
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.WRITE_ERROR);
        assertThat(result.getErrors()).singleElement()
                .extracting(SearchWriteError::category)
                .isEqualTo(SearchWriteError.Category.OTHER);
    }

    @Test
    public void foreignKeyViolationOnDeleteIsWriteError() {
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(
                sqlException(PostgresSearchWriteResults.SQLSTATE_FK_VIOLATION), true);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.WRITE_ERROR);
    }

    @Test
    public void otherSqlStateIsWriteError() {
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(sqlException("42601"), false);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.WRITE_ERROR);
    }

    @Test
    public void nonSqlFailureIsUnexpectedUnacknowledgedError() {
        final RuntimeException failure = new IllegalStateException("pool acquire timed out");
        final SearchWriteResult result = PostgresSearchWriteResults.fromThrowable(failure, false);
        assertThat(result.classify()).isEqualTo(SearchWriteResultStatus.UNACKNOWLEDGED);
        assertThat(result.getUnexpectedError()).isSameAs(failure);
    }

    @Test
    public void sqlStateIsExtractedFromDeeperInTheCauseChain() {
        final Throwable wrapped = new RuntimeException("wrapper",
                new IllegalStateException("mid", sqlException(PostgresSearchWriteResults.SQLSTATE_FK_VIOLATION)));
        assertThat(PostgresSearchWriteResults.extractSqlState(wrapped))
                .isEqualTo(PostgresSearchWriteResults.SQLSTATE_FK_VIOLATION);
    }

    @Test
    public void extractSqlStateReturnsNullForNonSqlFailure() {
        assertThat(PostgresSearchWriteResults.extractSqlState(new RuntimeException("boom"))).isNull();
    }

    @Test
    public void upsertSqlIsUnconditionalWithNoRevisionPredicate() {
        // Round-2 Critical guard at the flow level: the doc-row upsert MUST NOT carry any revision predicate, or a
        // same-revision policy fan-out write (newer auth data) would 0-row and never land.
        final String sql = PostgresSearchUpdaterFlow.UPSERT_DOC_SQL;
        assertThat(sql).contains("ON CONFLICT (thing_id) DO UPDATE");
        assertThat(sql).doesNotContain("WHERE");
        assertThat(sql).doesNotContain("revision <");
        assertThat(sql).doesNotContain("EXCLUDED.revision >");
    }

    @Test
    public void antiJoinSqlBindsOneArrayPerColumnViaUnnest() {
        final String sql = PostgresSearchUpdaterFlow.ANTIJOIN_FLAT_SQL;
        assertThat(sql).contains("unnest($1::text[], $2::text[], $3::text[], $4::int[], $5::smallint[], "
                + "$6::boolean[], $7::numeric[], $8::text[])");
        assertThat(sql).contains("IS NOT DISTINCT FROM");
    }

    private static R2dbcException sqlException(final String sqlState) {
        return new R2dbcException("simulated", sqlState) {};
    }

}
