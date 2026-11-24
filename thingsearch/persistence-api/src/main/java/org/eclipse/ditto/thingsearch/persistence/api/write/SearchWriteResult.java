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
package org.eclipse.ditto.thingsearch.persistence.api.write;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Backend-neutral result of applying a batch of search write models, modelled on what the Mongo bulk-write
 * acknowledgement flow consumes (acknowledged flag, matched/upserted counts, per-element errors). The
 * {@link #classify()} method reproduces the Mongo flow's decision rules in a backend-agnostic way, including
 * its decision precedence (CONSISTENCY_ERROR &gt; INCORRECT_PATCH &gt; WRITE_ERROR &gt; OK) as implemented by
 * {@code BulkWriteResultAckFlow.checkForConsistencyError}.
 * <p>
 * A result may also carry diagnostic values (a correlation ID and the inserted and deleted counts), set through
 * {@link #withDiagnostics(String, int, int)}. They exist only for logging and play no part in {@link #classify()}.
 * A backend that does not report a count leaves it at 0.
 * </p>
 *
 * @since 4.0.0
 */
@Immutable
public final class SearchWriteResult {

    private final boolean acknowledged;
    private final int writeModelCount;
    private final int nonDeleteWriteModelCount;
    private final int matchedCount;
    private final int modifiedCount;
    private final int upsertedCount;
    private final List<SearchWriteError> errors;
    @Nullable private final Throwable unexpectedError;
    @Nullable private final String correlationId;
    private final int insertedCount;
    private final int deletedCount;

    private SearchWriteResult(final boolean acknowledged, final int writeModelCount,
            final int nonDeleteWriteModelCount, final int matchedCount, final int modifiedCount,
            final int upsertedCount, final List<SearchWriteError> errors,
            @Nullable final Throwable unexpectedError, @Nullable final String correlationId,
            final int insertedCount, final int deletedCount) {
        this.acknowledged = acknowledged;
        this.writeModelCount = writeModelCount;
        this.nonDeleteWriteModelCount = nonDeleteWriteModelCount;
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
        this.upsertedCount = upsertedCount;
        this.errors = List.copyOf(errors);
        this.unexpectedError = unexpectedError;
        this.correlationId = correlationId;
        this.insertedCount = insertedCount;
        this.deletedCount = deletedCount;
    }

    /**
     * Create an acknowledged write result.
     * <p>
     * The Mongo original ({@code BulkWriteResultAckFlow}) uses two DIFFERENT counts of the submitted batch
     * depending on which check it feeds: {@code areAllIndexesWithinBounds} bounds-checks reported error
     * indices against the FULL batch size ({@code getWriteModels().size()}, delete models included, since a
     * delete model can legitimately occupy any index in the batch); {@code areUpdatesMissing} compares
     * {@code matchedCount + upsertedCount} against the batch size with delete models EXCLUDED, because a
     * Mongo delete reports through {@code deletedCount}, never through {@code matchedCount}/{@code
     * upsertedCount} &mdash; so counting deletes in that comparison would make every batch containing a
     * delete look like an incorrect patch. This type keeps both counts as separate parameters for exactly
     * that reason.
     *
     * @param writeModelCount the total number of write models in the submitted batch, delete models included;
     * used only to bounds-check reported error indices.
     * @param nonDeleteWriteModelCount the number of write models in the submitted batch EXCLUDING delete models (i.e.
     * the count a backend's write-result adapter must obtain by filtering out delete/removal write models before
     * calling this factory, mirroring {@code BulkWriteResultAckFlow.areUpdatesMissing}); used only to detect missing
     * patch updates.
     * @param matchedCount the number of documents matched.
     * @param modifiedCount the number of documents modified.
     * @param upsertedCount the number of documents upserted.
     * @param errors the per-element errors (may be empty).
     * @return the write result.
     */
    public static SearchWriteResult acknowledged(final int writeModelCount, final int nonDeleteWriteModelCount,
            final int matchedCount, final int modifiedCount, final int upsertedCount,
            final List<SearchWriteError> errors) {
        return new SearchWriteResult(true, writeModelCount, nonDeleteWriteModelCount, matchedCount, modifiedCount,
                upsertedCount, errors, null, null, 0, 0);
    }

    /**
     * Create an unacknowledged write result.
     *
     * @param writeModelCount the number of write models in the submitted batch.
     * @return the write result.
     */
    public static SearchWriteResult unacknowledged(final int writeModelCount) {
        return new SearchWriteResult(false, writeModelCount, writeModelCount, 0, 0, 0, List.of(), null, null, 0, 0);
    }

    /**
     * Create a result for an unexpected error that aborted the whole batch.
     *
     * @param writeModelCount the number of write models in the submitted batch.
     * @param unexpectedError the error.
     * @return the write result.
     */
    public static SearchWriteResult unexpectedError(final int writeModelCount, final Throwable unexpectedError) {
        return new SearchWriteResult(false, writeModelCount, writeModelCount, 0, 0, 0, List.of(), unexpectedError, null,
                0, 0);
    }

    /**
     * Return a copy of this result that carries the given diagnostic values. They are used only for logging.
     *
     * @param correlationId the correlation ID of the write, or {@code null} if there is none.
     * @param insertedCount the number of documents inserted, or 0 if the backend does not report it.
     * @param deletedCount the number of documents deleted, or 0 if the backend does not report it.
     * @return the copy.
     */
    public SearchWriteResult withDiagnostics(@Nullable final String correlationId, final int insertedCount,
            final int deletedCount) {
        return new SearchWriteResult(acknowledged, writeModelCount, nonDeleteWriteModelCount, matchedCount,
                modifiedCount, upsertedCount, errors, unexpectedError, correlationId, insertedCount, deletedCount);
    }

    /**
     * @return whether the backend acknowledged the write.
     */
    public boolean isAcknowledged() {
        return acknowledged;
    }

    /**
     * @return the total number of write models in the submitted batch, delete models included.
     */
    public int getWriteModelCount() {
        return writeModelCount;
    }

    /**
     * @return the number of write models in the submitted batch EXCLUDING delete models; mirrors
     * {@code BulkWriteResultAckFlow.areUpdatesMissing}'s non-delete count.
     */
    public int getNonDeleteWriteModelCount() {
        return nonDeleteWriteModelCount;
    }

    /**
     * @return the number of documents matched.
     */
    public int getMatchedCount() {
        return matchedCount;
    }

    /**
     * @return the number of documents modified.
     */
    public int getModifiedCount() {
        return modifiedCount;
    }

    /**
     * @return the number of documents upserted.
     */
    public int getUpsertedCount() {
        return upsertedCount;
    }

    /**
     * @return the per-element errors.
     */
    public List<SearchWriteError> getErrors() {
        return errors;
    }

    /**
     * @return the unexpected error that aborted the batch, if any.
     */
    @Nullable
    public Throwable getUnexpectedError() {
        return unexpectedError;
    }

    /**
     * @return the correlation ID of the write, if any; used only for logging.
     */
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * @return the number of documents inserted, or 0 if the backend does not report it; used only for logging.
     */
    public int getInsertedCount() {
        return insertedCount;
    }

    /**
     * @return the number of documents deleted, or 0 if the backend does not report it; used only for logging.
     */
    public int getDeletedCount() {
        return deletedCount;
    }

    /**
     * Classify this result into the neutral outcome vocabulary, reproducing the Mongo bulk-write
     * acknowledgement flow's rules AND its decision precedence
     * ({@code BulkWriteResultAckFlow.checkForConsistencyError}): unacknowledged wins first; then error
     * indices outside the full batch are a consistency error; then missing matched+upserted counts (against
     * the non-delete write-model count) indicate an incorrect patch &mdash; checked BEFORE write errors, so a
     * batch exhibiting both symptoms classifies as an incorrect patch, not a write error, exactly as the
     * Mongo original does; then any non duplicate-key error is a write error; otherwise the batch is OK
     * (duplicate-key errors count as success).
     *
     * @return the classified outcome.
     */
    public SearchWriteResultStatus classify() {
        if (!acknowledged) {
            return SearchWriteResultStatus.UNACKNOWLEDGED;
        }
        final boolean anyIndexOutOfBounds = errors.stream()
                .anyMatch(error -> error.index() < 0 || error.index() >= writeModelCount);
        if (anyIndexOutOfBounds) {
            return SearchWriteResultStatus.CONSISTENCY_ERROR;
        }
        if (matchedCount + upsertedCount < nonDeleteWriteModelCount) {
            return SearchWriteResultStatus.INCORRECT_PATCH;
        }
        final boolean anyRealError = errors.stream()
                .anyMatch(error -> error.category() != SearchWriteError.Category.DUPLICATE_KEY);
        if (anyRealError) {
            return SearchWriteResultStatus.WRITE_ERROR;
        }
        return SearchWriteResultStatus.OK;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SearchWriteResult that = (SearchWriteResult) o;
        return acknowledged == that.acknowledged &&
                writeModelCount == that.writeModelCount &&
                nonDeleteWriteModelCount == that.nonDeleteWriteModelCount &&
                matchedCount == that.matchedCount &&
                modifiedCount == that.modifiedCount &&
                upsertedCount == that.upsertedCount &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(unexpectedError, that.unexpectedError) &&
                Objects.equals(correlationId, that.correlationId) &&
                insertedCount == that.insertedCount &&
                deletedCount == that.deletedCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(acknowledged, writeModelCount, nonDeleteWriteModelCount, matchedCount, modifiedCount,
                upsertedCount, errors, unexpectedError, correlationId, insertedCount, deletedCount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "acknowledged=" + acknowledged +
                ", writeModelCount=" + writeModelCount +
                ", nonDeleteWriteModelCount=" + nonDeleteWriteModelCount +
                ", matchedCount=" + matchedCount +
                ", modifiedCount=" + modifiedCount +
                ", upsertedCount=" + upsertedCount +
                ", errors=" + errors +
                ", unexpectedError=" + unexpectedError +
                ", correlationId=" + correlationId +
                ", insertedCount=" + insertedCount +
                ", deletedCount=" + deletedCount +
                "]";
    }
}
