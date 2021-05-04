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
package org.eclipse.ditto.internal.utils.persistence.mongo.ops;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.BsonUtil;
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

        return doDrop(checkNotNull(collection, "collection"))
                .map(opt -> opt.map(Collections::singletonList).orElse(Collections.emptyList()));
    }

    public static Source<List<Throwable>, NotUsed> deleteByFilter(final MongoCollection<Document> collection,
            final Bson filter) {

        return doDeleteByFilter(checkNotNull(collection, "collection"), checkNotNull(filter, "filter "))
                .map(opt -> opt.map(Collections::singletonList).orElse(Collections.emptyList()));
    }

    private static Source<Optional<Throwable>, NotUsed> doDrop(final MongoCollection<Document> collection) {
        return Source.fromPublisher(collection.drop())
                .map(result -> {
                    LOGGER.debug("Successfully dropped collection <{}>.", collection.getNamespace());
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
                        LOGGER.debug("Deleted <{}> documents from collection <{}>. Filter was <{}>.",
                                result.getDeletedCount(), collection.getNamespace(), filterBsonDoc);
                    }
                    return Optional.<Throwable>empty();
                })
                .recoverWithRetries(RETRY_ATTEMPTS, new PFBuilder<Throwable, Source<Optional<Throwable>, NotUsed>>()
                        .matchAny(throwable -> Source.single(Optional.of(throwable)))
                        .build());
    }

}
