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
package org.eclipse.services.thingsearch.persistence.read;

import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.async.SingleResultCallback;
import com.mongodb.client.result.DeleteResult;

import akka.stream.ActorMaterializer;

/**
 * Interface for testdata generation on mongodb.
 */
public interface TestDataSearchPersistence {

    /**
     * Inserts a passed in {@link Document}. Afterwards there is called the passed in {@link SingleResultCallback} with
     * the result of the insert operation.
     *
     * @param doc the document to insert
     * @param callback the callback which is called as soon as the result is available
     */
    void insert(Document doc, BiConsumer<Void, Throwable> callback, final ActorMaterializer actorMaterializer,
            String collectionName);


    /**
     * Deletes all matching {@link Document}s.
     *
     * @param query the query for deletion
     * @param callback the callback which is called as soon as the result is available
     */
    void delete(Bson query, BiConsumer<DeleteResult, Throwable> callback, final ActorMaterializer actorMaterializer,
            String collectionName);

}
