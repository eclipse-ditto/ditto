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
import org.eclipse.ditto.services.utils.persistence.mongo.ops.MongoOpsUtil;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.NamespaceOps;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Namespace Ops on MongoDB EventSource persistence.
 */
public final class MongoNamespaceOps implements NamespaceOps {

    private final MongoDatabase db;
    private final MongoOpsSelectionProvider selectionProvider;

    private MongoNamespaceOps(final MongoDatabase db, final MongoEventSourceSettings eventSourceSettings) {
        this.db = requireNonNull(db);
        requireNonNull(eventSourceSettings);
        this.selectionProvider = MongoOpsSelectionProvider.of(eventSourceSettings);
    }

    /**
     * Create a new instance.
     *
     * @param db the database
     * @param eventSourceSettings the {@link MongoEventSourceSettings}
     * @return the instance
     */
    public static MongoNamespaceOps of(final MongoDatabase db, final MongoEventSourceSettings eventSourceSettings) {
        return new MongoNamespaceOps(db, eventSourceSettings);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        requireNonNull(namespace);

        final Collection<MongoOpsSelection> selections = selectNamespace(namespace.toString());

        return purgeAllSelections(selections);
    }

    private Collection<MongoOpsSelection> selectNamespace(final String namespace) {
        return selectionProvider.selectNamespace(namespace);
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
        if (selection.isEntireCollection()) {
            return MongoOpsUtil.drop(collection);
        } else {
            return MongoOpsUtil.deleteByFilter(collection, selection.getFilter());
        }
    }

}
