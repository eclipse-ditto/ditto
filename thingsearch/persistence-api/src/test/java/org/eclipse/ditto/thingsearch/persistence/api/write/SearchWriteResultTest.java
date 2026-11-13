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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/**
 * Tests {@link SearchWriteResult#classify()} against the decision precedence of the Mongo original it must
 * preserve, {@code BulkWriteResultAckFlow.checkForConsistencyError}: CONSISTENCY_ERROR &gt; INCORRECT_PATCH &gt;
 * WRITE_ERROR &gt; OK (with UNACKNOWLEDGED short-circuiting all of them), and the non-delete write-model count
 * used by {@code BulkWriteResultAckFlow.areUpdatesMissing}.
 */
public final class SearchWriteResultTest {

    @Test
    public void unacknowledgedWinsOverEverythingElse() {
        final SearchWriteResult result = SearchWriteResult.unacknowledged(5);

        assertEquals(SearchWriteResultStatus.UNACKNOWLEDGED, result.classify());
    }

    @Test
    public void consistencyErrorDominatesIncorrectPatchAndWriteError() {
        // index 5 is out of bounds for a 5-element batch (valid indices 0..4); the batch ALSO exhibits both
        // an incorrect-patch symptom (matched+upserted < non-delete count) and a write-error symptom (a
        // non duplicate-key error) so this proves CONSISTENCY_ERROR is checked and wins first.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 5, 1, 1, 0,
                List.of(new SearchWriteError(5, SearchWriteError.Category.OTHER, "boom")));

        assertEquals(SearchWriteResultStatus.CONSISTENCY_ERROR, result.classify());
    }

    @Test
    public void bothIncorrectPatchAndWriteErrorSymptomsClassifyAsIncorrectPatch() {
        // matchedCount + upsertedCount (1 + 0 = 1) < nonDeleteWriteModelCount (5): incorrect-patch symptom.
        // AND a non duplicate-key error at a valid index: write-error symptom.
        // The ORIGINAL (BulkWriteResultAckFlow.checkForConsistencyError) checks areUpdatesMissing BEFORE
        // checking for non-empty bulk write errors, so INCORRECT_PATCH must win, not WRITE_ERROR.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 5, 1, 1, 0,
                List.of(new SearchWriteError(2, SearchWriteError.Category.OTHER, "boom")));

        assertEquals(SearchWriteResultStatus.INCORRECT_PATCH, result.classify());
    }

    @Test
    public void writeErrorAloneWithNoMissingUpdatesClassifiesAsWriteError() {
        // matched+upserted (5) >= nonDeleteWriteModelCount (5): no incorrect-patch symptom.
        // A non duplicate-key error remains: write-error symptom, and nothing dominates it.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 5, 4, 4, 1,
                List.of(new SearchWriteError(2, SearchWriteError.Category.OTHER, "boom")));

        assertEquals(SearchWriteResultStatus.WRITE_ERROR, result.classify());
    }

    @Test
    public void duplicateKeyErrorsAloneCountAsSuccess() {
        // matched+upserted (5) >= nonDeleteWriteModelCount (5): no incorrect-patch symptom.
        // The only error is a duplicate-key error, which the acknowledgement flow treats as success.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 5, 4, 4, 1,
                List.of(new SearchWriteError(3, SearchWriteError.Category.DUPLICATE_KEY, "dup")));

        assertEquals(SearchWriteResultStatus.OK, result.classify());
    }

    @Test
    public void plainSuccessWithNoErrorsClassifiesAsOk() {
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 5, 3, 2, 2, List.of());

        assertEquals(SearchWriteResultStatus.OK, result.classify());
    }

    @Test
    public void deleteModelsAreExcludedFromTheIncorrectPatchCheck() {
        // Batch of 5 write models submitted, but 2 of them are delete models (mirrors
        // BulkWriteResultAckFlow.areUpdatesMissing filtering out ThingDeleteModel instances): a Mongo delete
        // reports through deletedCount, never matchedCount/upsertedCount, so only the remaining 3 non-delete
        // write models are expected to show up in matched+upserted. Full batch size (5) is still used for
        // the index-bounds check.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 3, 2, 2, 1, List.of());

        // matched(2) + upserted(1) = 3 >= nonDeleteWriteModelCount(3): no incorrect patch, despite
        // 3 < writeModelCount(5) which would have wrongly flagged INCORRECT_PATCH before this fix.
        assertEquals(SearchWriteResultStatus.OK, result.classify());
    }

    @Test
    public void deleteExclusionStillDetectsAGenuineIncorrectPatch() {
        // Same 5-element batch with 2 deletes (nonDeleteWriteModelCount = 3), but this time only 2 of the
        // 3 non-delete models were matched/upserted: a genuine incorrect patch.
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 3, 1, 1, 1, List.of());

        assertEquals(SearchWriteResultStatus.INCORRECT_PATCH, result.classify());
    }

    @Test
    public void unexpectedErrorFactoryClassifiesAsUnacknowledged() {
        final SearchWriteResult result = SearchWriteResult.unexpectedError(5, new IllegalStateException("boom"));

        assertEquals(SearchWriteResultStatus.UNACKNOWLEDGED, result.classify());
    }
}
