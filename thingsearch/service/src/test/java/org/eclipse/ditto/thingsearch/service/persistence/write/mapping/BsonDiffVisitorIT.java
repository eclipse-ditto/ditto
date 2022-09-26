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

import java.util.List;
import java.util.Set;

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
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.After;
import org.junit.Before;
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
abstract class BsonDiffVisitorIT {

    private DittoMongoClient client;
    private MongoCollection<Document> collection;
    private ActorSystem system;
    private Policy policy;
    private Policy policy2;

    protected abstract MongoDbResource getMongoDbResource();

    @Before
    public void init() {
        client = MongoClientWrapper.getBuilder()
                .hostnameAndPort(getMongoDbResource().getBindIp(), getMongoDbResource().getPort())
                .defaultDatabaseName("test")
                .build();

        collection = client.getCollection("test");

        system = ActorSystem.create();

        final int n = 20;
        policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy", "id"))
                .forLabel("grant-root")
                .setSubject("grant:" + "root".repeat(n), SubjectType.GENERATED)
                .setGrantedPermissions(THING, "/", Permission.READ)
                .forLabel("grant-d")
                .setSubject("grant:" + "dx.e".repeat(n), SubjectType.GENERATED)
                .setGrantedPermissions(THING, "/d/e", Permission.READ)
                .forLabel("revoke")
                .setSubject("revoke:" + "dx.e".repeat(n), SubjectType.GENERATED)
                .setRevokedPermissions(THING, "/d/e", Permission.READ)
                .build();

        policy2 = policy.toBuilder()
                .forLabel("additional")
                .setSubject("revoke:" + "dx.e".repeat(n), SubjectType.GENERATED)
                .setGrantedPermissions(THING, "/d/e/f", Permission.READ)
                .setGrantedPermissions(THING, "/d/e/h", Permission.READ)
                .build();
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

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing2(); // Thing1 with some fields updated

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy, metadata);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, 13);

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
    public void testEnforcerChange() {
        final var collection = client.getCollection("test");

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing1();

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy2, metadata);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, 13);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than replacement")
                .isLessThan(nextThingDoc.toString().length() / 2);

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    @Test
    public void testEnforcerAndThingChange() {
        final var collection = client.getCollection("test");

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing2();

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy2, metadata);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, 13);

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than half as large as replacement")
                .isLessThan((int) (nextThingDoc.toString().length() * 0.75));

        run(collection.insertOne(toDocument(prevThingDoc)));
        run(collection.updateOne(new Document(), updateDoc));

        final BsonDocument incrementalUpdateResult = toBsonDocument(run(collection.find()).get(0));

        assertThat(incrementalUpdateResult)
                .describedAs("Incremental update result")
                .isEqualTo(nextThingDoc);
    }

    @Test
    public void testArrayDiffPropertyDeleted() {
        final var collection = client.getCollection("test");

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing6(); // identical to Thing1 with property deleted

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy, metadata);

        assertThat(prevThingDoc).isNotEqualTo(nextThingDoc);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, client.getMaxWireVersion());

        final List<BsonDocument> updateDoc = diff.consumeAndExport();

        assertThat(updateDoc.toString().length())
                .describedAs("Incremental update should be less than 1/5 as large as replacement")
                .isLessThan(nextThingDoc.toString().length() / 5);

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

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing4(); // identical to Thing1 with an extra fields with empty object as value

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy, metadata);

        assertThat(prevThingDoc).isNotEqualTo(nextThingDoc);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, 13);

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
    public void testStringExpressionInUpdate() {
        final var collection = client.getCollection("test");

        final Metadata metadata =
                Metadata.of(ThingId.of("solar.system:pluto"), 23L,
                        PolicyTag.of(PolicyId.of("solar.system:pluto"), 45L), Set.of(), null, null);

        final JsonObject prevThing = getThing1();
        final JsonObject nextThing = getThing5(); // Thing1 with string field updated to begin with '$' and end with '.'

        final BsonDocument prevThingDoc =
                EnforcedThingMapper.toBsonDocument(prevThing, policy, metadata);

        final BsonDocument nextThingDoc =
                EnforcedThingMapper.toBsonDocument(nextThing, policy, metadata);

        final BsonDiff diff = BsonDiff.minusThingDocs(nextThingDoc, prevThingDoc, 13);

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
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", "x": 5}, true ],
                  "d": {
                    "e": {
                      "f": "g",
                      "h": "lorem ipsum dolor sit amet"
                    },
                    "j": true,
                    "k": 6.0,
                    "l": 123456789012
                  }
                }""");
    }

    private static JsonObject getThing2() {
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ false, {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", "x": 5} ],
                  "d": {
                    "e": {
                      "h": "lorem ipsum dolor sit amet"
                    },
                    "j": true,
                    "k": 6.0,
                    "l": 5,
                    "m": "MESSAGE"
                  }
                }""");
    }

    private static JsonObject getThing3() {
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", "y": 6}, true ],
                  "d": {
                    "j": true,
                    "k": 6.0,
                    "l": 123456789012,
                    "e": {
                      "f": "g",
                      "h": "lorem ipsum dolor sit amet"
                    }
                  }
                }""");
    }

    private static JsonObject getThing4() {
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", "x": 5}, true ],
                  "d": {
                    "e": {
                      "f": "g",
                      "h": "lorem ipsum dolor sit amet"
                    },
                    "j": true,
                    "k": 6.0,
                    "l": 123456789012,
                    "m": {},
                    "n": {"o":{}}
                  }
                }""");
    }

    private static JsonObject getThing5() {
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", "x": 5}, true ],
                  "d": {
                    "e": {
                      "f": "g",
                      "h": "$lorem ipsum dolor sit amet."
                    },
                    "j": true,
                    "k": 6.0,
                    "l": 123456789012
                  }
                }""");
    }

    private static JsonObject getThing6() {
        return JsonFactory.newObject("""
                {
                  "thingId":"solar.system:pluto",
                  "_namespace":"solar.system",
                  "a": [ {"b": "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"}, true ],
                  "d": {
                    "e": {
                      "f": "g",
                      "h": "lorem ipsum dolor sit amet"
                    },
                    "j": true,
                    "k": 6.0,
                    "l": 123456789012
                  }
                }""");
    }

}
