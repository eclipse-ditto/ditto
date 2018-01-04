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
package org.eclipse.ditto.services.utils.persistence.mongo;

import java.time.Duration;
import java.util.UUID;

import org.bson.Document;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatus;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;

import com.mongodb.client.MongoCollection;

import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor for handling calls to the mongodb.
 */
public final class MongoClientActor extends AbstractMongoClientActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "mongoClientActor";

    private final String connectionString;
    private final Duration timeout;

    /**
     * Constructs a {@code MongoClientActor}.
     */
    private MongoClientActor(final String connectionString, final Duration timeout) {
        this.connectionString = connectionString;
        this.timeout = timeout;
    }

    /**
     * Creates Akka configuration object Props for this MongoClientActor.
     *
     * @param connectionString the connection string of the database.
     * @param timeout the timeout for database operations.
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionString, final Duration timeout) {
        return Props.create(MongoClientActor.class, new Creator<MongoClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MongoClientActor create() throws Exception {
                return new MongoClientActor(connectionString, timeout);
            }
        });
    }

    @Override
    protected String getConnectionString() {
        return connectionString;
    }

    @Override
    protected Duration getTimeout() {
        return timeout;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveMongoStatus.class, retrieveStatus -> {
                    final RetrieveMongoStatusResponse retrieveMongoStatusResponse = generateStatusResponse();
                    getSender().tell(retrieveMongoStatusResponse, getSelf());
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private RetrieveMongoStatusResponse generateStatusResponse() {
        final String collectionName = "test";
        final String idField = "_id";

        try {
            final MongoCollection<Document> collection = database.getCollection(collectionName);

            final String id = UUID.randomUUID().toString();
            final Document document = new Document(idField, id);

            collection.insertOne(document);
            final Document deleted = collection.findOneAndDelete(document);

            if (null != deleted && deleted.getString(idField).equals(id)) {
                return new RetrieveMongoStatusResponse(true);
            } else {
                return new RetrieveMongoStatusResponse(false);
            }
        } catch (final Exception e) {
            log.error(e, "Failed to retrieve HealthStatus of persistence. Cause: {}.", e.getMessage());
            return new RetrieveMongoStatusResponse(false, e.getMessage());
        }
    }
}
