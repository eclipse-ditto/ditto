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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.stream.DelayOverflowStrategy;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoSearchUpdaterFlow}
 */
public final class MongoSearchUpdaterFlowTest {

    private final ActorSystem actorSystem =
            ActorSystem.create(UUID.randomUUID().toString(), ConfigFactory.load("test"));

    @After
    public void shutdownActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testThroughput() throws Throwable {
        new TestKit(actorSystem) {{

            // GIVEN: Persistence has high latency

            final MongoDatabase db = Mockito.mock(MongoDatabase.class);
            final MongoCollection<Document> collection = Mockito.mock(MongoCollection.class);
            Mockito.when(db.getCollection(any(), any(Class.class))).thenReturn(collection);
            doAnswer(inv -> {
                final var size = inv.<List<?>>getArgument(0).size();
                final BulkWriteResult result = BulkWriteResult.acknowledged(0, size, 0, size, List.of(), List.of());
                return Source.single(result)
                        .delay(Duration.of(ThreadLocalRandom.current().nextInt(1, 4), ChronoUnit.SECONDS),
                                DelayOverflowStrategy.backpressure())
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), actorSystem);
            }).when(collection).bulkWrite(any(), any(BulkWriteOptions.class));

            final MongoSearchUpdaterFlow flow = MongoSearchUpdaterFlow.of(db,
                    DefaultPersistenceStreamConfig.of(ConfigFactory.empty()), SearchUpdateMapper.get(actorSystem));

            // WHEN: 25 changes each in 3 iterations go through 32 parallel streams with maxBulkSize 4

            final int numberOfIterations = 3;
            final int numberOfChanges = 25;
            final CountDownLatch latch = new CountDownLatch(numberOfChanges * numberOfIterations);

            final List<AbstractWriteModel> writeModels = new ArrayList<>();
            for (int j = 0; j < numberOfIterations; j++) {
                for (int i = 0; i < numberOfChanges; i++) {
                    final Metadata metadata =
                            Metadata.of(ThingId.of("thing:id-" + i), 1L, PolicyId.of("policy:id-" + i), 1L, null);
                    writeModels.add(ThingWriteModel.of(metadata, new BsonDocument()));
                }
            }
            final Thread testRunnerThread = Thread.currentThread();
            final AtomicReference<Throwable> errorBox = new AtomicReference<>();

            final int parallelism = 32;
            final int maxBulkSize = 4;
            final var writeModelSource = Source.from(writeModels)
                    .groupBy(parallelism, w -> Math.floorMod(w.getMetadata().getThingId().hashCode(), parallelism))
                    .map(List::of);
            final var runnableGraph =
                    flow.start(writeModelSource, false)
                            .map(writeResultAndErrors -> {
                                if (writeResultAndErrors.getBulkWriteErrors().isEmpty()) {
                                    writeResultAndErrors.getWriteModels().forEach(writeModel -> {
                                        // a single writeResultAndErrors can contain multiple applied writeModels
                                        latch.countDown();
                                    });
                                }
                                return writeResultAndErrors;
                            })
                            .mergeSubstreams()
                            .toMat(Sink.ignore(), Keep.right());

            runnableGraph.run(actorSystem).exceptionally(error -> {
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

}
