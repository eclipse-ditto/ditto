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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.RestartSink;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;

/**
 * Tests {@link MongoSearchUpdaterFlow}.
 */
public final class MongoSearchUpdaterFlowTest {

    @Nullable
    private ActorSystem actorSystem;

    @Before
    public void startActorSystem() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void streamIsRestartableAfterMongoBulkWriteException() throws Exception {

        new TestKit(actorSystem) {{

            // GIVEN: The persistence fails with MongoBulkWriteException on every write
            final BulkWriteResult bulkWriteResult = Mockito.mock(BulkWriteResult.class);
            final MongoBulkWriteException error = Mockito.mock(MongoBulkWriteException.class);
            Mockito.when(error.getWriteResult()).thenReturn(bulkWriteResult);

            final MongoDatabase db = Mockito.mock(MongoDatabase.class);
            final MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
            final Publisher<BulkWriteResult> publisher = s -> s.onError(error);
            Mockito.when(db.getCollection(Mockito.any())).thenReturn(collection);
            Mockito.when(collection.bulkWrite(Mockito.any(), Mockito.any(BulkWriteOptions.class)))
                    .thenReturn(publisher);

            // GIVEN: MongoSearchUpdaterFlow is wrapped inside a RestartSink

            final MongoSearchUpdaterFlow flow = MongoSearchUpdaterFlow.of(db);

            final Sink<Source<AbstractWriteModel, NotUsed>, ?> sink =
                    flow.start(1, 1, Duration.ZERO).to(Sink.ignore());

            final Sink<Source<AbstractWriteModel, NotUsed>, ?> restartSink =
                    RestartSink.withBackoff(Duration.ZERO, Duration.ZERO, 1.0, () -> sink);

            // WHEN: Many changes stream through MongoSearchUpdaterFlow

            final int numberOfChanges = 25;
            final CountDownLatch latch = new CountDownLatch(numberOfChanges);

            final AbstractWriteModel abstractWriteModel = Mockito.mock(AbstractWriteModel.class);
            final WriteModel<Document> mongoWriteModel = new DeleteOneModel<>(new Document());
            Mockito.when(abstractWriteModel.toMongo()).thenReturn(mongoWriteModel);
            Source.repeat(Source.single(abstractWriteModel))
                    .take(numberOfChanges)
                    .buffer(1, OverflowStrategy.backpressure())
                    .map(source -> {
                        latch.countDown();
                        return source;
                    })
                    .runWith(restartSink, ActorMaterializer.create(actorSystem));

            // THEN: MongoSearchUpdaterFlow should keep restarting and keep consuming changes from the stream

            latch.await(5L, TimeUnit.SECONDS);
            assertThat(latch.getCount()).isZero();
        }};
    }

}
