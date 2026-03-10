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
package org.eclipse.ditto.connectivity.service.messaging.persistence.migration;

import javax.annotation.Nullable;

import org.bson.Document;

import com.mongodb.client.model.WriteModel;

/**
 * Result of processing a MongoDB document during encryption migration.
 * <p>
 * Encapsulates the outcome (processed, skipped, or failed) and an optional write model
 * for batch updates.
 *
 * @param outcome the processing outcome
 * @param writeModel the MongoDB write model for batch updates, or {@code null} if dry-run or skipped
 */
public record DocumentProcessingResult(
        DocumentOutcome outcome,
        @Nullable WriteModel<Document> writeModel
) {

    /**
     * Creates a result for a successfully processed document.
     *
     * @param writeModel the MongoDB write model, or {@code null} if dry-run
     * @return a processed result
     */
    public static DocumentProcessingResult processed(@Nullable final WriteModel<Document> writeModel) {
        return new DocumentProcessingResult(DocumentOutcome.PROCESSED, writeModel);
    }

    /**
     * Creates a result for a skipped document.
     * <p>
     * Documents are skipped when they are already in the desired state (e.g., already encrypted
     * with the new key, or already plaintext when disabling encryption).
     *
     * @return a skipped result
     */
    public static DocumentProcessingResult skipped() {
        return new DocumentProcessingResult(DocumentOutcome.SKIPPED, null);
    }

    /**
     * Creates a result for a failed document.
     * <p>
     * Documents fail when they cannot be processed due to errors (e.g., corrupted data,
     * decryption failures, unexpected structure).
     *
     * @return a failed result
     */
    public static DocumentProcessingResult failed() {
        return new DocumentProcessingResult(DocumentOutcome.FAILED, null);
    }

    /**
     * Possible outcomes for document processing.
     */
    public enum DocumentOutcome {
        /**
         * Document was successfully processed and re-encrypted.
         */
        PROCESSED,

        /**
         * Document was skipped because it's already in the desired state.
         */
        SKIPPED,

        /**
         * Document processing failed due to an error.
         */
        FAILED
    }
}
