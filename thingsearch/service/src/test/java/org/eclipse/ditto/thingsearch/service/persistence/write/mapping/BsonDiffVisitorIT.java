/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import java.util.Collections;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.eclipse.ditto.internal.utils.persistence.mongo.BsonUtil;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.mongodb.reactivestreams.client.MongoCollection;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests incremental update.
 */
public class BsonDiffVisitorIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    private static final List<PolicyId> KNOWN_POLICY_IMPORTS = Collections.emptyList();

    private DittoMongoClient client;
    private MongoCollection<Document> collection;
    private ActorSystem system;
    private Enforcer enforcer;

    @Before
    public void init() {
        client = MongoClientWrapper.getBuilder().hostnameAndPort(MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort())
                .defaultDatabaseName("test")
                .build();

        collection = client.getCollection("test");

        system = ActorSystem.create();

        enforcer = PolicyEnforcers.defaultEvaluator(
                PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy", "id"))
                        .forLabel("grant-root")
                        .setSubject("grant:" + "root".repeat(20), SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/", Permission.READ)
                        .forLabel("grant-d")
                        .setSubject("grant:" + "dx.e".repeat(20), SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/d/e", Permission.READ)
                        .forLabel("revoke")
                        .setSubject("revoke:" + "dx.e".repeat(20), SubjectType.GENERATED)
                        .setRevokedPermissions(THING, "/d/e", Permission.READ)
                        .build());
    }

    @After
    public void shutdown() {
        cleanupAction(() -> run(collection.drop()));
        cleanupAction(() -> client.close());
        cleanupAction(() -> TestKit.shutdownActorSystem(system));
    }

    @Test
    public void testAggregationUpdate() {
        final var collection = client.getCollection("test");

        final int maxArraySize = 99;
        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L, PolicyId.of("solar.system:pluto"), 45L, null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing2(); // Thing1 with some fields updated

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than half as large as replacement")
                .isLessThan(nextThingDoc.toString().length() / 2);

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    @Test
    public void testArrayConcat() {
        final var collection = client.getCollection("test");

        final int maxArraySize = 99;
        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L, PolicyId.of("solar.system:pluto"), 45L, null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing3(); // identical to Thing1 with fields rearranged and slightly edited

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        assertThat(prevThingDoc).isNotEqualTo(nextThingDoc);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than 1/10 as large as replacement")
                .isLessThan(nextThingDoc.toString().length() / 10);

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    @Test
    public void testSetEmptyObject() {
        final var collection = client.getCollection("test");

        final int maxArraySize = 99;
        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L, PolicyId.of("solar.system:pluto"), 45L, null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing4(); // identical to Thing1 with an extra fields with emtpy object as value

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        assertThat(prevThingDoc).isNotEqualTo(nextThingDoc);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than 1/8 as large as replacement")
                .isLessThan(nextThingDoc.toString().length() / 8);

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    @Test
    public void testEmptyUpdate() {
        final var collection = client.getCollection("test");

        final int maxArraySize = 99;
        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L, PolicyId.of("solar.system:pluto"), 45L, null, null);

        final JsonObject thing = getThing1();

        final BsonDocument thingDoc =
                EnforcedThingMapper.toBsonDocument(thing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDiff diff = BsonDiff.minusThingDocs(thingDoc, thingDoc);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than 1/20 as large as replacement")
                .isLessThan(thingDoc.toString().length() / 20);

        run(collection.insertOne(toDocument(thingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(thingDoc);
    }

    @Test
    public void testStringExpressionInUpdate() {
        final var collection = client.getCollection("test");

        final int maxArraySize = 99;
        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L, PolicyId.of("solar.system:pluto"), 45L, null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing5(); // Thing1 with string field updated to begin with '$' and end with '.'

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, enforcer, maxArraySize, metadata, KNOWN_POLICY_IMPORTS);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than 1/8 as large as replacement")
                .isLessThan(nextThingDoc.toString().length() / 8);

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    private <T> List<T> run(final Publisher<T> publisher) {
        return Source.fromPublisher(publisher).runWith(Sink.seq(), system).toCompletableFuture().join();
    }

    private static Document toDocument(final BsonDocument bsonDocument) {
        return BsonUtil.getCodecRegistry()
                .get(Document.class)
                .decode(bsonDocument.asBsonReader(), DecoderContext.builder().build());
    }

    private static BsonDocument toBsonDocument(final Document document) {
        return document.toBsonDocument(BsonDocument.class, BsonUtil.getCodecRegistry());
    }

    private static void cleanupAction(final Runnable cleanup) {
        try {
            cleanup.run();
        } catch (final NullPointerException e) {
            // resource not initialized
        }
    }

    private static JsonObject getThing1() {
        return JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ {\"b\": \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"lorem ipsum dolor sit amet\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012\n" +
                "  }\n" +
                "}");
    }

    private static JsonObject getThing2() {
        return JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ false, {\"b\": \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"} ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"h\": \"lorem ipsum dolor sit amet\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 5,\n" +
                "    \"m\": \"MESSAGE\"\n" +
                "  }\n" +
                "}");
    }

    private static JsonObject getThing3() {
        return JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ {\"b\": \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012,\n" +
                "    \"e\": {\n" +
                "      \"f\": \"h\",\n" +
                "      \"h\": \"lorem ipsum dolor sit amet\"\n" +
                "    }\n" +
                "  }\n" +
                "}");
    }

    private static JsonObject getThing4() {
        return JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ {\"b\": \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"lorem ipsum dolor sit amet\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012,\n" +
                "    \"m\": {},\n" +
                "    \"n\": {\"o\":{}}\n" +
                "  }\n" +
                "}");
    }

    private static JsonObject getThing5() {
        return JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ {\"b\": \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"$lorem ipsum dolor sit amet.\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012\n" +
                "  }\n" +
                "}");
    }
}
