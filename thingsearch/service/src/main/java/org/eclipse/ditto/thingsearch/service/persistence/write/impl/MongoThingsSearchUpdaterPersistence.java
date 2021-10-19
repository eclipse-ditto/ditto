/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.impl;

import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterPersistenceConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.reactivestreams.Publisher;

import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;

/**
 * MongoDB specific implementation of the {@link org.eclipse.ditto.thingsearch.service.persistence.write.ThingsSearchUpdaterPersistence}.
 */
public final class MongoThingsSearchUpdaterPersistence implements ThingsSearchUpdaterPersistence {

    private final MongoCollection<Document> collection;

    private MongoThingsSearchUpdaterPersistence(final MongoDatabase database,
            final UpdaterPersistenceConfig updaterPersistenceConfig) {

        collection = database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME)
                .withReadConcern(updaterPersistenceConfig.readConcern().getMongoReadConcern())
                .withReadPreference(updaterPersistenceConfig.readPreference().getMongoReadPreference());
    }

    /**
     * Constructor.
     *
     * @param database the database.
     * @param updaterPersistenceConfig the updater persistence config to use.
     */
    public static ThingsSearchUpdaterPersistence of(final MongoDatabase database,
            final UpdaterPersistenceConfig updaterPersistenceConfig) {
        return new MongoThingsSearchUpdaterPersistence(database, updaterPersistenceConfig);
    }

    @Override
    public Source<PolicyReferenceTag, NotUsed> getPolicyReferenceTags(final Map<PolicyId, Long> policyRevisions) {
        final Set<String> policyIds = policyRevisions.keySet()
                        .stream()
                        .map(String::valueOf)
                        .collect(Collectors.toSet());
        final Bson filter = or(
                in(PersistenceConstants.FIELD_POLICY_ID, policyIds),
                in(PersistenceConstants.FIELD_POLICY_IMPORTS, policyIds)
        );
        final Publisher<Document> publisher =
                collection.find(filter).projection(new Document()
                        .append(PersistenceConstants.FIELD_ID, new BsonInt32(1))
                        .append(PersistenceConstants.FIELD_POLICY_ID, new BsonInt32(1))
                        .append(PersistenceConstants.FIELD_POLICY_IMPORTS, new BsonInt32(1))
                );
        return Source.fromPublisher(publisher)
                .mapConcat(doc -> {
                    final ThingId thingId = ThingId.of(doc.getString(PersistenceConstants.FIELD_ID));
                    final String policyIdString = doc.getString(PersistenceConstants.FIELD_POLICY_ID);
                    final PolicyId policyId = PolicyId.of(policyIdString);
                    final Long revision = policyRevisions.get(policyId);
                    if (revision == null) {
                        if (policyIds.contains(policyIdString)) {
                            return Collections.emptyList();
                        } else {
                            // in that case, a policy was modified (being contained in Set `policyIds`) which was
                            // imported by the `policyId`
                            final Set<PolicyReferenceTag> combinedPolicyReferenceTags = new HashSet<>();
                            combinedPolicyReferenceTags.add(PolicyReferenceTag.of(thingId, PolicyTag.of(policyId,
                                    PolicyTag.IMPORTED_POLICY_UPDATE_CAUSE_REVISION)));

                            doc.getList(
                                    PersistenceConstants.FIELD_POLICY_IMPORTS, String.class, Collections.emptyList()
                            ).stream()
                                    .filter(policyIds::contains)
                                    .map(importedPolicyId -> {
                                        final PolicyId imported = PolicyId.of(importedPolicyId);
                                        return PolicyTag.of(imported, policyRevisions.get(imported));
                                    })
                                    .map(policyTag -> PolicyReferenceTag.of(thingId, policyTag))
                                    .forEach(combinedPolicyReferenceTags::add);
                            return combinedPolicyReferenceTags;
                        }
                    } else {
                        final PolicyTag policyTag = PolicyTag.of(policyId, revision);
                        return Collections.singletonList(PolicyReferenceTag.of(thingId, policyTag));
                    }
                });
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        final Bson filter = thingNamespaceFilter(namespace);
        final Bson update = new BsonDocument().append(AbstractWriteModel.SET,
                new BsonDocument().append(PersistenceConstants.FIELD_DELETE_AT, new BsonDateTime(0L)));
        final UpdateOptions updateOptions = new UpdateOptions().bypassDocumentValidation(true);
        final WriteModel<Document> writeModel = new UpdateManyModel<>(filter, update, updateOptions);

        return Source.fromPublisher(collection.bulkWrite(Collections.singletonList(writeModel)))
                .map(bulkWriteResult -> Collections.<Throwable>emptyList())
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<List<Throwable>, NotUsed>>()
                        .matchAny(throwable -> Source.single(Collections.singletonList(throwable)))
                        .build());
    }

    private Document thingNamespaceFilter(final CharSequence namespace) {
        return new Document().append(PersistenceConstants.FIELD_NAMESPACE, new BsonString(namespace.toString()));
    }

}
