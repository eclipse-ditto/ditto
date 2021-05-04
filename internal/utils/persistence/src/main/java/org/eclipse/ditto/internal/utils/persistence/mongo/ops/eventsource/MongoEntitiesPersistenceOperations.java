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
package org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.persistence.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.MongoOpsUtil;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Entities Ops on MongoDB EventSource persistence.
 */
public final class MongoEntitiesPersistenceOperations implements EntityPersistenceOperations {

    private final MongoDatabase db;
    private final MongoPersistenceOperationsSelectionProvider selectionProvider;

    private MongoEntitiesPersistenceOperations(final MongoDatabase db,
            final MongoEventSourceSettings eventSourceSettings) {
        this.db = requireNonNull(db);
        requireNonNull(eventSourceSettings);
        this.selectionProvider = MongoPersistenceOperationsSelectionProvider.of(eventSourceSettings);
    }

    /**
     * Create a new instance.
     *
     * @param db the database
     * @param eventSourceSettings the {@link MongoEventSourceSettings}
     * @return the instance
     */
    public static MongoEntitiesPersistenceOperations of(final MongoDatabase db,
            final MongoEventSourceSettings eventSourceSettings) {
        return new MongoEntitiesPersistenceOperations(db, eventSourceSettings);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purgeEntity(final EntityId entityId) {
        requireNonNull(entityId);

        final Collection<MongoPersistenceOperationsSelection> selections = selectEntity(entityId);

        return purgeAllSelections(selections);
    }

    private Collection<MongoPersistenceOperationsSelection> selectEntity(final EntityId entityId) {
        return selectionProvider.selectEntity(entityId);
    }

    private Source<List<Throwable>, NotUsed> purgeAllSelections(
            final Collection<MongoPersistenceOperationsSelection> selections) {
        Source<List<Throwable>, NotUsed> result = Source.empty();

        for (MongoPersistenceOperationsSelection mongoOpsSelection : selections) {
            final Source<List<Throwable>, NotUsed> purge = purge(mongoOpsSelection);
            result = result.merge(purge);
        }

        return result;
    }

    private Source<List<Throwable>, NotUsed> purge(final MongoPersistenceOperationsSelection selection) {
        final MongoCollection<Document> collection = db.getCollection(selection.getCollectionName());

        return MongoOpsUtil.deleteByFilter(collection, selection.getFilter());
    }

}
