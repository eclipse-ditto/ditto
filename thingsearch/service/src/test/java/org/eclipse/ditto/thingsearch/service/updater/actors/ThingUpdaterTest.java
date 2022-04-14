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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClientSettings;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThing;
import org.eclipse.ditto.thingsearch.service.persistence.BulkWriteComplete;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.starter.actors.MongoClientExtension;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mongodb.scala.bson.BsonNumber;
import org.reactivestreams.Subscriber;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.testkit.TestPublisher;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ThingUpdater}.
 */
public final class ThingUpdaterTest {

    private static final String NAMESPACE = "abc";

    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "myId");
    private static final PolicyId POLICY_ID = PolicyId.of(THING_ID);

    private static final long REVISION = 1L;

    private final Thing thing = ThingsModelFactory.newThingBuilder()
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .setRevision(REVISION).build();

    private ActorSystem actorSystem;
    private TestProbe pubSubTestProbe;
    private TestProbe changeQueueTestProbe;

    private MongoClientExtension mongoClientExtension;
    private DittoMongoClient dittoMongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> searchCollection;
    private FindPublisher<Document> findPublisher;

    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        startActorSystem(config);
        mongoClientExtension = Mockito.mock(MongoClientExtension.class);
        dittoMongoClient = Mockito.mock(DittoMongoClient.class);
        Mockito.when(mongoClientExtension.getSearchClient()).thenReturn(dittoMongoClient);
        mongoDatabase = Mockito.mock(MongoDatabase.class);
        Mockito.when(dittoMongoClient.getDefaultDatabase()).thenReturn(mongoDatabase);
        Mockito.when(dittoMongoClient.getDittoSettings()).thenReturn(DittoMongoClientSettings.getBuilder().build());
        searchCollection = Mockito.mock(MongoCollection.class);
        Mockito.when(mongoDatabase.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME))
                .thenReturn(searchCollection);
        findPublisher = Mockito.mock(FindPublisher.class);
        Mockito.when(searchCollection.find(Filters.eq(PersistenceConstants.FIELD_ID, THING_ID.toString())))
                .thenReturn(findPublisher);
        Mockito.when(findPublisher.limit(anyInt())).thenReturn(findPublisher);
        Mockito.when(findPublisher.skip(anyInt())).thenReturn(findPublisher);
        Mockito.when(findPublisher.sort(any())).thenReturn(findPublisher);
    }

    @After
    public void tearDownBase() {
        shutdownActorSystem();
    }

    private void startActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubTestProbe = TestProbe.apply(actorSystem);
        changeQueueTestProbe = TestProbe.apply(actorSystem);
    }

    private void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        pubSubTestProbe = null;
    }

    @Test
    public void createThing() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final ThingCreated thingCreated = ThingCreated.of(thing, 1L, Instant.now(), dittoHeaders, null);
                underTest.tell(thingCreated, getRef());

                final Metadata metadata = changeQueueTestProbe.expectMsgClass(Metadata.class);
                Assertions.assertThat((CharSequence) metadata.getThingId()).isEqualTo(THING_ID);
                Assertions.assertThat(metadata.getThingRevision()).isEqualTo(1L);
                Assertions.assertThat(metadata.getPolicyId()).isEmpty();
                Assertions.assertThat(metadata.getPolicyRevision()).contains(-1L);
            }
        };
    }

    @Test
    public void policyReferenceTagTriggersPolicyUpdate() {
        final long newPolicyRevision = REVISION + 2L;
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final PolicyId policyId = PolicyId.of(THING_ID);
                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, newPolicyRevision)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId, newPolicyRevision, null)
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, REVISION)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
            }
        };
    }

    @Test
    public void policyIdChangeTriggersSync() {
        final PolicyId policyId1 = PolicyId.of("policy", "1");
        final PolicyId policyId2 = PolicyId.of("policy", "2");

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // establish policy ID
                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId1, 99L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId1, 99L, null)
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId2, 9L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId2, 9L, null)
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));
            }
        };
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = watch(createThingUpdaterActor());

                final DistributedPubSubMediator.Subscribe subscribe =
                        DistPubSubAccess.subscribe(Shutdown.TYPE, underTest);
                pubSubTestProbe.expectMsg(subscribe);
                pubSubTestProbe.reply(new DistributedPubSubMediator.SubscribeAck(subscribe));

                underTest.tell(Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(NAMESPACE),
                        DittoHeaders.empty()), pubSubTestProbe.ref());
                expectTerminated(underTest);
            }
        };

    }

    @Test
    public void recoverLastWriteModel() {
        new TestKit(actorSystem) {{
            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            Duration.ZERO, 0.0, mongoClientExtension, false, true,
                            writeModel -> {}));
            final var underTest = childActorOf(props, THING_ID.toString());

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1234))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, 1234, null, null, null), document);

            // GIVEN: updater is recovered with a write model
            underTest.tell(writeModel, ActorRef.noSender());

            // WHEN: updater is requested to compute incremental update against the same write model
            underTest.tell(UpdateThing.of(THING_ID, UpdateReason.UNKNOWN, DittoHeaders.empty()), getRef());
            underTest.tell(writeModel, getRef());

            // THEN: expect no update.
            expectMsg(Done.done());
        }};
    }

    @Test
    public void refuseToPerformOutOfOrderUpdate() {
        new TestKit(actorSystem) {{
            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            Duration.ZERO, 0.0, mongoClientExtension, false, true,
                            writeModel -> {}));
            final var underTest = childActorOf(props, THING_ID.toString());

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1234))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, 1234, null, null, null), document);

            // GIVEN: updater is recovered with a write model
            underTest.tell(writeModel, ActorRef.noSender());

            // WHEN: updater is requested to compute incremental update of an older write model
            underTest.tell(UpdateThing.of(THING_ID, UpdateReason.UNKNOWN, DittoHeaders.empty()), getRef());
            final var olderWriteModel =
                    ThingWriteModel.of(Metadata.of(THING_ID, 1233, null, null, null), document);
            underTest.tell(olderWriteModel, getRef());

            // THEN: expect no update.
            expectMsg(Done.done());
        }};
    }

    @Test
    public void forceUpdateOnSameSequenceNumber() {
        new TestKit(actorSystem) {{
            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            Duration.ZERO, 0.0, mongoClientExtension, false, true,
                            writeModel -> {}));
            final var underTest = childActorOf(props, THING_ID.toString());

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1234))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, 1234, null, null, null), document);

            // GIVEN: updater is recovered with a write model
            underTest.tell(writeModel, ActorRef.noSender());

            // WHEN: updater is requested to compute incremental update of an older write model
            final var forceUpdateHeaders = DittoHeaders.newBuilder().putHeader("force-update", "true").build();
            underTest.tell(UpdateThing.of(THING_ID, UpdateReason.UNKNOWN, forceUpdateHeaders), getRef());
            final var olderWriteModel = ThingDeleteModel.of(Metadata.of(THING_ID, 1234, null, null, null));
            underTest.tell(olderWriteModel, getRef());

            // THEN: expect an update.
            final var mongoWriteModel = expectMsgClass(MongoWriteModel.class);
            assertThat(mongoWriteModel.getDitto()).isEqualTo(olderWriteModel);
        }};
    }

    @Test
    public void forceUpdateAfterInitialStart() throws InterruptedException {
        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.of(THING_ID);

            final TestPublisher.Probe<Object> probe = TestPublisher.probe(1, actorSystem);
            doAnswer(invocation -> {
                probe.subscribe(invocation.getArgument(0, Subscriber.class));
                return null;
            }).when(findPublisher).subscribe(any());

            DocumentCodec codec = new DocumentCodec();
            DecoderContext decoderContext = DecoderContext.builder().build();
            final BsonDocument existingIndexBsonDocument = new BsonDocument()
                    .append(PersistenceConstants.FIELD_ID, new BsonString(THING_ID.toString()))
                    .append(PersistenceConstants.FIELD_REVISION, new BsonInt64(1234L))
                    .append(PersistenceConstants.FIELD_POLICY_ID, new BsonString(THING_ID.toString()))
                    .append(PersistenceConstants.FIELD_POLICY_REVISION, new BsonInt64(1L))
                    .append(PersistenceConstants.FIELD_NAMESPACE, new BsonString(THING_ID.getNamespace()))
                    .append(PersistenceConstants.FIELD_GLOBAL_READ, new BsonString("pre:ditto"))
                    .append(PersistenceConstants.FIELD_SORTING, new BsonDocument().append("Lorem ipsum",
                            new BsonString("Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")))
                    .append(PersistenceConstants.FIELD_INTERNAL, new BsonArray());

            final CountDownLatch recoveryCompleteLatch = new CountDownLatch(1);
            final Consumer<AbstractWriteModel> recoveryCompleteConsumer = writeModel -> {
                assertThat(writeModel).isEqualTo(
                        ThingWriteModel.of(
                                Metadata.of(THING_ID, 1234L, PolicyId.of(THING_ID), 1L, null),
                                existingIndexBsonDocument));
                recoveryCompleteLatch.countDown();
            };
            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            Duration.ZERO, 0.0, mongoClientExtension, true, true,
                            recoveryCompleteConsumer));
            final var underTest = childActorOf(props, THING_ID.toString());

            final long request = probe.expectRequest();
            final var existingIndexDocument = codec.decode(new BsonDocumentReader(existingIndexBsonDocument),
                    decoderContext);
            probe.sendNext(existingIndexDocument);

            // wait until Actor was recovered:
            assertThat(recoveryCompleteLatch.await(5L, TimeUnit.SECONDS)).isTrue();

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1235))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, 1235L, policyId, 1L, null), document);

            // WHEN: updater is requested to compute incremental update against the next update
            underTest.tell(UpdateThing.of(THING_ID, UpdateReason.UNKNOWN, DittoHeaders.empty()), getRef());
            underTest.tell(writeModel, getRef());

            final MongoWriteModel mongoWriteModel = expectMsgClass(MongoWriteModel.class);
            final UpdateOneModel<?> updateOneModel = (UpdateOneModel<?>) mongoWriteModel.getBson();
            Assertions.assertThat(updateOneModel.getFilter()).isEqualTo(Filters.and(
                    Filters.eq(PersistenceConstants.FIELD_ID, new BsonString(THING_ID.toString())),
                    Filters.eq(PersistenceConstants.FIELD_REVISION, BsonNumber.apply(1234L))
            ));

            // THEN: an update is triggered
            changeQueueTestProbe.expectMsgClass(Metadata.class);

            // WHEN: updater is requested to compute incremental update against the same write model
            underTest.tell(BulkWriteComplete.of("correlation-id"), getRef());
            underTest.tell(ThingUpdater.FORCE_UPDATE_AFTER_START, getRef());
            underTest.tell(UpdateThing.of(THING_ID, UpdateReason.UNKNOWN, DittoHeaders.empty()), getRef());
            underTest.tell(writeModel, getRef());

            // THEN: expect full forced update
            final MongoWriteModel mongoWriteModel2 = expectMsgClass(MongoWriteModel.class);
            final ReplaceOneModel<?> replaceOneModel = (ReplaceOneModel<?>) mongoWriteModel2.getBson();
            Assertions.assertThat(replaceOneModel.getReplacement()).isEqualTo(document);
        }};
    }

    private ActorRef createThingUpdaterActor() {
        final Props props = Props.create(ThingUpdater.class,
                () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0, Duration.ZERO,
                        0.0, mongoClientExtension, false, false,
                        writeModel -> {}));
        return actorSystem.actorOf(props, THING_ID.toString());
    }

}
