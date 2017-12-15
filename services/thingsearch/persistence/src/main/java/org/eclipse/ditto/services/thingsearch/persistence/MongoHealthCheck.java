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
package org.eclipse.ditto.services.thingsearch.persistence;

import static com.mongodb.client.model.Filters.eq;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;

import com.mongodb.ReadPreference;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Special persistence implementation to do the MongoDB health check.
 */
public class MongoHealthCheck implements PersistenceHealthCheck {

    private static final String COLLECTION_NAME = "test";
    private static final String ID_FIELD = "_id";
    private static final long TIMEOUT = 10000;

    protected final MongoCollection<Document> collection;
    private final Materializer mat;
    private final LoggingAdapter log;

    /**
     * Constructor.
     *
     * @param wrapper the MongoClientWrapper.
     * @param actorSystem the actor system.
     * @param log the logger.
     */
    public MongoHealthCheck(final MongoClientWrapper wrapper, final ActorSystem actorSystem, final LoggingAdapter log) {
        this.collection = wrapper.getDatabase().getCollection(COLLECTION_NAME);
        this.mat = ActorMaterializer.create(actorSystem);
        this.log = log;
    }

    @Override
    public boolean checkHealth() {
        final String id = UUID.randomUUID().toString();
        final Document document = new Document(ID_FIELD, id);

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to
         * retrieve the inserted document from a secondary directly after inserting it on the primary.
         */
        try {
            return Source.fromPublisher(collection.insertOne(document))
                    .log("insertOne", log)
                    .flatMapConcat(s ->
                            Source.fromPublisher(collection.withReadPreference(ReadPreference.primaryPreferred())
                                    .find(eq(ID_FIELD, id))
                            )
                                    .log("find", log)
                                    .filter(r -> r.containsKey(ID_FIELD) && r.getString(ID_FIELD).equals(id))
                                    .flatMapConcat(r ->
                                            Source.fromPublisher(collection.deleteOne(eq(ID_FIELD, id)))
                                                    .log("deleteOne", log)
                                                    .map(dr -> dr.getDeletedCount() > 0)
                                    )
                    ) //
                    .runWith(Sink.head(), mat).toCompletableFuture().get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            log.error(e, "Got exception while checking for health: {}", e.getMessage());
            return false;
        }
    }

}
