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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.bson.Document;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.mongodb.reactivestreams.client.Success;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ManualUpdater}.
 */
public final class ManualUpdaterIT {

    private static final ThingId THING_ID_1 = ThingId.of("x", "1");
    private static final ThingId THING_ID_2 = ThingId.of("x", "2");
    private static final ThingId THING_ID_3 = ThingId.of("x", "3");
    private static final ThingId THING_ID_4 = ThingId.of("x", "4");
    private static final ThingId THING_ID_5 = ThingId.of("x", "5");
    private static final ThingId THING_ID_6 = ThingId.of("x", "6");
    private static final ThingId THING_ID_7 = ThingId.of("x", "7");
    private static final ThingId THING_ID_8 = ThingId.of("x", "8");
    private static final ThingId THING_ID_9 = ThingId.of("x", "9");

    private ActorSystem actorSystem;
    private MongoDbResource mongoResource;
    private DittoMongoClient mongoClient;

    @Before
    public void start() {
        actorSystem = ActorSystem.create();
        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
        mongoClient = newClient();
    }

    @After
    public void stop() {
        // shutdown actor system along with manual updater before closing shared client
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
        if (mongoResource != null) {
            mongoResource.stop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Test
    public void sendAllThingTagsAndEmptyCollection() {
        new TestKit(actorSystem) {{
            actorSystem.actorOf(manualUpdaterProps(mongoClient, getRef()));
            insertDocuments(
                    doc(THING_ID_1.toString(), 1L),
                    doc(THING_ID_2.toString(), 2L),
                    doc(THING_ID_3.toString(), 3L));

            expectMsg(ThingTag.of(THING_ID_1, 1L));
            expectMsg(ThingTag.of(THING_ID_2, 2L));
            expectMsg(ThingTag.of(THING_ID_3, 3L));

            assertThat(
                    Source.fromPublisher(mongoClient.getCollection(ManualUpdater.COLLECTION_NAME).countDocuments())
                            .runWith(Sink.seq(), ActorMaterializer.create(actorSystem))
                            .toCompletableFuture()
                            .join())
                    .isEqualTo(Collections.singletonList(0L));

        }};
    }

    @Test
    public void recoverFromMongoDowntime() {
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
        new TestKit(actorSystem) {{
            actorSystem.actorOf(manualUpdaterProps(mongoClient, getRef()));
            insertDocuments(doc(THING_ID_4.toString(), 4L));
            expectMsg(ThingTag.of(THING_ID_4, 4L));

            final int mongoPort = mongoResource.getPort();
            mongoResource.stop();
            mongoResource = new MongoDbResource("localhost", mongoPort, null);
            mongoResource.start();

            insertDocuments(doc(THING_ID_5.toString(), 5L));
            expectMsg(ThingTag.of(THING_ID_5, 5L));
        }};
    }

    @Test
    public void recoverFromBadDocuments() {
        new TestKit(actorSystem) {{
            actorSystem.actorOf(manualUpdaterProps(mongoClient, getRef()));
            insertDocuments(
                    doc(THING_ID_6.toString(), 6L),
                    doc(null, 7L),
                    doc(THING_ID_8.toString(), null),
                    doc(THING_ID_9.toString(), 9L));
            expectMsg(ThingTag.of(THING_ID_6, 6L));
            expectMsg(ThingTag.of(THING_ID_9, 9L));
        }};
    }

    private void insertDocuments(final Document... docs) {
        final Publisher<Success> insertResultPublisher =
                mongoClient.getCollection(ManualUpdater.COLLECTION_NAME).insertMany(Arrays.asList(docs));

        Source.fromPublisher(insertResultPublisher)
                .runWith(Sink.ignore(), ActorMaterializer.create(actorSystem))
                .toCompletableFuture()
                .join();
    }

    private DittoMongoClient newClient() {
        return MongoClientWrapper.getBuilder()
                .hostnameAndPort(mongoResource.getBindIp(), mongoResource.getPort())
                .defaultDatabaseName("manualUpdaterIT")
                .build();
    }

    private static Document doc(final String id, final Long revision) {
        final Document document = new Document();
        if (id != null) {
            document.append(ManualUpdater.ID_FIELD, id);
        }
        if (revision != null) {
            document.append(ManualUpdater.REVISION, revision);
        }
        return document;
    }

    private static Props manualUpdaterProps(final DittoMongoClient mongoClient, final ActorRef actorRef) {
        return Props.create(ManualUpdater.class, mongoClient.getDefaultDatabase(), actorRef, Duration.ZERO, Duration.ZERO,
                        Duration.ofMillis(50L), Duration.ofSeconds(1L));
    }
}
