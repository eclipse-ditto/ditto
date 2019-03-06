/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.EntitiesOps;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.MongoOpsUtil;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Entities Ops on MongoDB EventSource persistence.
 */
public final class MongoEntitiesOps implements EntitiesOps {

    private final MongoDatabase db;
    private final MongoOpsSelectionProvider selectionProvider;

    private MongoEntitiesOps(final MongoDatabase db, final MongoOpsSelectionProvider selectionProvider) {
        this.db = requireNonNull(db);
        this.selectionProvider = requireNonNull(selectionProvider);
    }

    /**
     * Create a new instance.
     *
     * @param db the database
     * @param selectionProvider the {@link MongoOpsSelectionProvider}
     * @return the instance
     */
    public static MongoEntitiesOps of(final MongoDatabase db,
            final MongoOpsSelectionProvider selectionProvider) {
        return new MongoEntitiesOps(db, selectionProvider);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purgeEntity(final CharSequence entityId) {
        requireNonNull(entityId);

        final Collection<MongoOpsSelection> selections = selectEntity(entityId.toString());

        return purgeAllSelections(selections);
    }

    private Collection<MongoOpsSelection> selectEntity(final String entityId) {
        return selectionProvider.selectEntity(entityId);
    }

    private Source<List<Throwable>, NotUsed> purgeAllSelections(final Collection<MongoOpsSelection> selections) {
        Source<List<Throwable>, NotUsed> result = Source.empty();

        for (MongoOpsSelection mongoOpsSelection : selections) {
            final Source<List<Throwable>, NotUsed> purge = purge(mongoOpsSelection);
            result = result.merge(purge);
        }

        return result;
    }

    private Source<List<Throwable>, NotUsed> purge(final MongoOpsSelection selection) {
        final MongoCollection<Document> collection = db.getCollection(selection.getCollectionName());

        return MongoOpsUtil.deleteByFilter(collection, selection.getFilter());
    }

}
