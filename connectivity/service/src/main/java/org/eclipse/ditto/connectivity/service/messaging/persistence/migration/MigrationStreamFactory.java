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

import java.util.List;
import java.util.stream.Collectors;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.connectivity.service.messaging.persistence.JsonFieldsEncryptor;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;

/**
 * Factory for building Pekko Streams sources for encryption migration.
 * <p>
 * Provides static methods that wire the persistence backend's {@link PersistedPayloadStore} streams with the
 * encryptable-field filter, resume cursor and throttling for snapshot and journal migrations. The backend decides how
 * the filter and the cursor are evaluated; this class only names the fields the payloads must hold.
 */
public final class MigrationStreamFactory {

    // Entity type prefix used for journal event encryption
    private static final String JOURNAL_ENTITY_TYPE_PREFIX = "connection";
    // Snapshot encryption uses empty prefix
    private static final String SNAPSHOT_ENTITY_TYPE_PREFIX = "";

    private MigrationStreamFactory() {
        // Utility class - no instantiation
    }

    /**
     * Builds a Pekko Streams source for snapshot migration.
     * <p>
     * The source streams the snapshots from the store with:
     * <ul>
     *   <li>Resume support: Skip snapshots already processed (based on lastProcessedSnapshotId)</li>
     *   <li>Encryptable fields: Only fetch snapshots containing fields that can be encrypted</li>
     * </ul>
     * Snapshots arrive in a stable, backend-defined order and are fetched with the specified batch size; the saved
     * cursor continues behind the last processed entry.
     *
     * @param store the persistence backend's payload store
     * @param progress the current migration progress (for resume support)
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param batchSize the backend fetch batch size
     * @param maxDocumentsPerMinute throttling limit (0 = disabled)
     * @return a Pekko Streams source of persisted snapshots
     */
    public static Source<PersistedSnapshot, NotUsed> buildSnapshotStream(
            final PersistedPayloadStore store,
            final MigrationProgress progress,
            final List<String> pointers,
            final int batchSize,
            final int maxDocumentsPerMinute) {

        final Source<PersistedSnapshot, NotUsed> source = store.streamSnapshots(progress.lastProcessedSnapshotId(),
                requiredFieldPointers(SNAPSHOT_ENTITY_TYPE_PREFIX, pointers), batchSize);

        return applyThrottling(source, maxDocumentsPerMinute);
    }

    /**
     * Builds a Pekko Streams source for journal migration.
     * <p>
     * The source streams the journal entries from the store with:
     * <ul>
     *   <li>Resume support: Skip entries already processed (based on lastProcessedJournalId)</li>
     *   <li>Encryptable fields: Only fetch entries with event payloads containing encryptable fields</li>
     * </ul>
     * Entries arrive in a stable, backend-defined order and are fetched with the specified batch size; the saved
     * cursor continues behind the last processed entry.
     *
     * @param store the persistence backend's payload store
     * @param progress the current migration progress (for resume support)
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param batchSize the backend fetch batch size
     * @param maxDocumentsPerMinute throttling limit (0 = disabled)
     * @return a Pekko Streams source of persisted journal entries
     */
    public static Source<PersistedJournalEntry, NotUsed> buildJournalStream(
            final PersistedPayloadStore store,
            final MigrationProgress progress,
            final List<String> pointers,
            final int batchSize,
            final int maxDocumentsPerMinute) {

        final Source<PersistedJournalEntry, NotUsed> source = store.streamJournalEntries(
                progress.lastProcessedJournalId(),
                requiredFieldPointers(JOURNAL_ENTITY_TYPE_PREFIX, pointers), batchSize);

        return applyThrottling(source, maxDocumentsPerMinute);
    }

    /**
     * Builds the full JSON pointers of the encryptable fields as the payload JSON has them: the configured pointers
     * prefixed with the entity type prefix the same way {@link JsonFieldsEncryptor} prefixes them when encrypting
     * (e.g. {@code /uri} stays {@code /uri} for snapshots and becomes {@code /connection/uri} for journal events).
     *
     * @param entityTypePrefix the entity type prefix applied to pointers (e.g. "connection" for journal)
     * @param pointers the configured JSON pointers to encrypt
     * @return the full pointers, at least one of which a payload must hold to be streamed
     */
    static List<String> requiredFieldPointers(final String entityTypePrefix, final List<String> pointers) {
        return JsonFieldsEncryptor.prefixPointers(entityTypePrefix, pointers).collect(Collectors.toList());
    }

    /**
     * Applies throttling to the source stream if throttling is enabled (maxDocsPerMinute > 0).
     * <p>
     * Throttling is implemented using Pekko Streams throttle operator with a token-bucket
     * algorithm, allowing bursts up to maxDocsPerMinute as long as the average rate stays
     * within the limit.
     *
     * @param source the source stream
     * @param maxDocsPerMinute maximum documents per minute, 0 means no throttling
     * @param <T> the element type
     * @return throttled source if enabled, original source otherwise
     */
    static <T> Source<T, NotUsed> applyThrottling(final Source<T, NotUsed> source,
            final int maxDocsPerMinute) {
        if (maxDocsPerMinute <= 0) {
            return source;
        }

        return source.throttle(maxDocsPerMinute, java.time.Duration.ofMinutes(1));
    }
}
