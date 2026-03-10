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

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoCollection;

/**
 * Factory for building Pekko Streams sources for encryption migration.
 * <p>
 * Provides static methods to construct MongoDB query streams with appropriate filters,
 * sorting, throttling, and resume capabilities for snapshot and journal migrations.
 */
public final class MigrationStreamFactory {

    // MongoDB field names for pekko-persistence-mongodb
    private static final String SNAPSHOT_SERIALIZED_FIELD = "s2";
    private static final String JOURNAL_EVENTS_FIELD = "events";
    private static final String JOURNAL_PAYLOAD_FIELD = "p";
    private static final String ID_FIELD = "_id";

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
     * The source queries the snapshot collection with filters for:
     * <ul>
     *   <li>Resume support: Skip documents already processed (based on lastProcessedSnapshotId)</li>
     *   <li>Encryptable fields: Only fetch documents containing fields that can be encrypted</li>
     * </ul>
     * Documents are sorted by _id (ascending) and fetched with the specified batch size.
     *
     * @param snapshotCollection the MongoDB snapshot collection
     * @param progress the current migration progress (for resume support)
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param batchSize the MongoDB fetch batch size
     * @param maxDocumentsPerMinute throttling limit (0 = disabled)
     * @return a Pekko Streams source of snapshot documents
     */
    public static Source<Document, NotUsed> buildSnapshotStream(
            final MongoCollection<Document> snapshotCollection,
            final MigrationProgress progress,
            final List<String> pointers,
            final int batchSize,
            final int maxDocumentsPerMinute) {

        final Bson resumeFilter = buildResumeFilter(progress.lastProcessedSnapshotId());
        final Bson encryptableFieldsFilter = buildEncryptableFieldsFilter(
                SNAPSHOT_SERIALIZED_FIELD, SNAPSHOT_ENTITY_TYPE_PREFIX, pointers);
        final Bson filter = Filters.and(resumeFilter, encryptableFieldsFilter);

        final Source<Document, NotUsed> source = Source.fromPublisher(
                snapshotCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize));

        return applyThrottling(source, maxDocumentsPerMinute);
    }

    /**
     * Builds a Pekko Streams source for journal migration.
     * <p>
     * The source queries the journal collection with filters for:
     * <ul>
     *   <li>Resume support: Skip documents already processed (based on lastProcessedJournalId)</li>
     *   <li>Encryptable fields: Only fetch documents with event payloads containing encryptable fields</li>
     * </ul>
     * Documents are sorted by _id (ascending) and fetched with the specified batch size.
     *
     * @param journalCollection the MongoDB journal collection
     * @param progress the current migration progress (for resume support)
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param batchSize the MongoDB fetch batch size
     * @param maxDocumentsPerMinute throttling limit (0 = disabled)
     * @return a Pekko Streams source of journal documents
     */
    public static Source<Document, NotUsed> buildJournalStream(
            final MongoCollection<Document> journalCollection,
            final MigrationProgress progress,
            final List<String> pointers,
            final int batchSize,
            final int maxDocumentsPerMinute) {

        final Bson resumeFilter = buildResumeFilter(progress.lastProcessedJournalId());
        final Bson encryptableFieldsFilter = buildEncryptableFieldsFilter(
                JOURNAL_EVENTS_FIELD + "." + JOURNAL_PAYLOAD_FIELD,
                JOURNAL_ENTITY_TYPE_PREFIX, pointers);
        final Bson filter = Filters.and(resumeFilter, encryptableFieldsFilter);

        final Source<Document, NotUsed> source = Source.fromPublisher(
                journalCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize));

        return applyThrottling(source, maxDocumentsPerMinute);
    }

    /**
     * Builds a MongoDB resume filter to skip already-processed documents.
     * <p>
     * Handles both ObjectId and string-based _id formats. If the lastProcessedId is not
     * a valid ObjectId, falls back to string comparison.
     *
     * @param lastProcessedId the ID of the last processed document, or null to start from the beginning
     * @return a Bson filter for resume support
     */
    private static Bson buildResumeFilter(@Nullable final String lastProcessedId) {
        if (lastProcessedId == null) {
            return Filters.empty();
        }

        try {
            return Filters.gt(ID_FIELD, new ObjectId(lastProcessedId));
        } catch (final IllegalArgumentException e) {
            // Non-ObjectId _id format — use string comparison as fallback
            return Filters.gt(ID_FIELD, lastProcessedId);
        }
    }

    /**
     * Builds a MongoDB filter that matches only documents containing at least one of the
     * encryptable fields. This avoids fetching documents (e.g. empty events) that have
     * no fields to encrypt/decrypt.
     *
     * @param documentPrefix the BSON path prefix to the document (e.g. "s2" for snapshots,
     *        "events.p" for journal payloads)
     * @param entityTypePrefix the entity type prefix applied to pointers (e.g. "connection" for journal)
     * @param pointers the configured JSON pointers to encrypt
     * @return a Bson filter requiring at least one encryptable field to exist
     */
    static Bson buildEncryptableFieldsFilter(final String documentPrefix,
            final String entityTypePrefix, final List<String> pointers) {
        final String prefix = entityTypePrefix.isEmpty()
                ? documentPrefix
                : documentPrefix + "." + entityTypePrefix;
        final List<Bson> existsFilters = pointers.stream()
                .map(pointer -> pointer.replace("/", "."))
                .map(dotPath -> Filters.exists(prefix + dotPath))
                .collect(Collectors.toList());
        return Filters.or(existsFilters);
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
     * @return throttled source if enabled, original source otherwise
     */
    static Source<Document, NotUsed> applyThrottling(final Source<Document, NotUsed> source,
            final int maxDocsPerMinute) {
        if (maxDocsPerMinute <= 0) {
            return source;
        }

        return source.throttle(maxDocsPerMinute, java.time.Duration.ofMinutes(1));
    }
}
