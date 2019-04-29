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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETE_AT;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;
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
 * MongoDB specific implementation of the {@link ThingsSearchUpdaterPersistence}.
 */
public final class MongoThingsSearchUpdaterPersistence implements ThingsSearchUpdaterPersistence {

    private final MongoDatabase database;
    private final MongoCollection<Document> collection;

    private MongoThingsSearchUpdaterPersistence(final MongoDatabase database) {
        this.database = database;
        collection = database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
    }

    /**
     * Constructor.
     *
     * @param database the database.
     */
    public static ThingsSearchUpdaterPersistence of(final MongoDatabase database) {
        return new MongoThingsSearchUpdaterPersistence(database);
    }

    @Override
    public Source<PolicyReferenceTag, NotUsed> getPolicyReferenceTags(final Map<String, Long> policyRevisions) {
        final Bson filter = in(PersistenceConstants.FIELD_POLICY_ID, policyRevisions.keySet());
        final Publisher<Document> publisher =
                collection.find(filter).projection(new Document()
                        .append(PersistenceConstants.FIELD_ID, new BsonInt32(1))
                        .append(PersistenceConstants.FIELD_POLICY_ID, new BsonInt32(1)));
        return Source.fromPublisher(publisher)
                .mapConcat(doc -> {
                    final String thingId = doc.getString(PersistenceConstants.FIELD_ID);
                    final String policyId = doc.getString(PersistenceConstants.FIELD_POLICY_ID);
                    final Long revision = policyRevisions.get(policyId);
                    if (revision == null) {
                        return Collections.emptyList();
                    } else {
                        final PolicyTag policyTag = PolicyTag.of(policyId, revision);
                        return Collections.singletonList(PolicyReferenceTag.of(thingId, policyTag));
                    }
                });
    }

    @Override
    public Source<String, NotUsed> getOutdatedThingIds(final PolicyTag policyTag) {
        final String policyId = policyTag.getId();
        final Bson filter = and(eq(PersistenceConstants.FIELD_POLICY_ID, policyId), lt(
                PersistenceConstants.FIELD_POLICY_REVISION, policyTag.getRevision()));
        final Publisher<Document> publisher =
                collection.find(filter).projection(new BsonDocument(PersistenceConstants.FIELD_ID, new BsonInt32(1)));
        return Source.fromPublisher(publisher)
                .map(doc -> doc.getString(PersistenceConstants.FIELD_ID));
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        final Bson filter = thingNamespaceFilter(namespace);
        final Bson update = new BsonDocument().append(AbstractWriteModel.SET,
                new BsonDocument().append(FIELD_DELETE_AT, new BsonDateTime(0L)));
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
