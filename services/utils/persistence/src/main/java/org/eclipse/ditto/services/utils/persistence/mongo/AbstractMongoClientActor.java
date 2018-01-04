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

import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoDatabase;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Actor with a connection to MongoDB with messaging behavior defined in subclasses.
 */
public abstract class AbstractMongoClientActor extends AbstractActor {

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    protected MongoDatabase database;
    protected MongoClient mongoClient;

    protected abstract String getConnectionString();

    protected abstract Duration getTimeout();

    @Override
    public void preStart() {
        final Duration timeout = getTimeout();
        final MongoClientOptions.Builder mongoClientOptionsBuilder =
                MongoClientOptions.builder()
                        .connectTimeout((int) timeout.toMillis())
                        .socketTimeout((int) timeout.toMillis())
                        .serverSelectionTimeout((int) timeout.toMillis());

        final MongoClientURI mongoClientURI = new MongoClientURI(getConnectionString(), mongoClientOptionsBuilder);

        mongoClient = new MongoClient(mongoClientURI);

        /*
         * It's important to have the read preferences to primary preferred because the replication is to slow to retrieve
         * the inserted document from a secondary directly after inserting it on the primary.
         */
        database = mongoClient.getDatabase(mongoClientURI.getDatabase())
                .withReadPreference(ReadPreference.primaryPreferred());
    }

    @Override
    public void postStop() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
