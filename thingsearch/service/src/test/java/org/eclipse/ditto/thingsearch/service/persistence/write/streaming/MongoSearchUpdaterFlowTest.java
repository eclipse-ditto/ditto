/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultPersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.DelayOverflowStrategy;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSink;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoSearchUpdaterFlow}
 */
public final class MongoSearchUpdaterFlowTest {

    private final ActorSystem actorSystem =
            ActorSystem.create(UUID.randomUUID().toString(), ConfigFactory.load("test"));
    private final Materializer materializer = SystemMaterializer.get(actorSystem).materializer();

    @After
    public void shutdownActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void streamIsRestartableAfterMongoBulkWriteException() throws Exception {
        final BulkWriteResult bulkWriteResult = Mockito.mock(BulkWriteResult.class);
        final MongoBulkWriteException error = Mockito.mock(MongoBulkWriteException.class);
        Mockito.when(error.getWriteResult()).thenReturn(bulkWriteResult);

        testStreamRestart(() -> error);
    }

    @Test
    public void streamIsRestartableAfterGenericMongoException() throws Exception {
        testStreamRestart(new FakeMongoExceptionSupplier());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testThroughput() throws Throwable {
        new TestKit(actorSystem) {{

            // GIVEN: Persistence has high latency

            final var latency = Duration.ofSeconds(1);
            final MongoDatabase db = Mockito.mock(MongoDatabase.class);
            final MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
            Mockito.when(db.getCollection(any(), any(Class.class))).thenReturn(collection);
            doAnswer(inv -> {
                final var size = inv.<List<?>>getArgument(0).size();
                final BulkWriteResult result = BulkWriteResult.acknowledged(0, size, 0, size, List.of(), List.of());
                return Source.single(result)
                        .delay(latency, DelayOverflowStrategy.backpressure())
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), actorSystem);
            }).when(collection).bulkWrite(any(), any(BulkWriteOptions.class));

            final MongoSearchUpdaterFlow flow = MongoSearchUpdaterFlow.of(db,
                    DefaultPersistenceStreamConfig.of(ConfigFactory.empty()), SearchUpdateMapper.get(actorSystem));

            // WHEN: 25 updates go through 32 parallel streams

            final int numberOfChanges = 25;
            final CountDownLatch latch = new CountDownLatch(numberOfChanges);

            final Sink<Source<AbstractWriteModel, NotUsed>, CompletionStage<Done>> sink =
                    flow.start(false, 32, 1)
                            .map(writeResultAndErrors -> {
                                latch.countDown();
                                return writeResultAndErrors;
                            })
                            .toMat(Sink.ignore(), Keep.right());

            final Metadata metadata = Metadata.of(ThingId.of("thing:id"), 1L, PolicyId.of("policy:id"), 1L, null);
            final AbstractWriteModel abstractWriteModel = ThingWriteModel.of(metadata, new BsonDocument());
            final Thread testRunnerThread = Thread.currentThread();
            final AtomicReference<Throwable> errorBox = new AtomicReference<>();
            Source.repeat(Source.single(abstractWriteModel))
                    .take(numberOfChanges)
                    .runWith(Objects.requireNonNull(sink), actorSystem)
                    .exceptionally(error -> {
                        errorBox.set(error);
                        testRunnerThread.interrupt();
                        return null;
                    });

            // THEN: updates complete quickly

            try {
                final var result = latch.await(20L, TimeUnit.SECONDS);
                assertThat(latch.getCount()).isZero();
                assertThat(result).isTrue();
            } catch (final InterruptedException e) {
                if (errorBox.get() != null) {
                    throw errorBox.get();
                } else {
                    throw e;
                }
            }
        }};
    }

    @SuppressWarnings("unchecked")
    private void testStreamRestart(final Supplier<Throwable> errorSupplier) throws Exception {

        new TestKit(actorSystem) {{

            // GIVEN: The persistence fails with an error on every write

            final MongoDatabase db = Mockito.mock(MongoDatabase.class);
            final MongoCollection<BsonDocument> collection = Mockito.mock(MongoCollection.class);
            final Publisher<BulkWriteResult> publisher = s -> s.onError(errorSupplier.get());
            Mockito.when(db.getCollection(any(), any(Class.class))).thenReturn(collection);
            Mockito.when(collection.bulkWrite(any(), any(BulkWriteOptions.class)))
                    .thenReturn(publisher);

            // GIVEN: MongoSearchUpdaterFlow is wrapped inside a RestartSink

            final MongoSearchUpdaterFlow flow = MongoSearchUpdaterFlow.of(db,
                    DefaultPersistenceStreamConfig.of(ConfigFactory.empty()),
                    SearchUpdateMapper.get(actorSystem));

            final Sink<Source<AbstractWriteModel, NotUsed>, ?> sink =
                    flow.start(false, 1, 1).to(Sink.ignore());

            final Sink<Source<AbstractWriteModel, NotUsed>, ?> restartSink =
                    RestartSink.withBackoff(Duration.ZERO, Duration.ZERO, 1.0, () -> sink);

            // WHEN: Many changes stream through MongoSearchUpdaterFlow

            final int numberOfChanges = 25;
            final CountDownLatch latch = new CountDownLatch(numberOfChanges);

            final AbstractWriteModel abstractWriteModel = Mockito.mock(AbstractWriteModel.class);
            final WriteModel<BsonDocument> mongoWriteModel = new DeleteOneModel<>(new Document());
            Mockito.when(abstractWriteModel.toMongo()).thenReturn(mongoWriteModel);
            Source.repeat(Source.single(abstractWriteModel))
                    .take(numberOfChanges)
                    .buffer(1, OverflowStrategy.backpressure())
                    .map(source -> {
                        latch.countDown();
                        return source;
                    })
                    .runWith(restartSink, actorSystem);

            // THEN: MongoSearchUpdaterFlow should keep restarting and keep consuming changes from the stream

            latch.await(5L, TimeUnit.SECONDS);
            assertThat(latch.getCount()).isZero();
        }};
    }

    private static final class FakeMongoException extends MongoException {

        private FakeMongoException(final int i) {
            super("Fake MongoException #" + i);
        }
    }

    private static final class FakeMongoExceptionSupplier implements Supplier<Throwable> {

        private static final AtomicInteger i = new AtomicInteger();

        @Override
        public MongoException get() {
            return new FakeMongoException(i.incrementAndGet());
        }
    }

}
