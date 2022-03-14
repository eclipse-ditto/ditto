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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultUpdaterConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.ChangeQueueActor;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdateMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdaterStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.mongodb.client.model.Filters;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.RepointableActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Integration Test for search consistency with conflicting search updates.
 */
public final class SearchConsistencyIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();

    //Arbitrary
    private static final long INITIAL_REVISION = 1L;
    private static final String NAMESPACE = "abc";
    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "myId");
    private static final PolicyId POLICY_ID = PolicyId.of(THING_ID);
    private static final Policy POLICY = Policy.newBuilder(POLICY_ID)
            .setRevision(INITIAL_REVISION)
            .set(PolicyEntry.newInstance("test", List.of(Subject.newInstance(SubjectIssuer.INTEGRATION, "test")),
                    List.of(Resource.newInstance(ResourceKey.newInstance("thing:/"),
                            EffectedPermissions.newInstance(List.of("WRITE", "READ"), Collections.emptyList())))))
            .build();
    private static final long REVISION = 2L;
    private static final Thing THING = ThingsModelFactory.newThingBuilder()
            .setRevision(INITIAL_REVISION)
            .setAttribute(JsonPointer.of("number"), JsonValue.of(22))
            .setAttribute(JsonPointer.of("char"), JsonValue.of("abc"))
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .build();
    private static final long EXPECTED_NUMBER = 32;
    private static final String EXPECTED_CHAR = "cba";
    private static final Instant MODIFIED = Instant.now();

    private ActorSystem actorSystem;
    private ActorRef updater1;
    private SearchUpdaterStream updaterStream1;
    private ActorRef updater2;
    private SearchUpdaterStream updaterStream2;
    private DittoMongoClient mongoClient;

    @Before()
    public void setup() {
        mongoClient = provideClientWrapper();
        final var config = ConfigFactory.load("consistency-it");
        final var updaterConfig = DefaultUpdaterConfig.of(ConfigFactory.load("updater-test"));
        actorSystem = ActorSystem.create(getClass().getSimpleName(), config);
        final var pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        final ActorRef changeQueue1 = createChangeQueue(actorSystem);
        final ActorRef changeQueue2 = createChangeQueue(actorSystem);
        updater1 = createSearchUpdaterShardRegion(changeQueue1, pubSubMediator, updaterConfig);
        updater2 = createSearchUpdaterShardRegion(changeQueue2, pubSubMediator, updaterConfig);
        updaterStream1 = createSearchUpdaterStream(updaterConfig, updater1, changeQueue1);
        updaterStream2 = createSearchUpdaterStream(updaterConfig, updater2, changeQueue2);

    }

    @After()
    public void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + MONGO_RESOURCE.getBindIp() + ":" + MONGO_RESOURCE.getPort() + "/testSearchDB")
                .build();
    }

    private static ActorRef createChangeQueue(final ActorRefFactory actorSystem) {
        final var changeQueueProps = ChangeQueueActor.props();
        return actorSystem.actorOf(changeQueueProps);
    }

    private SearchUpdaterStream createSearchUpdaterStream(final UpdaterConfig updaterConfig,
            final ActorRef updaterShard, final ActorRef changeQueue) {

        final var thingsShard = actorSystem.actorOf(ThingShardMock.props());
        final var policiesShard = actorSystem.actorOf(PolicyShardMock.props());
        final var db = mongoClient.getDefaultDatabase();
        final var blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final var updateMapper = SearchUpdateMapper.get(actorSystem);
        return SearchUpdaterStream.of(updaterConfig, actorSystem, thingsShard, policiesShard, updaterShard, changeQueue,
                db, blockedNamespaces, updateMapper);
    }

    private ActorRef createSearchUpdaterShardRegion(final ActorRef changeQueue,
            final ActorRef pubSub,
            final UpdaterConfig config) {

        ChangeQueueActor.createSource(changeQueue, true, java.time.Duration.ofSeconds(1));
        final var updaterProps = ThingUpdater.props(pubSub, changeQueue, config);
        final var updater = actorSystem.actorOf(updaterProps, THING_ID.toString() + Math.random());

        final var shardMockProps = UpdaterShardMock.props(updater);
        return actorSystem.actorOf(shardMockProps);
    }

    @Test
    public void assertConsistencyInCaseOfConflict() {
        final var probe = TestProbe.apply(actorSystem);
        updaterStream1.start(actorSystem);
        updaterStream2.start(actorSystem);
        probe.send(updater1, getRecoveryWriteModel());
        probe.send(updater2, getRecoveryWriteModel());
        probe.send(updater1, getThingEvent(ThingsModelFactory.newAttributes(JsonFactory
                .newObjectBuilder().set("char", EXPECTED_CHAR).set("number", EXPECTED_NUMBER).build())));
        probe.send(updater2, getThingEvent(ThingsModelFactory.newAttributes(JsonFactory
                .newObjectBuilder().set("number", EXPECTED_NUMBER).set("char", EXPECTED_CHAR).build())));
        assertSearchPersisted(Duration.ofSeconds(5));
    }

    private static ThingEvent<?> getThingEvent(final Attributes attributes) {
        final var dittoHeaders = getDittoHeadersWithSearchPersistedAck();
        return AttributesModified.of(THING_ID, attributes, REVISION, MODIFIED, dittoHeaders, null);
    }

    private static DittoHeaders getDittoHeadersWithSearchPersistedAck() {
        return DittoHeaders.newBuilder()
                .acknowledgementRequest(
                        AcknowledgementRequest.of(
                                AcknowledgementLabel.of(DittoAcknowledgementLabel.SEARCH_PERSISTED)))
                .build();
    }

    private void assertSearchPersisted(final Duration timeout) {
        final var materializer = Materializer.createMaterializer(actorSystem);
        Awaitility.await()
                .atMost(timeout)
                .until(() -> {
                    final var persistedDocuments =
                            Source.fromPublisher(mongoClient.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME)
                                    .find(Filters.eq(PersistenceConstants.FIELD_ID, THING_ID.toString())))
                                    .runWith(Sink.seq(), materializer)
                                    .toCompletableFuture()
                                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);

                    return persistedDocuments.size() == 1 &&
                            persistedDocuments.get(0).toString().equals(getExpectedDocument().toString());
                });
    }

    private static AbstractWriteModel getRecoveryWriteModel() {
        final Metadata metadata = Metadata.of(THING_ID, INITIAL_REVISION, POLICY_ID, INITIAL_REVISION, null);
        final BsonDocument thingDocument = new BsonDocument()
                .append(PersistenceConstants.FIELD_ID, new BsonString(THING_ID.toString()))
                .append(PersistenceConstants.FIELD_REVISION, new BsonInt64(INITIAL_REVISION))
                .append(PersistenceConstants.FIELD_POLICY_ID, new BsonString(POLICY_ID.toString()))
                .append(PersistenceConstants.FIELD_POLICY_REVISION, new BsonInt64(INITIAL_REVISION))
                .append(PersistenceConstants.FIELD_NAMESPACE, new BsonString(THING_ID.getNamespace()))
                .append(PersistenceConstants.FIELD_GLOBAL_READ, new BsonString("integration:test"))
                .append(PersistenceConstants.FIELD_SORTING, new BsonDocument())
                .append(PersistenceConstants.FIELD_INTERNAL, new BsonArray());
        return ThingWriteModel.of(metadata, thingDocument);
    }

    private static Document getExpectedDocument() {
        return new Document()
                .append(PersistenceConstants.FIELD_ID, THING_ID.toString())
                .append(PersistenceConstants.FIELD_REVISION, REVISION)
                .append(PersistenceConstants.FIELD_NAMESPACE, THING_ID.getNamespace())
                .append(PersistenceConstants.FIELD_GLOBAL_READ, List.of("integration:test"))
                .append(PersistenceConstants.FIELD_POLICY_ID, POLICY_ID.toString())
                .append(PersistenceConstants.FIELD_POLICY_REVISION, INITIAL_REVISION)
                .append(PersistenceConstants.FIELD_SORTING, new Document()
                        .append(PersistenceConstants.FIELD_REVISION, REVISION)
                        .append(PersistenceConstants.FIELD_NAMESPACE, THING_ID.getNamespace())
                        .append("thingId", THING_ID.toString())
                        .append("policyId", POLICY_ID.toString())
                        .append("attributes", new Document()
                                .append("number", EXPECTED_NUMBER)
                                .append("char", EXPECTED_CHAR))
                        .append("_modified", MODIFIED))
                .append(PersistenceConstants.FIELD_INTERNAL, List.of(
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/" + PersistenceConstants.FIELD_REVISION)
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, REVISION)
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/" + PersistenceConstants.FIELD_NAMESPACE)
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, THING_ID.getNamespace())
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/thingId")
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, THING_ID.toString())
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/policyId")
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, POLICY_ID.toString())
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/attributes/number")
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, EXPECTED_NUMBER)
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/attributes/char")
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, EXPECTED_CHAR)
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList()),
                        new Document()
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        "/_modified")
                                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, MODIFIED)
                                .append(PersistenceConstants.FIELD_GRANTED,
                                        List.of("integration:test"))
                                .append(PersistenceConstants.FIELD_REVOKED, Collections.emptyList())));
    }

    private static final class UpdaterShardMock extends AbstractActor {

        private final ActorRef updater;

        private UpdaterShardMock(final RepointableActorRef updater) {
            this.updater = updater;
        }

        private static Props props(final ActorRef updater) {
            return Props.create(UpdaterShardMock.class, updater);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(ThingEvent.class, event -> updater.tell(event, getSender()))
                    .match(AbstractWriteModel.class, writeModel -> updater.tell(writeModel, getSender()))
                    .match(ShardedMessageEnvelope.class,
                            message -> updater.tell(
                                    UpdateThingResponse.fromJson(message.getMessage(), message.getDittoHeaders()),
                                    getSender()))
                    .build();
        }

    }

    private static final class ThingShardMock extends AbstractActor {


        private ThingShardMock() {
        }

        private static Props props() {
            return Props.create(ThingShardMock.class);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(SudoRetrieveThing.class, this::sendSudoRetrieveThingResponse)
                    .build();
        }

        private void sendSudoRetrieveThingResponse(final WithDittoHeaders srt) {
            final var thingJson = THING.toJson(FieldType.all());
            final var response = SudoRetrieveThingResponse.of(thingJson, srt.getDittoHeaders());
            getSender().tell(response, getSelf());
        }

    }

    private static final class PolicyShardMock extends AbstractActor {


        private PolicyShardMock() {
        }

        private static Props props() {
            return Props.create(PolicyShardMock.class);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(SudoRetrievePolicy.class, this::sendSudoRetrievePolicyResponse)
                    .build();
        }

        private void sendSudoRetrievePolicyResponse(final WithDittoHeaders srt) {
            final var response = SudoRetrievePolicyResponse.of(POLICY_ID, POLICY, srt.getDittoHeaders());
            getSender().tell(response, getSelf());
        }

    }


}
