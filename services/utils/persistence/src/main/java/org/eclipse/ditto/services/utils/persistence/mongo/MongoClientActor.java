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
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatus;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor for handling calls to the mongodb.
 */
public final class MongoClientActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "mongoClientActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final MongoDatabase database;
    private final MongoClient mongoClient;

    /**
     * Constructs a {@code MongoClientActor}.
     */
    private MongoClientActor(final String connectionString, final Duration timeout) {
        final MongoClientOptions.Builder mongoClientOptionsBuilder =
                MongoClientOptions.builder()
                        .connectTimeout((int) timeout.toMillis())
                        .socketTimeout((int) timeout.toMillis())
                        .serverSelectionTimeout((int) timeout.toMillis());

        final MongoClientURI mongoClientURI = new MongoClientURI(connectionString, mongoClientOptionsBuilder);

        mongoClient = new MongoClient(mongoClientURI);

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to retrieve
         * the inserted document from a secondary directly after inserting it on the primary.
         */
        database =
                mongoClient.getDatabase(mongoClientURI.getDatabase())
                        .withReadPreference(ReadPreference.primaryPreferred());
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
    public void postStop() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }
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
