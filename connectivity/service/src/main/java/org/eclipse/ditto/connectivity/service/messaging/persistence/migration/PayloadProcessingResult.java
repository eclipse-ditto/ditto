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

/**
 * Result of processing one persisted entry (a snapshot or a journal entry) during encryption migration.
 * <p>
 * Encapsulates the outcome (processed, skipped, or failed) and, for a processed entry outside a dry run, the entry
 * with its re-encrypted payload(s), ready to be written back through the persistence backend's payload store.
 *
 * @param outcome the processing outcome
 * @param rewritten the entry with the re-encrypted payload(s), or {@code null} if dry-run, skipped or failed
 * @param <E> the entry type: a {@code PersistedSnapshot} or a {@code PersistedJournalEntry}
 */
public record PayloadProcessingResult<E>(
        Outcome outcome,
        @Nullable E rewritten
) {

    /**
     * Creates a result for a successfully processed entry.
     *
     * @param rewritten the entry with the re-encrypted payload(s), or {@code null} if dry-run
     * @param <E> the entry type
     * @return a processed result
     */
    public static <E> PayloadProcessingResult<E> processed(@Nullable final E rewritten) {
        return new PayloadProcessingResult<>(Outcome.PROCESSED, rewritten);
    }

    /**
     * Creates a result for a skipped entry.
     * <p>
     * Entries are skipped when they are already in the desired state (e.g., already encrypted with the new key, or
     * already plaintext when disabling encryption).
     *
     * @param <E> the entry type
     * @return a skipped result
     */
    public static <E> PayloadProcessingResult<E> skipped() {
        return new PayloadProcessingResult<>(Outcome.SKIPPED, null);
    }

    /**
     * Creates a result for a failed entry.
     * <p>
     * Entries fail when they cannot be processed due to errors (e.g., corrupted data, decryption failures, unexpected
     * structure).
     *
     * @param <E> the entry type
     * @return a failed result
     */
    public static <E> PayloadProcessingResult<E> failed() {
        return new PayloadProcessingResult<>(Outcome.FAILED, null);
    }

    /**
     * Possible outcomes for processing an entry.
     */
    public enum Outcome {
        /**
         * The entry was successfully processed and re-encrypted.
         */
        PROCESSED,

        /**
         * The entry was skipped because it is already in the desired state.
         */
        SKIPPED,

        /**
         * Processing the entry failed due to an error.
         */
        FAILED
    }
}
