/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.mongo.write.streaming;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultPersistenceStreamConfig;

import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.ConfigFactory;

/**
 * Run parts of the updater stream for unit tests.
 */
public final class TestSearchUpdaterStream {

    private final MongoSearchUpdaterFlow mongoSearchUpdaterFlow;

    private TestSearchUpdaterStream(final MongoSearchUpdaterFlow mongoSearchUpdaterFlow) {
        this.mongoSearchUpdaterFlow = mongoSearchUpdaterFlow;
    }

    /**
     * Create a test stream.
     *
     * @param database the MongoDB database.
     * @return the test stream.
     */
    public static TestSearchUpdaterStream of(final MongoDatabase database) {
        final var mongoSearchUpdaterFlow = MongoSearchUpdaterFlow.of(database,
                DefaultPersistenceStreamConfig.of(ConfigFactory.empty()), WriteConcern.JOURNALED,
                13
        );
        return new TestSearchUpdaterStream(mongoSearchUpdaterFlow);
    }

    /**
     * Write a thing into the updater stream. The write model is built like the search updater's
     * {@code EnforcedThingMapper.toWriteModel} builds it for a first update: no previous metadata and no
     * referenced policies.
     *
     * @param thing the thing
     * @param policy the policy
     * @param policyRevision the policy revision
     * @return source of write result.
     */
    public Source<UpdaterResult, NotUsed> write(final Thing thing,
            final Policy policy,
            final long policyRevision) {

        final JsonObject thingJson = thing.toJson(FieldType.all());
        final ThingId thingId = ThingId.of(thingJson.getValueOrThrow(Thing.JsonFields.ID));
        final long thingRevision = thingJson.getValueOrThrow(Thing.JsonFields.REVISION);
        final PolicyId policyId = thingJson.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of).orElse(null);
        final Metadata metadata = Metadata.of(thingId, thingRevision,
                policyId == null ? null : PolicyTag.of(policyId, policyRevision), null, Set.of(), null, List.of(),
                List.of(), List.of(), List.of(UpdateReason.UNKNOWN));
        final AbstractWriteModel current =
                ThingWriteModel.of(metadata, SearchIndexDocumentFactory.create(thingJson, policy, metadata, -1));
        final AbstractWriteModel last =
                ThingDeleteModel.of(Metadata.ofDeleted(thing.getEntityId().orElseThrow()));

        return Source.single(new UpdaterData(current, last))
                .via(mongoSearchUpdaterFlow.create());
    }

    /**
     * Deletes a thing from the updater stream.
     *
     * @param thingId the thing id.
     * @param revision the revision.
     * @param policyId the policy id.
     * @param policyRevision the policy revision.
     * @return the write result.
     */
    public Source<UpdaterResult, NotUsed> delete(final ThingId thingId, final long revision,
            @Nullable final PolicyId policyId, final long policyRevision) {
        return delete(Metadata.of(thingId, revision, policyId == null ? null : PolicyTag.of(policyId, policyRevision),
                null, Set.of(), null));
    }

    /**
     * Delete a thing from the updater stream.
     *
     * @param metadata the metadata.
     * @return source of write result.
     */
    private Source<UpdaterResult, NotUsed> delete(final Metadata metadata) {
        final AbstractWriteModel current = ThingDeleteModel.of(metadata);
        final AbstractWriteModel last = ThingDeleteModel.of(Metadata.ofDeleted(metadata.getThingId()));
        return Source.single(new UpdaterData(current, last))
                .via(mongoSearchUpdaterFlow.create());
    }

}
