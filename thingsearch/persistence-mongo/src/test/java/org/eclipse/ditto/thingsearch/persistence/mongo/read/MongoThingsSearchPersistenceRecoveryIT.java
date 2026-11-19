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
package org.eclipse.ditto.thingsearch.persistence.mongo.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultPersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResultStatus;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.persistence.mongo.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.thingsearch.persistence.mongo.PersistenceConstants;
import org.eclipse.ditto.thingsearch.persistence.mongo.write.mapping.SearchIndexDocumentMongoEncoder;
import org.eclipse.ditto.thingsearch.persistence.mongo.write.streaming.MongoSearchUpdaterFlow;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.ConfigFactory;

/**
 * Tests that the last write model recovered by {@link MongoThingsSearchPersistence#recoverLastWriteModel(ThingId)}
 * serves as the base of the next update of the thing.
 */
public final class MongoThingsSearchPersistenceRecoveryIT extends AbstractThingSearchPersistenceITBase {

    private static final ThingId THING_ID = ThingId.of("thing:recovery");
    private static final PolicyId POLICY_ID = PolicyId.of("thing:recovery-policy");
    private static final Policy POLICY = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
            .set(PolicyEntry.newInstance("viewer",
                    KNOWN_SUBJECTS.stream()
                            .map(subjectId -> Subject.newInstance(subjectId, SubjectType.GENERATED))
                            .toList(),
                    List.of(Resource.newInstance(ResourceKey.newInstance("thing:/"),
                            EffectedPermissions.newInstance(List.of("READ"), List.of())))))
            .setRevision(1L)
            .build();

    private MongoCollection<BsonDocument> bsonCollection;
    private MongoSearchUpdaterFlow flow;

    @Before
    public void init() {
        bsonCollection = mongoClient.getDefaultDatabase()
                .getCollection(PersistenceConstants.THINGS_COLLECTION_NAME, BsonDocument.class);
        flow = MongoSearchUpdaterFlow.of(mongoClient.getDefaultDatabase(),
                DefaultPersistenceStreamConfig.of(ConfigFactory.empty()), WriteConcern.JOURNALED, 13);
    }

    @Test
    public void recoveredDocumentIsPatchedAgainstTheStoredBson() {
        runBlockingWithReturn(writePersistence.write(thing(1L, 1), POLICY, 1L));
        final AbstractWriteModel recovered = recover();
        final ThingWriteModel next = nextWriteModel(thing(2L, 2));

        final List<UpdaterResult> results = waitFor(Source.single(new UpdaterData(next, recovered)).via(flow.create()));

        assertThat(recovered).isInstanceOf(ThingWriteModel.class);
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(((ThingWriteModel) result.writeModel()).isPatchUpdate()).isTrue();
            assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
        });
        assertThat(stored()).isEqualTo(SearchIndexDocumentMongoEncoder.encode(next.getDocument()));
    }

    @Test
    public void undecodableStoredDocumentIsReplacedOnTheNextUpdate() {
        final BsonDocument undecodable = SearchIndexDocumentMongoEncoder.encode(
                nextWriteModel(thing(1L, 1)).getDocument()).clone();
        undecodable.remove(PersistenceConstants.FIELD_NAMESPACE);
        runBlockingWithReturn(Source.fromPublisher(bsonCollection.insertOne(undecodable)));
        final AbstractWriteModel recovered = recover();
        final ThingWriteModel next = nextWriteModel(thing(2L, 2));

        final List<UpdaterResult> results = waitFor(Source.single(new UpdaterData(next, recovered)).via(flow.create()));

        assertThat(recovered).isInstanceOf(ThingDeleteModel.class);
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(((ThingWriteModel) result.writeModel()).isPatchUpdate()).isFalse();
            assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
        });
        final BsonDocument stored = stored();
        assertThat(stored).isEqualTo(SearchIndexDocumentMongoEncoder.encode(next.getDocument()));
        assertThat(stored.containsKey(PersistenceConstants.FIELD_NAMESPACE)).isTrue();
    }

    private AbstractWriteModel recover() {
        return readPersistence.recoverLastWriteModel(THING_ID)
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private BsonDocument stored() {
        return Source.fromPublisher(bsonCollection.find(Filters.eq(PersistenceConstants.FIELD_ID, THING_ID.toString())))
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    /**
     * Builds the write model like the search service's {@code EnforcedThingMapper.toWriteModel} does for a first
     * update: no old metadata and no referenced policies. That class lives in the thing-search service, which this
     * module must not depend on.
     */
    private static ThingWriteModel nextWriteModel(final Thing thing) {
        final JsonObject thingJson = thing.toJson(FieldType.all());
        final long thingRevision = thingJson.getValueOrThrow(Thing.JsonFields.REVISION);
        final PolicyTag thingPolicyTag = thingJson.getValue(Thing.JsonFields.POLICY_ID)
                .map(policyId -> PolicyTag.of(PolicyId.of(policyId), 1L))
                .orElse(null);
        final Metadata metadata = Metadata.of(THING_ID, thingRevision, thingPolicyTag, null, Set.of(), null, List.of(),
                List.of(), List.of(), List.of(UpdateReason.UNKNOWN));
        return ThingWriteModel.of(metadata, SearchIndexDocumentFactory.create(thingJson, POLICY, metadata, -1));
    }

    /**
     * A thing with 20 attributes of which only {@code attr0} depends on the given value, so that the update between
     * two of them is a patch.
     */
    private static Thing thing(final long revision, final int value) {
        final JsonObjectBuilder attributes = JsonObject.newBuilder().set("attr0", value);
        for (int i = 1; i < 20; ++i) {
            attributes.set("attr" + i, "a fairly long attribute value number " + i);
        }
        return Thing.newBuilder()
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .setRevision(revision)
                .setAttributes(attributes.build())
                .build();
    }

}
