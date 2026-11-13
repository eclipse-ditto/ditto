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
package org.eclipse.ditto.internal.utils.persistence.mongo.payload;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * The MongoDB {@link PersistedPayloadStore}: streams and rewrites the documents of the {@code <entityType>_snaps} and
 * {@code <entityType>_journal} collections the {@code pekko-persistence-mongodb} plugin writes, and keeps the progress
 * record as the single document {@code _id: "current"} of the {@code <entityType>_encryption_migration} collection —
 * the collection keeps the historical name the connectivity encryption migration gave it before this store existed.
 * <p>
 * The statements are the ones that migration ran directly against the driver before: a {@code find} filtered by
 * {@code $or} of {@code $exists} on the dotted paths of the required fields below the payload ({@code s2.} for
 * snapshots, {@code events.p.} for journal events), combined with {@code _id > afterCursor} (the cursor parsed as an
 * {@link ObjectId} first, as a string otherwise), sorted by {@code _id} ascending and fetched with the given batch
 * size; an unordered bulk write of {@code ReplaceOne} by {@code _id} for the rewrites; and a {@code replaceOne} with
 * upsert, a {@code find(...).first()} and a {@code deleteOne} for the progress record, whose document carries the JSON
 * record's fields in order after {@code _id} with strings as strings, integral numbers as int64 and JSON nulls as BSON
 * nulls.
 * <p>
 * On MongoDB the stream order is {@code _id} ascending and the cursor of an entry is its {@code _id} as a string, so a
 * resumed stream continues right behind the document the cursor names. The rewrites are written unordered: one
 * document that fails to be replaced does not stop the others, and the count the stage completes with is the number
 * of documents the server actually replaced.
 * <p>
 * One store owns one {@link MongoClientWrapper}, built from the {@code ditto.mongodb} block of the actor system's
 * config when the store is created and closed by {@link #close()}.
 *
 * @since 4.0.0
 */
public final class MongoPersistedPayloadStore implements PersistedPayloadStore {

    /** Collection suffixes per entity type: the pekko-persistence-mongodb plugin's and the historical progress one. */
    static final String SNAPSHOT_COLLECTION_SUFFIX = "_snaps";
    static final String JOURNAL_COLLECTION_SUFFIX = "_journal";
    static final String PROGRESS_COLLECTION_SUFFIX = "_encryption_migration";

    /** The dotted prefixes below which the required fields live in a snapshot and in a journal document. */
    static final String SNAPSHOT_PAYLOAD_PREFIX = MongoPersistedSnapshot.PAYLOAD_FIELD;
    static final String JOURNAL_PAYLOAD_PREFIX =
            MongoPersistedJournalEntry.EVENTS_FIELD + "." + MongoPersistedJournalEntry.PAYLOAD_FIELD;

    /** The {@code _id} of the one progress document. */
    static final String PROGRESS_ID = "current";

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoPersistedPayloadStore.class);
    private static final String ID_FIELD = PayloadDocuments.ID_FIELD;

    private final MongoClientWrapper mongoClient;
    private final MongoCollection<Document> snapshotCollection;
    private final MongoCollection<Document> journalCollection;
    private final MongoCollection<Document> progressCollection;
    private final Materializer materializer;

    private MongoPersistedPayloadStore(final MongoClientWrapper mongoClient, final String entityType,
            final Materializer materializer) {

        this.mongoClient = mongoClient;
        final MongoDatabase db = mongoClient.getDefaultDatabase();
        snapshotCollection = db.getCollection(entityType + SNAPSHOT_COLLECTION_SUFFIX);
        journalCollection = db.getCollection(entityType + JOURNAL_COLLECTION_SUFFIX);
        progressCollection = db.getCollection(entityType + PROGRESS_COLLECTION_SUFFIX);
        this.materializer = materializer;
    }

    /**
     * Creates a store over the collections of the given entity type, with its own {@link MongoClientWrapper} built from
     * the {@code ditto.mongodb} block of the actor system's config — the same block a service's own
     * {@link MongoDbConfig} is read from.
     *
     * @param entityType the entity type, e.g. {@code "connection"}.
     * @param actorSystem the actor system providing the config and the materializer for the single operations.
     * @return the store; the caller closes it.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MongoPersistedPayloadStore newInstance(final String entityType, final ActorSystem actorSystem) {
        checkNotNull(entityType, "entityType");
        checkNotNull(actorSystem, "actorSystem");
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        return new MongoPersistedPayloadStore(mongoClient, entityType,
                SystemMaterializer.get(actorSystem).materializer());
    }

    @Override
    public Source<PersistedSnapshot, NotUsed> streamSnapshots(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {

        final Bson filter = Filters.and(resumeFilter(afterCursor),
                requiredFieldsFilter(SNAPSHOT_PAYLOAD_PREFIX, checkNotNull(requiredFieldPointers,
                        "requiredFieldPointers")));
        return Source.fromPublisher(snapshotCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize))
                .<PersistedSnapshot>map(MongoPersistedSnapshot::of);
    }

    @Override
    public Source<PersistedJournalEntry, NotUsed> streamJournalEntries(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {

        final Bson filter = Filters.and(resumeFilter(afterCursor),
                requiredFieldsFilter(JOURNAL_PAYLOAD_PREFIX, checkNotNull(requiredFieldPointers,
                        "requiredFieldPointers")));
        return Source.fromPublisher(journalCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize))
                .<PersistedJournalEntry>map(MongoPersistedJournalEntry::of);
    }

    @Override
    public CompletionStage<Long> rewriteSnapshots(final List<PersistedSnapshot> snapshots) {
        checkNotNull(snapshots, "snapshots");
        final List<Document> documents = snapshots.stream()
                .map(snapshot -> ownEntry(snapshot, MongoPersistedSnapshot.class).document())
                .toList();
        return bulkReplace(snapshotCollection, documents);
    }

    @Override
    public CompletionStage<Long> rewriteJournalEntries(final List<PersistedJournalEntry> entries) {
        checkNotNull(entries, "entries");
        final List<Document> documents = entries.stream()
                .map(entry -> ownEntry(entry, MongoPersistedJournalEntry.class).document())
                .toList();
        return bulkReplace(journalCollection, documents);
    }

    @Override
    public CompletionStage<Optional<JsonObject>> loadProgress() {
        return Source.fromPublisher(progressCollection.find(Filters.eq(ID_FIELD, PROGRESS_ID)).first())
                .runWith(Sink.headOption(), materializer)
                .thenApply(optDoc -> optDoc.map(MongoPersistedPayloadStore::progressToJson));
    }

    @Override
    public CompletionStage<Void> saveProgress(final JsonObject progress) {
        checkNotNull(progress, "progress");
        try {
            final Document progressDoc = progressToDocument(progress);
            return Source.fromPublisher(progressCollection.replaceOne(
                            Filters.eq(ID_FIELD, PROGRESS_ID),
                            progressDoc,
                            new ReplaceOptions().upsert(true)))
                    .runWith(Sink.head(), materializer)
                    .thenApply(result -> {
                        LOGGER.info("Progress saved: modified={}, upserted={}",
                                result.getModifiedCount(), result.getUpsertedId() != null ? 1 : 0);
                        return (Void) null;
                    });
        } catch (final RuntimeException e) {
            // the SPI promises a failed stage for a record that cannot be saved (e.g. not flat, or carrying _id),
            // never a synchronous throw: the caller only handles the stage
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<Void> deleteProgress() {
        return Source.fromPublisher(progressCollection.deleteOne(Filters.eq(ID_FIELD, PROGRESS_ID)))
                .runWith(Sink.ignore(), materializer)
                .thenApply(done -> null);
    }

    @Override
    public void close() {
        mongoClient.close();
    }

    MongoCollection<Document> snapshotCollection() {
        return snapshotCollection;
    }

    MongoCollection<Document> journalCollection() {
        return journalCollection;
    }

    MongoCollection<Document> progressCollection() {
        return progressCollection;
    }

    /**
     * Builds the filter that matches only documents holding at least one of the required fields below the given
     * payload prefix: a pointer {@code /credentials/password} below {@code s2} becomes
     * {@code $exists: s2.credentials.password}.
     *
     * @param documentPrefix the dotted path to the payload inside the document ({@code s2} or {@code events.p}).
     * @param requiredFieldPointers the JSON pointers of the required fields, relative to the payload; not empty.
     * @return the {@code $or} of the {@code $exists} filters.
     * @throws IllegalArgumentException if {@code requiredFieldPointers} is empty (an empty {@code $or} is rejected by
     * the server anyway; failing here keeps the SPI contract and spares the round trip).
     */
    static Bson requiredFieldsFilter(final String documentPrefix, final List<String> requiredFieldPointers) {
        checkArgument(requiredFieldPointers, pointers -> !pointers.isEmpty(),
                () -> "The requiredFieldPointers must not be empty: at least one field must be required.");
        final List<Bson> existsFilters = requiredFieldPointers.stream()
                .map(pointer -> pointer.replace("/", "."))
                .map(dotPath -> Filters.exists(documentPrefix + dotPath))
                .collect(Collectors.toList());
        return Filters.or(existsFilters);
    }

    /**
     * Builds the filter that skips the documents up to and including the given cursor: {@code _id} greater than the
     * cursor parsed as an {@link ObjectId}, or than the cursor string if it is not one.
     *
     * @param afterCursor the cursor of the last processed document, or {@code null} to start from the beginning.
     * @return the filter.
     */
    static Bson resumeFilter(@Nullable final String afterCursor) {
        if (afterCursor == null) {
            return Filters.empty();
        }
        try {
            return Filters.gt(ID_FIELD, new ObjectId(afterCursor));
        } catch (final IllegalArgumentException e) {
            // Non-ObjectId _id format — use string comparison as fallback
            return Filters.gt(ID_FIELD, afterCursor);
        }
    }

    /**
     * Renders the progress JSON as the document to store: {@code _id: "current"} first, then the record's fields in
     * order with strings as strings, integral numbers as int64, other numbers as doubles, booleans as booleans and JSON
     * nulls as BSON nulls.
     *
     * @param progress the flat progress record.
     * @return the document.
     * @throws IllegalArgumentException if the record carries an {@code _id} field or a value that is not a scalar.
     */
    static Document progressToDocument(final JsonObject progress) {
        final Document document = new Document(ID_FIELD, PROGRESS_ID);
        for (final JsonField field : progress) {
            final String key = field.getKeyName();
            if (ID_FIELD.equals(key)) {
                throw new IllegalArgumentException("The progress record must not carry the field <" + ID_FIELD +
                        ">; the store owns it.");
            }
            document.append(key, toScalar(key, field.getValue()));
        }
        return document;
    }

    /**
     * Renders a stored progress document as the flat JSON record it was saved from, without the {@code _id}. Int64 and
     * int32 values are long JSON numbers, so a counter reads with {@code asLong()} either way; any value that is not a
     * string, number, boolean or null is rendered as its string form so that a hand-edited record still loads.
     *
     * @param document the stored progress document.
     * @return the progress record.
     */
    static JsonObject progressToJson(final Document document) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        document.forEach((key, value) -> {
            if (!ID_FIELD.equals(key)) {
                builder.set(key, toJsonValue(value));
            }
        });
        return builder.build();
    }

    @Nullable
    private static Object toScalar(final String key, final JsonValue value) {
        if (value.isNull()) {
            return null;
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNumber()) {
            // isLong() holds for every integral number (int, long, integral double): those are the int64 counters
            return value.isLong() ? (Object) value.asLong() : (Object) value.asDouble();
        } else if (value.isBoolean()) {
            return value.asBoolean();
        }
        throw new IllegalArgumentException("The progress record field <" + key + "> is not a scalar: " + value);
    }

    private static JsonValue toJsonValue(@Nullable final Object value) {
        if (value == null) {
            return JsonValue.nullLiteral();
        } else if (value instanceof String string) {
            return JsonValue.of(string);
        } else if (value instanceof Integer || value instanceof Long) {
            return JsonValue.of(((Number) value).longValue());
        } else if (value instanceof Number number) {
            return JsonValue.of(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            return JsonValue.of(bool);
        }
        return JsonValue.of(value.toString());
    }

    private CompletionStage<Long> bulkReplace(final MongoCollection<Document> collection,
            final List<Document> documents) {

        if (documents.isEmpty()) {
            return CompletableFuture.completedFuture(0L);
        }
        final List<WriteModel<Document>> writeModels = documents.stream()
                .<WriteModel<Document>>map(doc -> new ReplaceOneModel<>(Filters.eq(ID_FIELD, doc.get(ID_FIELD)), doc))
                .toList();
        return Source.fromPublisher(collection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false)))
                .runWith(Sink.head(), materializer)
                .thenApply(result -> (long) result.getModifiedCount() + result.getInsertedCount());
    }

    private static <T> T ownEntry(final Object entry, final Class<T> type) {
        if (!type.isInstance(entry)) {
            throw new IllegalArgumentException("Expected a " + type.getSimpleName() + " streamed by this store, got <" +
                    (entry == null ? "null" : entry.getClass().getName()) + ">.");
        }
        return type.cast(entry);
    }

}
