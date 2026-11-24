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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.AsPublisher;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Tests {@link MongoSearchUpdaterFlow}.
 */
public final class MongoSearchUpdaterFlowTest {

    private static final ThingId THING_ID = ThingId.of("thing:id");

    private final ActorSystem actorSystem = ActorSystem.create();

    private MongoCollection<BsonDocument> collection;
    private MongoSearchUpdaterFlow underTest;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        collection = mock(MongoCollection.class);
        when(collection.withWriteConcern(any())).thenReturn(collection);
        final MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(eq(PersistenceConstants.THINGS_COLLECTION_NAME), eq(BsonDocument.class)))
                .thenReturn(collection);
        final PersistenceStreamConfig persistenceConfig = mock(PersistenceStreamConfig.class);
        when(persistenceConfig.getWithAcknowledgementsWriteConcern()).thenReturn(WriteConcern.ACKNOWLEDGED);
        underTest = MongoSearchUpdaterFlow.of(database, persistenceConfig, 21);
    }

    @After
    public void stopActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void failureToEncodeTheWriteModelSkipsTheUpdateAndStopsItsTimers() throws Exception {
        final StartedTimer timer = mock(StartedTimer.class);
        final var writeModel = ThingWriteModel.of(metadata(2L, timer), unencodableDocument());
        final var lastWriteModel = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        assertThat(results).isEmpty();
        verify(timer).stop();
        verify(collection, never()).bulkWrite(anyList(), any(BulkWriteOptions.class));
    }

    @Test
    public void failureToEncodeTheLastWriteModelSkipsTheUpdateAndStopsItsTimers() throws Exception {
        final StartedTimer timer = mock(StartedTimer.class);
        final var writeModel = ThingWriteModel.of(metadata(2L, timer), document(2L));
        final var lastWriteModel = ThingWriteModel.of(metadata(1L, null), unencodableDocument());

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        assertThat(results).isEmpty();
        verify(timer).stop();
        verify(collection, never()).bulkWrite(anyList(), any(BulkWriteOptions.class));
    }

    @Test
    public void timerThatFailsToStopDoesNotFailTheStream() throws Exception {
        final StartedTimer timer = mock(StartedTimer.class);
        doThrow(new IllegalStateException("already stopped")).when(timer).stop();
        final var writeModel = ThingWriteModel.of(metadata(2L, timer), unencodableDocument());
        final var lastWriteModel = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        assertThat(results).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void updateAgainstANotIndexedLastModelIsAFullReplaceWithUpsert() throws Exception {
        final BulkWriteResult bulkWriteResult = BulkWriteResult.acknowledged(0, 0, 0, 0,
                List.of(new BulkWriteUpsert(0, new BsonString(THING_ID.toString()))), List.of());
        when(collection.bulkWrite(anyList(), any(BulkWriteOptions.class)))
                .thenReturn(Source.single(bulkWriteResult)
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), actorSystem));
        final var writeModel = ThingWriteModel.of(metadata(2L, null), document(2L));
        final var lastWriteModel = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        final ArgumentCaptor<List<? extends WriteModel<BsonDocument>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(collection).bulkWrite(captor.capture(), any(BulkWriteOptions.class));
        assertThat(captor.getValue()).singleElement()
                .isInstanceOfSatisfying(ReplaceOneModel.class,
                        replace -> assertThat(replace.getReplaceOptions().isUpsert()).isTrue());
        assertThat(results).hasSize(1);
    }

    @Test
    public void resultCarriesTheBulkWriteCorrelationIdAndCounts() throws Exception {
        final BulkWriteResult bulkWriteResult = BulkWriteResult.acknowledged(2, 1, 3, 1, List.of(), List.of());
        whenBulkWriteReturn(bulkWriteResult);
        final var writeModel = ThingWriteModel.of(metadata(2L, null), document(2L));
        final var lastWriteModel = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        assertThat(results).hasSize(1);
        final SearchWriteResult result = results.get(0).result();
        assertThat(result.getCorrelationId()).hasValueSatisfying(id -> assertThat(id).isNotBlank());
        assertThat(result.getInsertedCount()).isEqualTo(2);
        assertThat(result.getDeletedCount()).isEqualTo(3);
    }

    @Test
    public void resultOfAnUnexpectedErrorCarriesTheCorrelationId() {
        final SearchWriteResult unexpected = MongoSearchUpdaterFlow.toSearchWriteResult(
                WriteResultAndErrors.unexpectedError(List.of(), new IllegalStateException("boom"), "cid-1"));
        final SearchWriteResult unacknowledged = MongoSearchUpdaterFlow.toSearchWriteResult(
                WriteResultAndErrors.success(List.of(), BulkWriteResult.unacknowledged(), "cid-2"));

        assertThat(unexpected.getCorrelationId()).contains("cid-1");
        assertThat(unexpected.getInsertedCount()).isZero();
        assertThat(unexpected.getDeletedCount()).isZero();
        assertThat(unacknowledged.getCorrelationId()).contains("cid-2");
        assertThat(unacknowledged.getInsertedCount()).isZero();
        assertThat(unacknowledged.getDeletedCount()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void patchUpdateIsReportedWithThePatchFlagAndThePreviousRevision() throws Exception {
        whenBulkWriteReturn(BulkWriteResult.acknowledged(0, 1, 0, 1, List.of(), List.of()));
        final var lastWriteModel = ThingWriteModel.of(metadata(1L, null), document(1L, thingWithAttributes(1)));
        final SearchIndexDocument currentDocument = document(2L, thingWithAttributes(2));
        final var writeModel = ThingWriteModel.of(metadata(2L, null), currentDocument);

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        final ArgumentCaptor<List<? extends WriteModel<BsonDocument>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(collection).bulkWrite(captor.capture(), any(BulkWriteOptions.class));
        assertThat(captor.getValue()).singleElement().isInstanceOf(UpdateOneModel.class);
        assertThat(results).singleElement().satisfies(result ->
                assertThat(result.writeModel()).isInstanceOfSatisfying(ThingWriteModel.class, reported -> {
                    assertThat(reported.isPatchUpdate()).isTrue();
                    assertThat(reported.getPreviousRevision()).isEqualTo(1L);
                    assertThat(reported.getDocument()).isEqualTo(currentDocument);
                }));
    }

    @Test
    public void fullReplaceIsReportedWithoutThePatchFlag() throws Exception {
        whenBulkWriteReturn(BulkWriteResult.acknowledged(0, 0, 0, 0,
                List.of(new BulkWriteUpsert(0, new BsonString(THING_ID.toString()))), List.of()));
        final var writeModel = ThingWriteModel.of(metadata(2L, null), document(2L));
        final var lastWriteModel = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

        final List<UpdaterResult> results = runFlow(new UpdaterData(writeModel, lastWriteModel));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.writeModel()).isSameAs(writeModel);
            assertThat(((ThingWriteModel) result.writeModel()).isPatchUpdate()).isFalse();
        });
    }

    private void whenBulkWriteReturn(final BulkWriteResult bulkWriteResult) {
        when(collection.bulkWrite(anyList(), any(BulkWriteOptions.class)))
                .thenReturn(Source.single(bulkWriteResult)
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), actorSystem));
    }

    private List<UpdaterResult> runFlow(final UpdaterData updaterData) throws Exception {
        return Source.single(updaterData)
                .via(underTest.create())
                .runWith(Sink.seq(), actorSystem)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    private static Metadata metadata(final long revision, final StartedTimer timer) {
        return Metadata.of(THING_ID, revision, null, null, Set.of(), timer);
    }

    private static SearchIndexDocument document(final long revision) {
        return document(revision, JsonObject.empty());
    }

    private static SearchIndexDocument document(final long revision, final JsonObject thing) {
        return SearchIndexDocument.newBuilder(THING_ID)
                .namespace(THING_ID.getNamespace())
                .revision(revision)
                .thing(thing)
                .policyAuth(JsonObject.empty())
                .build();
    }

    /**
     * A thing with 20 attributes of which only {@code attr0} depends on the given value, so that the diff between
     * two of them is smaller than the whole document.
     */
    private static JsonObject thingWithAttributes(final int value) {
        final JsonObjectBuilder attributes = JsonObject.newBuilder().set("attr0", value);
        for (int i = 1; i < 20; ++i) {
            attributes.set("attr" + i, "a fairly long attribute value number " + i);
        }
        return JsonObject.newBuilder()
                .set("thingId", THING_ID.toString())
                .set("attributes", attributes.build())
                .build();
    }

    /**
     * A document whose encoding to BSON fails, standing in for any failure to compute the MongoDB write model.
     */
    private static SearchIndexDocument unencodableDocument() {
        final SearchIndexDocument document = mock(SearchIndexDocument.class);
        when(document.thingId()).thenThrow(new IllegalStateException("document cannot be encoded"));
        return document;
    }

}
