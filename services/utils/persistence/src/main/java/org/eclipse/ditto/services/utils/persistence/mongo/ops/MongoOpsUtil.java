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
package org.eclipse.ditto.services.utils.persistence.mongo.ops;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;

/**
 * Util for Mongo persistence ops.
 */
public final class MongoOpsUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoOpsUtil.class);
    private static final int RETRY_ATTEMPTS = 0;

    private MongoOpsUtil() {
        throw new AssertionError();
    }

    public static Source<List<Throwable>, NotUsed> drop(final MongoCollection<Document> collection) {
        requireNonNull(collection);

        return doDrop(collection)
                .map(opt -> opt.isPresent() ? Collections.singletonList(opt.get()) : Collections.emptyList());
    }

    public static Source<List<Throwable>, NotUsed> deleteByFilter(final MongoCollection<Document> collection,
            final Bson filter) {

        requireNonNull(collection);
        requireNonNull(filter);

        return doDeleteByFilter(collection, filter)
                .map(opt -> opt.isPresent() ? Collections.singletonList(opt.get()) : Collections.emptyList());
    }

    private static Source<Optional<Throwable>, NotUsed> doDrop(final MongoCollection<Document> collection) {
        return Source.fromPublisher(collection.drop())
                .map(result -> {
                    LOGGER.debug("Successfully dropped collection <{}>.", collection);
                    return Optional.<Throwable>empty();
                })
                .recoverWithRetries(RETRY_ATTEMPTS, new PFBuilder<Throwable, Source<Optional<Throwable>, NotUsed>>()
                        .matchAny(throwable -> Source.single(Optional.of(throwable)))
                        .build());
    }

    private static Source<Optional<Throwable>, NotUsed> doDeleteByFilter(final MongoCollection<Document> collection,
            final Bson filter) {
        // https://stackoverflow.com/a/33164008
        // claims unordered bulk ops halve MongoDB load
        final List<WriteModel<Document>> writeModel =
                Collections.singletonList(new DeleteManyModel<>(filter));
        final BulkWriteOptions options = new BulkWriteOptions().ordered(false);
        return Source.fromPublisher(collection.bulkWrite(writeModel, options))
                .map(result -> {
                    if (LOGGER.isDebugEnabled()) {
                        // in contrast to Bson, BsonDocument has meaningful toString()
                        final BsonDocument filterBsonDoc = BsonUtil.toBsonDocument(filter);
                        LOGGER.debug("Deleted <{}> documents. Filter was <{}>.",
                                result.getDeletedCount(), filterBsonDoc);
                    }
                    return Optional.<Throwable>empty();
                })
                .recoverWithRetries(RETRY_ATTEMPTS, new PFBuilder<Throwable, Source<Optional<Throwable>, NotUsed>>()
                        .matchAny(throwable -> Source.single(Optional.of(throwable)))
                        .build());
    }

}
