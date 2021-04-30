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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.MongoOpsUtil;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Namespace Ops on MongoDB EventSource persistence.
 */
public final class MongoNamespacePersistenceOperations implements NamespacePersistenceOperations {

    private final MongoDatabase db;
    private final MongoPersistenceOperationsSelectionProvider selectionProvider;

    private MongoNamespacePersistenceOperations(final MongoDatabase db,
            final MongoEventSourceSettings eventSourceSettings) {

        this.db = checkNotNull(db, "database");
        selectionProvider = MongoPersistenceOperationsSelectionProvider.of(eventSourceSettings);
    }

    /**
     * Create a new instance.
     *
     * @param db the database
     * @param eventSourceSettings the {@link MongoEventSourceSettings}
     * @return the instance
     */
    public static MongoNamespacePersistenceOperations of(final MongoDatabase db,
            final MongoEventSourceSettings eventSourceSettings) {

        return new MongoNamespacePersistenceOperations(db, eventSourceSettings);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        return purgeAllSelections(selectNamespace(namespace));
    }

    private Collection<MongoPersistenceOperationsSelection> selectNamespace(final CharSequence namespace) {
        return selectionProvider.selectNamespace(namespace);
    }

    private Source<List<Throwable>, NotUsed> purgeAllSelections(
            final Iterable<MongoPersistenceOperationsSelection> selections) {

        Source<List<Throwable>, NotUsed> result = Source.empty();

        for (final MongoPersistenceOperationsSelection mongoOpsSelection : selections) {
            final Source<List<Throwable>, NotUsed> purge = purge(mongoOpsSelection);
            result = result.merge(purge);
        }

        return result;
    }

    private Source<List<Throwable>, NotUsed> purge(final MongoPersistenceOperationsSelection selection) {
        final MongoCollection<Document> collection = db.getCollection(selection.getCollectionName());
        if (selection.isEntireCollection()) {
            return MongoOpsUtil.drop(collection);
        } else {
            return MongoOpsUtil.deleteByFilter(collection, selection.getFilter());
        }
    }

}
