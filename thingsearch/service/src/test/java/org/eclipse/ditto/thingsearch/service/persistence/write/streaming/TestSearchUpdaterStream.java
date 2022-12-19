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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultPersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.EnforcedThingMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.ThingUpdater;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.stream.javadsl.Source;

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
    public static TestSearchUpdaterStream of(final MongoDatabase database,
            final SearchUpdateMapper searchUpdateMapper) {

        final var mongoSearchUpdaterFlow = MongoSearchUpdaterFlow.of(database,
                DefaultPersistenceStreamConfig.of(ConfigFactory.empty())
        );
        return new TestSearchUpdaterStream(mongoSearchUpdaterFlow);
    }

    /**
     * Write a thing into the updater stream.
     *
     * @param thing the thing
     * @param policy the policy
     * @param policyRevision the policy revision
     * @return source of write result.
     */
    public Source<WriteResultAndErrors, NotUsed> write(final Thing thing,
            final Policy policy,
            final long policyRevision) {

        final JsonObject thingJson = thing.toJson(FieldType.all());
        final AbstractWriteModel writeModel =
                EnforcedThingMapper.toWriteModel(thingJson, policy, Set.of(), policyRevision, null, -1);
        final var mongoWriteModel =
                writeModel.toIncrementalMongo(
                        ThingDeleteModel.of(Metadata.ofDeleted(thing.getEntityId().orElseThrow())), 13).orElseThrow();

        return Source.single(mongoWriteModel)
                .via(mongoSearchUpdaterFlow.create())
                .map(ThingUpdater.Result::resultAndErrors);
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
    public Source<WriteResultAndErrors, NotUsed> delete(final ThingId thingId, final long revision,
            @Nullable final PolicyId policyId, final long policyRevision) {
        return delete(Metadata.of(thingId, revision, policyId == null ? null : PolicyTag.of(policyId, policyRevision),
                Set.of(), null));
    }

    /**
     * Delete a thing from the updater stream.
     *
     * @param metadata the metadata.
     * @return source of write result.
     */
    private Source<WriteResultAndErrors, NotUsed> delete(final Metadata metadata) {
        final AbstractWriteModel writeModel = ThingDeleteModel.of(metadata);
        return Source.single(writeModel.toIncrementalMongo(writeModel, 13).orElseThrow())
                .via(mongoSearchUpdaterFlow.create())
                .map(ThingUpdater.Result::resultAndErrors);
    }

}
