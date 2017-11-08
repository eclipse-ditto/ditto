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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.MongoClientWrapper;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.event.Logging;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Testdata persistence.
 */
public class MongoSearchTestDataPersistence implements TestDataSearchPersistence {

    protected final MongoDatabase database;

    /**
     * Constructor.
     *
     * @param wrapper the MongoClientWrapper.
     */
    public MongoSearchTestDataPersistence(final MongoClientWrapper wrapper) {
        this.database = wrapper.getDatabase();
    }

    @Override
    public void insert(final Document doc, final BiConsumer<Void, Throwable> callback,
            final ActorMaterializer actorMaterializer, final String collectionName) {
        Source.fromPublisher(database.getCollection(collectionName).insertOne(doc)) //
                .log("insertOne") //
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(), Logging
                        .WarningLevel())) //
                .map(s -> {
                    callback.accept(null, null);
                    return s;
                }).runWith(Sink.ignore(), actorMaterializer);
    }

    @Override
    public void delete(final Bson query, final BiConsumer<DeleteResult, Throwable> callback,
            final ActorMaterializer actorMaterializer, final String collectionName) {
        Source.fromPublisher(database.getCollection(collectionName).deleteMany(query)) //
                .log("deleteMany") //
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(), Logging
                        .WarningLevel())) //
                .map(dr -> {
                    callback.accept(dr, null);
                    return dr;
                }).runWith(Sink.ignore(), actorMaterializer);
    }
}
