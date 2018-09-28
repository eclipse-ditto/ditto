/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.pf.Match;
import akka.stream.javadsl.Source;

/**
 * Database operations on the level of namespaces.
 */
public final class NamespaceOps {

    private final MongoDatabase db;

    private NamespaceOps(final MongoDatabase db) {
        this.db = db;
    }

    /**
     * Create a new NamespaceOps object.
     *
     * @param db the database to operate on.
     * @return a new NamespaceOps object.
     */
    public static NamespaceOps of(final MongoDatabase db) {
        return new NamespaceOps(db);
    }

    /**
     * Check if a collection has any document matching a namespace.
     *
     * @param selection collection-filter pair to identify documents belonging to a namespace.
     * @return source of whether the identified documents form an empty set.
     */
    public final Source<Boolean, NotUsed> isEmpty(final NamespaceSelection selection) {
        final String collection = selection.getCollectionName();
        final Bson filter = selection.getFilter();
        final CountOptions options = new CountOptions().limit(1);
        return Source.fromPublisher(db.getCollection(collection).count(filter, options))
                .map(count -> count == 0L);
    }

    /**
     * Check if selected documents of all given collections are empty.
     *
     * @param selections collection-filter pairs to identify documents belonging to a namespace.
     * @return source of whether the identified documents form an empty set.
     */
    public final Source<Boolean, NotUsed> areEmpty(final Collection<NamespaceSelection> selections) {
        return Source.from(selections)
                .flatMapConcat(this::isEmpty)
                .fold(true, Boolean::logicalAnd);
    }

    /**
     * Purge documents in a namespace from one collection.
     *
     * @param selection collection-filter pair to identify documents belonging to a namespace.
     * @return source of any error during the purge.
     */
    public final Source<Optional<Throwable>, NotUsed> purge(final NamespaceSelection selection) {
        final MongoCollection<Document> collection = db.getCollection(selection.getCollectionName());
        if (selection.isEntireCollection()) {
            return Source.fromPublisher(collection.drop())
                    .map(success -> Optional.empty());
        } else {
            // https://stackoverflow.com/a/33164008
            // claims unordered bulk ops halve MongoDB load
            final List<WriteModel<Document>> writeModel =
                    Collections.singletonList(new DeleteManyModel<>(selection.getFilter()));
            final BulkWriteOptions options = new BulkWriteOptions().ordered(false);
            return Source.fromPublisher(collection.bulkWrite(writeModel, options))
                    .map(result -> Optional.<Throwable>empty())
                    .recover(Match.<Throwable, Optional<Throwable>>matchAny(Optional::of).build());
        }
    }

    /**
     * Purge documents in a namespace from all given collections.
     *
     * @param selections collection-filter pairs to identify documents belonging to a namespace.
     * @return source of any errors during the purge.
     */
    public final Source<List<Throwable>, NotUsed> purgeAll(final Collection<NamespaceSelection> selections) {
        return Source.from(selections)
                .flatMapConcat(this::purge)
                .flatMapConcat(result -> result.map(Source::single).orElseGet(Source::empty))
                .fold(new LinkedList<>(), NamespaceOps::appendToMutableList);
    }

    private static <T> List<T> appendToMutableList(final List<T> xs, final T x) {
        xs.add(x);
        return xs;
    }
}
