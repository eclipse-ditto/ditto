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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link MongoPersistedPayloadStore} that need no MongoDB: the query filters, the pure JSON &harr;
 * BSON conversion of the progress record, the argument validation the SPI promises (which happens before any
 * publisher is built), and the way {@link MongoPersistenceBackendProvider#payloadStore(String)} builds the store. Like
 * {@code MongoPersistenceOperationsFactoryTest}, this relies on {@code MongoClientWrapper} being lazy: no connection is
 * opened before the first database operation, which these tests never run.
 * <p>
 * The filters and the progress document are pinned to what the connectivity encryption migration produced before the
 * store existed ({@code MigrationStreamFactory.buildEncryptableFieldsFilter}, {@code buildResumeFilter} and
 * {@code MigrationProgressTracker.saveProgress}): same dotted paths, same {@code $or} of {@code $exists}, same
 * {@code _id: "current"}, same field names and BSON types (strings, int64, null).
 */
public final class MongoPersistedPayloadStoreTest {

    private static final Config CONFIG = ConfigFactory.parseString("""
            ditto.mongodb.uri = "mongodb://localhost:27017/testdb"
            """);

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("MongoPersistedPayloadStoreTest", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    // ---- query filters ----

    @Test
    public void requiredFieldsFilterIsAnOrOfExistsOnTheDottedPathsBelowTheSnapshotPayload() {
        final Bson filter = MongoPersistedPayloadStore.requiredFieldsFilter("s2",
                List.of("/uri", "/credentials/password"));

        assertThat(toBson(filter)).isEqualTo(toBson(Filters.or(
                Filters.exists("s2.uri"),
                Filters.exists("s2.credentials.password"))));
    }

    @Test
    public void requiredFieldsFilterIsAnOrOfExistsOnTheDottedPathsBelowTheEventPayloads() {
        final Bson filter = MongoPersistedPayloadStore.requiredFieldsFilter("events.p",
                List.of("/connection/uri", "/connection/credentials/password"));

        assertThat(toBson(filter)).isEqualTo(toBson(Filters.or(
                Filters.exists("events.p.connection.uri"),
                Filters.exists("events.p.connection.credentials.password"))));
    }

    @Test
    public void noResumeCursorMeansNoResumeFilter() {
        assertThat(toBson(MongoPersistedPayloadStore.resumeFilter(null))).isEqualTo(toBson(Filters.empty()));
    }

    @Test
    public void anObjectIdCursorResumesWithAGreaterThanOnTheObjectId() {
        final Bson filter = MongoPersistedPayloadStore.resumeFilter("507f1f77bcf86cd799439011");

        assertThat(toBson(filter))
                .isEqualTo(toBson(Filters.gt("_id", new ObjectId("507f1f77bcf86cd799439011"))));
    }

    @Test
    public void anotherCursorResumesWithAGreaterThanOnTheString() {
        final Bson filter = MongoPersistedPayloadStore.resumeFilter("connection:plain-id");

        assertThat(toBson(filter)).isEqualTo(toBson(Filters.gt("_id", "connection:plain-id")));
    }

    // ---- progress record ----

    @Test
    public void progressJsonBecomesTheDocumentTheMigrationWroteBefore() {
        final Document document = MongoPersistedPayloadStore.progressToDocument(progressJson());

        assertThat(document).isEqualTo(progressDocumentAsWrittenBefore());
        assertThat(List.copyOf(document.keySet())).isEqualTo(List.copyOf(progressDocumentAsWrittenBefore().keySet()));
        // counters are int64 so that Document.getLong(...) reads them, as before
        assertThat(document.get("snapshotsProcessed")).isInstanceOf(Long.class);
        assertThat(document.getLong("snapshotsProcessed")).isEqualTo(150L);
        assertThat(document.getLong("journalFailed")).isEqualTo(0L);
        // a JSON null is a BSON null, not an absent field
        assertThat(document.containsKey("lastProcessedJournalId")).isTrue();
        assertThat(document.get("lastProcessedJournalId")).isNull();
        assertThat(document.getString("lastProcessedSnapshotId")).isEqualTo("507f1f77bcf86cd799439011");
    }

    @Test
    public void progressDocumentWrittenBeforeRoundTripsThroughJson() {
        final Document before = progressDocumentAsWrittenBefore();

        final JsonObject json = MongoPersistedPayloadStore.progressToJson(before);
        final Document after = MongoPersistedPayloadStore.progressToDocument(json);

        assertThat(json.contains("_id")).isFalse();
        assertThat(json).isEqualTo(progressJson());
        assertThat(json.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(150L);
        assertThat(json.getValue("lastProcessedJournalId").map(JsonValue::isNull)).contains(true);
        assertThat(after).isEqualTo(before);
        assertThat(after.get("snapshotsSkipped")).isInstanceOf(Long.class);
    }

    @Test
    public void progressJsonMustNotCarryTheId() {
        final JsonObject withId = progressJson().toBuilder().set("_id", "other").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MongoPersistedPayloadStore.progressToDocument(withId))
                .withMessageContaining("_id");
    }

    @Test
    public void progressJsonMustBeFlat() {
        final JsonObject nested = progressJson().toBuilder()
                .set("nested", JsonFactory.newObjectBuilder().set("a", 1).build())
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MongoPersistedPayloadStore.progressToDocument(nested))
                .withMessageContaining("nested");
    }

    @Test
    public void progressDocumentWithOtherScalarTypesStillLoads() {
        final Document document = progressDocumentAsWrittenBefore()
                .append("anInt", 3)
                .append("aDouble", 1.5d)
                .append("aFlag", true)
                .append("anObjectId", new ObjectId("507f1f77bcf86cd799439011"));

        final JsonObject json = MongoPersistedPayloadStore.progressToJson(document);

        assertThat(json.getValue("anInt").map(JsonValue::asLong)).contains(3L);
        assertThat(json.getValue("aDouble").map(JsonValue::asDouble)).contains(1.5d);
        assertThat(json.getValue("aFlag").map(JsonValue::asBoolean)).contains(true);
        assertThat(json.getValue("anObjectId").map(JsonValue::asString)).contains("507f1f77bcf86cd799439011");
    }

    // ---- argument validation, as the SPI documents it: synchronous, before any publisher is built ----

    @Test
    public void streamSnapshotsRejectsEmptyRequiredFieldPointers() {
        final PersistedPayloadStore store = newStore("connection");
        try {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> store.streamSnapshots(null, List.of(), 100))
                    .withMessageContaining("requiredFieldPointers");
        } finally {
            store.close();
        }
    }

    @Test
    public void streamJournalEntriesRejectsEmptyRequiredFieldPointers() {
        final PersistedPayloadStore store = newStore("connection");
        try {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> store.streamJournalEntries("507f1f77bcf86cd799439011", List.of(), 100))
                    .withMessageContaining("requiredFieldPointers");
        } finally {
            store.close();
        }
    }

    @Test
    public void rewriteSnapshotsRejectsASnapshotNotStreamedByThisStore() {
        final PersistedPayloadStore store = newStore("connection");
        try {
            final PersistedSnapshot foreign = new ForeignSnapshot("c-1", "connection:foreign", progressJson());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> store.rewriteSnapshots(List.of(foreign)))
                    .withMessageContaining("MongoPersistedSnapshot")
                    .withMessageContaining(ForeignSnapshot.class.getName());
        } finally {
            store.close();
        }
    }

    @Test
    public void rewriteJournalEntriesRejectsAnEntryNotStreamedByThisStore() {
        final PersistedPayloadStore store = newStore("connection");
        try {
            final PersistedJournalEntry foreign =
                    new ForeignJournalEntry("c-1", "connection:foreign", List.of(progressJson()));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> store.rewriteJournalEntries(List.of(foreign)))
                    .withMessageContaining("MongoPersistedJournalEntry")
                    .withMessageContaining(ForeignJournalEntry.class.getName());
        } finally {
            store.close();
        }
    }

    @Test
    public void saveProgressWithAnInvalidRecordFailsTheStageInsteadOfThrowing() {
        final PersistedPayloadStore store = newStore("connection");
        try {
            final JsonObject withId = progressJson().toBuilder().set("_id", "other").build();

            // the SPI promises a failed stage for a record the store cannot save, never a synchronous throw
            final CompletionStage<Void> stage = store.saveProgress(withId);

            assertThat(stage.toCompletableFuture()).isCompletedExceptionally();
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("_id");
        } finally {
            store.close();
        }
    }

    // ---- construction through the provider ----

    @Test
    public void providerBuildsAFreshStoreOverTheEntityTypesCollectionsPerCall() {
        final MongoPersistenceBackendProvider provider =
                new MongoPersistenceBackendProvider(system, ConfigFactory.empty());

        final PersistedPayloadStore first = provider.payloadStore("connection");
        final PersistedPayloadStore second = provider.payloadStore("connection");
        try {
            assertThat(first).isInstanceOf(MongoPersistedPayloadStore.class).isNotSameAs(second);
            final MongoPersistedPayloadStore store = (MongoPersistedPayloadStore) first;
            assertThat(store.snapshotCollection().getNamespace().getCollectionName()).isEqualTo("connection_snaps");
            assertThat(store.journalCollection().getNamespace().getCollectionName()).isEqualTo("connection_journal");
            assertThat(store.progressCollection().getNamespace().getCollectionName())
                    .isEqualTo("connection_encryption_migration");
            assertThat(store.snapshotCollection().getNamespace().getDatabaseName()).isEqualTo("testdb");
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void closingTwiceIsHarmless() {
        final PersistedPayloadStore store = newStore("thing");

        store.close();
        store.close();
    }

    private static PersistedPayloadStore newStore(final String entityType) {
        return new MongoPersistenceBackendProvider(system, ConfigFactory.empty()).payloadStore(entityType);
    }

    private static BsonDocument toBson(final Bson filter) {
        return filter.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    /** A snapshot of another backend's store: the MongoDB store must refuse to write it. */
    private record ForeignSnapshot(String cursor, String pid, JsonObject payload) implements PersistedSnapshot {

        @Override
        public PersistedSnapshot withPayload(final JsonObject newPayload) {
            return new ForeignSnapshot(cursor, pid, newPayload);
        }
    }

    /** A journal entry of another backend's store: the MongoDB store must refuse to write it. */
    private record ForeignJournalEntry(String cursor, String pid, List<JsonObject> eventPayloads)
            implements PersistedJournalEntry {

        @Override
        public PersistedJournalEntry withEventPayloads(final List<JsonObject> payloads) {
            return new ForeignJournalEntry(cursor, pid, List.copyOf(payloads));
        }
    }

    /** The progress record as connectivity's {@code MigrationProgressTracker} renders it. */
    private static JsonObject progressJson() {
        return JsonFactory.newObjectBuilder()
                .set("phase", "snapshots")
                .set("lastProcessedSnapshotId", "507f1f77bcf86cd799439011")
                .set("lastProcessedSnapshotPid", "connection:mqtt-prod-sensor-01")
                .set("lastProcessedJournalId", JsonValue.nullLiteral())
                .set("lastProcessedJournalPid", JsonValue.nullLiteral())
                .set("snapshotsProcessed", 150L)
                .set("snapshotsSkipped", 10L)
                .set("snapshotsFailed", 2L)
                .set("journalProcessed", 0L)
                .set("journalSkipped", 0L)
                .set("journalFailed", 0L)
                .set("startedAt", "2026-02-16T10:00:00Z")
                .set("updatedAt", "2026-02-16T10:05:00Z")
                .build();
    }

    /** The progress document exactly as {@code MigrationProgressTracker.saveProgress} built it before the store. */
    private static Document progressDocumentAsWrittenBefore() {
        return new Document()
                .append("_id", "current")
                .append("phase", "snapshots")
                .append("lastProcessedSnapshotId", "507f1f77bcf86cd799439011")
                .append("lastProcessedSnapshotPid", "connection:mqtt-prod-sensor-01")
                .append("lastProcessedJournalId", null)
                .append("lastProcessedJournalPid", null)
                .append("snapshotsProcessed", 150L)
                .append("snapshotsSkipped", 10L)
                .append("snapshotsFailed", 2L)
                .append("journalProcessed", 0L)
                .append("journalSkipped", 0L)
                .append("journalFailed", 0L)
                .append("startedAt", "2026-02-16T10:00:00Z")
                .append("updatedAt", "2026-02-16T10:05:00Z");
    }
}
