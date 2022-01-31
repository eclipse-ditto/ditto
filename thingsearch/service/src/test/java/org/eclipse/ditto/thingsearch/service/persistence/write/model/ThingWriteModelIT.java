/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.scala.bson.BsonNumber;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Tests MongoDB interaction of {@link ThingWriteModel}.
 */
public final class ThingWriteModelIT extends AbstractThingSearchPersistenceITBase {

    private final DittoLogger logger = DittoLoggerFactory.getLogger(getClass());
    private MongoCollection<BsonDocument> collection;

    @Before
    public void init() {
        collection = mongoClient.getDefaultDatabase()
                .getCollection(PersistenceConstants.THINGS_COLLECTION_NAME, BsonDocument.class);
    }

    @After
    public void dropTestDatabase() {
        Source.fromPublisher(mongoClient.getDefaultDatabase().drop())
                .runWith(Sink.ignore(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    @Test
    public void insert() {
        final var underTest = getWriteModel(1, 1);
        final var result = executeWrite(underTest);
        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getMatchedCount()).isEqualTo(0);
        assertThat(result.getModifiedCount()).isEqualTo(0);
        assertThat(result.getUpserts()).hasSize(1);
    }

    @Test
    public void update() {
        final var previousModel = getWriteModel(1, 1);
        executeWrite(previousModel);
        final var underTest = getWriteModel(2, 2);
        final var result = executeWrite(underTest);
        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getModifiedCount()).isEqualTo(1);
        assertThat(result.getUpserts()).hasSize(0);
    }

    @Test
    public void collision() {
        // GIVEN: Document exists with revision 2
        final var previousModel = getWriteModel(2, 2);
        executeWrite(previousModel);

        // WHEN: 2 updates are executed, one of which does not match the state
        final var collision = getWriteModel(1, 1).asPatchUpdate(0);
        final var update = getWriteModel(3, 3).asPatchUpdate(2);
        final var result = executeWrite(collision, update);

        // THEN: The matched update succeeds; the other update is dropped
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getUpserts()).hasSize(0);
        final var document = Source.fromPublisher(collection.find())
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
        assertThat(document.getInt64("_revision").getValue()).isEqualTo(3);
    }

    private BulkWriteResult executeWrite(final ThingWriteModel... thingWriteModels) {
        final var writeModels = Arrays.stream(thingWriteModels)
                .map(ThingWriteModel::toMongo)
                .collect(Collectors.toList());
        final var result =
                runBlockingWithReturn(Source.fromPublisher(collection.bulkWrite(writeModels)));
        logger.info("BulkWriteResult=<{}>", result);
        return result;
    }

    private static ThingWriteModel getWriteModel(final long sn, final int counterValue) {
        final Metadata metadata = Metadata.of(ThingId.of("thing:id"), sn, null, null, null);
        final BsonDocument thingDocument = new BsonDocument()
                .append("_revision", BsonNumber.apply(sn))
                .append("counter", BsonNumber.apply(counterValue));
        return ThingWriteModel.of(metadata, thingDocument);
    }
}
