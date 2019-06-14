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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryParser;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.TestSearchUpdaterStream;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.thingsearch.starter.actors.SearchActor}.
 */
public final class SearchActorIT {

    private static final AuthorizationContext AUTH_CONTEXT =
            AuthorizationContext.newInstance(AuthorizationSubject.newInstance("ditto:ditto"));

    private static QueryParser queryParser;
    private static MongoDbResource mongoResource;
    private static DittoMongoClient mongoClient;

    private MongoThingsSearchPersistence readPersistence;
    private MongoCollection<Document> thingsCollection;
    private TestSearchUpdaterStream writePersistence;

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;

    @BeforeClass
    public static void startMongoResource() {
        queryParser = SearchRootActor.getQueryParser(DefaultLimitsConfig.of(ConfigFactory.empty()));
        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
        mongoClient = provideClientWrapper();
    }

    @Before
    public void before() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(),
                ConfigFactory.parseString("search-dispatcher {\n" +
                        "  type = Dispatcher\n" +
                        "  executor = \"fork-join-executor\"\n" +
                        "  fork-join-executor {\n" +
                        "    parallelism-min = 4\n" +
                        "    parallelism-factor = 3.0\n" +
                        "    parallelism-max = 32\n" +
                        "  }\n" +
                        "  throughput = 5\n" +
                        "}"));
        materializer = ActorMaterializer.create(actorSystem);
        readPersistence = provideReadPersistence();
        writePersistence = provideWritePersistence();
        thingsCollection = mongoClient.getDefaultDatabase().getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
    }

    private MongoThingsSearchPersistence provideReadPersistence() {
        final MongoThingsSearchPersistence result = new MongoThingsSearchPersistence(mongoClient, actorSystem);
        // explicitly trigger CompletableFuture to make sure that indices are created before test runs
        result.initializeIndices().toCompletableFuture().join();
        return result;
    }

    private static TestSearchUpdaterStream provideWritePersistence() {
        return TestSearchUpdaterStream.of(mongoClient.getDefaultDatabase());
    }

    private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + mongoResource.getBindIp() + ":" + mongoResource.getPort() + "/testSearchDB")
                .build();
    }

    @After
    public void after() {
        if (mongoClient != null) {
            thingsCollection.drop();
        }
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        materializer = null;
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
            if (mongoResource != null) {
                mongoResource.stop();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }

    @Test
    public void testSearch() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(SearchActor.props(queryParser, readPersistence));

            insertTestThings();

            underTest.tell(queryThings(200, null), getRef());

            final QueryThingsResponse response = expectMsgClass(QueryThingsResponse.class);

            assertThat(response.getSearchResult().getItems()).isEqualTo(expectedIds(4, 2, 0, 1, 3));
        }};
    }

    @Test
    public void testCursorSearch() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(SearchActor.props(queryParser, readPersistence));
            final Supplier<AssertionError> noCursor =
                    () -> new AssertionError("No cursor where a cursor is expected");

            insertTestThings();

            underTest.tell(queryThings(1, null), getRef());
            final QueryThingsResponse response0 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response0.getSearchResult().getItems()).isEqualTo(expectedIds(4));

            underTest.tell(queryThings(1, response0.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response1 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response1.getSearchResult().getItems()).isEqualTo(expectedIds(2));

            underTest.tell(queryThings(1, response1.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response2 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response2.getSearchResult().getItems()).isEqualTo(expectedIds(0));

            underTest.tell(queryThings(1, response2.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response3 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response3.getSearchResult().getItems()).isEqualTo(expectedIds(1));

            underTest.tell(queryThings(1, response3.getSearchResult().getCursor().orElseThrow(noCursor)), getRef());
            final QueryThingsResponse response4 = expectMsgClass(QueryThingsResponse.class);
            assertThat(response4.getSearchResult().getItems()).isEqualTo(expectedIds(3));

            assertThat(response4.getSearchResult().getCursor()).isEmpty();
        }};
    }

    private static QueryThings queryThings(final int size, final @Nullable String cursor) {
        final List<String> options = new ArrayList<>();
        if (cursor == null) {
            options.add("sort(-attributes/c,+attributes/b,-attributes/a,+attributes/null/1,-attributes/null/2)");
        }
        options.add("size(" + size + ")");
        if (cursor != null) {
            options.add("cursor(" + cursor + ")");
        }
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AUTH_CONTEXT)
                .build();
        return QueryThings.of("eq(attributes/x,5)", options, null, null, dittoHeaders);
    }

    private void insertTestThings() {
        final Thing baseThing = ThingsModelFactory.newThingBuilder()
                .setId("thing:00")
                .setRevision(1234L)
                .setPermissions(AUTH_CONTEXT.getFirstAuthorizationSubject().orElseThrow(AssertionError::new),
                        Permission.READ)
                .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                .build();

        final Thing irrelevantThing = baseThing.toBuilder().removeAllAttributes().build();

        writePersistence.writeThingWithAcl(template(baseThing, 0, "a"))
                .concat(writePersistence.writeThingWithAcl(template(baseThing, 1, "b")))
                .concat(writePersistence.writeThingWithAcl(template(baseThing, 2, "a")))
                .concat(writePersistence.writeThingWithAcl(template(baseThing, 3, "b")))
                .concat(writePersistence.writeThingWithAcl(template(baseThing, 4, "c")))
                .concat(writePersistence.writeThingWithAcl(template(irrelevantThing, 5, "c")))
                .runWith(Sink.ignore(), materializer)
                .toCompletableFuture()
                .join();
    }

    private static JsonArray expectedIds(final int... thingOrdinals) {
        return Arrays.stream(thingOrdinals)
                .mapToObj(i -> JsonFactory.readFrom(String.format("{\"thingId\":\"thing:%d\"}", i)))
                .collect(JsonCollectors.valuesToArray());
    }

    private static Thing template(final Thing thing, final int i, final CharSequence attribute) {
        return thing.toBuilder()
                .setId("thing:" + i)
                .setAttribute(JsonPointer.of(attribute), JsonValue.of(i))
                .build();
    }

}
