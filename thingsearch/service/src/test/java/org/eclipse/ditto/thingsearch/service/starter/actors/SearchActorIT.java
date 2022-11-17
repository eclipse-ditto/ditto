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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultSearchPersistenceConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdateMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.TestSearchUpdaterStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link SearchActor}.
 */
public final class SearchActorIT {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final AuthorizationContext AUTH_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("ditto:ditto"));
    private static final PolicyId POLICY_ID = PolicyId.of("default", "policy");
    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    private static Policy policy;
    private static DittoMongoClient mongoClient;

    private static Config actorsTestConfig;
    private static QueryParser queryParser;

    private ActorSystem actorSystem;
    private MongoThingsSearchPersistence readPersistence;
    private MongoCollection<Document> thingsCollection;
    private TestSearchUpdaterStream writePersistence;

    @BeforeClass
    public static void startMongoResource() {
        actorsTestConfig = ConfigFactory.load("actors-test.conf");
        queryParser = SearchRootActor.getQueryParser(
                DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(actorsTestConfig)),
                ActorSystem.create(SearchActorIT.class.getSimpleName(), actorsTestConfig));
        mongoClient = provideClientWrapper();
        policy = createPolicy();
    }

    @Before
    public void before() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), actorsTestConfig);
        readPersistence = provideReadPersistence();
        writePersistence = provideWritePersistence(actorSystem);
        thingsCollection = mongoClient.getDefaultDatabase().getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
    }

    private MongoThingsSearchPersistence provideReadPersistence() {
        final var config = DefaultSearchPersistenceConfig.of(ConfigFactory.empty());
        final MongoThingsSearchPersistence result = new MongoThingsSearchPersistence(mongoClient, actorSystem, config);
        // explicitly trigger CompletableFuture to make sure that indices are created before test runs
        result.initializeIndices().toCompletableFuture().join();
        return result;
    }

    private static TestSearchUpdaterStream provideWritePersistence(final ActorSystem system) {
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(system.settings().config());
        return TestSearchUpdaterStream.of(mongoClient.getDefaultDatabase(),
                SearchUpdateMapper.get(system, dittoExtensionsConfig));
    }

    private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + MONGO_RESOURCE.getBindIp() + ":" + MONGO_RESOURCE.getPort() + "/testSearchDB")
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
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }

    @Test
    public void testSearch() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(SearchActor.props(queryParser, readPersistence,
                    actorSystem.deadLetters()));

            insertTestThings();

            underTest.tell(queryThings(200, null), getRef());

            final QueryThingsResponse response = expectMsgClass(QueryThingsResponse.class);

            assertThat(response.getSearchResult().getItems()).isEqualTo(expectedIds(4, 2, 0, 1, 3));
        }};
    }

    @Test
    public void testStream() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(SearchActor.props(queryParser, readPersistence,
                    actorSystem.deadLetters()));

            insertTestThings();

            underTest.tell(queryThings(null, null), getRef());

            final SourceRef<?> response = expectMsgClass(SourceRef.class);
            final JsonArray searchResult = response.getSource()
                    .runWith(Sink.seq(), actorSystem)
                    .toCompletableFuture()
                    .join()
                    .stream()
                    .map(thingId -> wrapAsSearchResult((String) thingId))
                    .collect(JsonCollectors.valuesToArray());

            assertThat(searchResult).isEqualTo(expectedIds(4, 2, 0, 1, 3));
        }};
    }

    @Test
    public void testCursorSearch() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(SearchActor.props(queryParser, readPersistence,
                    actorSystem.deadLetters()));
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

    private static Command<?> queryThings(@Nullable final Integer size, final @Nullable String cursor) {
        final List<String> options = new ArrayList<>();
        final String sort = "sort(-attributes/c,+attributes/b,-attributes/a,+attributes/null/1,-attributes/null/2)";
        if (cursor == null) {
            options.add(sort);
        }
        if (size != null) {
            options.add("size(" + size + ")");
        }
        if (cursor != null) {
            options.add("cursor(" + cursor + ")");
        }
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AUTH_CONTEXT)
                .build();
        final String filter = "and(eq(attributes/x,5),eq(_metadata/attributes/x/type,\"x attribute\"))";
        if (size != null) {
            return QueryThings.of(filter, options, null, null, dittoHeaders);
        } else {
            return StreamThings.of(filter, null, sort, null, dittoHeaders);
        }
    }

    private void insertTestThings() {
        final Thing baseThing = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("thing", "00"))
                .setRevision(1234L)
                .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                .setMetadata(MetadataModelFactory.newMetadataBuilder()
                        .set("attributes", JsonObject.newBuilder()
                                .set("x", JsonObject.newBuilder()
                                        .set("type", "x attribute")
                                        .build())
                                .build())
                        .build())
                .build();

        final Thing irrelevantThing = baseThing.toBuilder().removeAllAttributes().build();

        writePersistence.write(template(baseThing, 0, "a"), policy, 0L)
                .concat(writePersistence.write(template(baseThing, 1, "b"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 2, "a"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 3, "b"), policy, 0L))
                .concat(writePersistence.write(template(baseThing, 4, "c"), policy, 0L))
                .concat(writePersistence.write(template(irrelevantThing, 5, "c"), policy, 0L))
                .runWith(Sink.ignore(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private static Policy createPolicy() {
        final Collection<Subject> subjects =
                AUTH_CONTEXT.getAuthorizationSubjectIds().stream()
                        .map(subjectId -> Subject.newInstance(subjectId, SubjectType.GENERATED))
                        .toList();
        final Collection<Resource> resources = Collections.singletonList(Resource.newInstance(
                ResourceKey.newInstance("thing:/"),
                EffectedPermissions.newInstance(Collections.singletonList("READ"), Collections.emptyList())
        ));
        final PolicyEntry policyEntry = PolicyEntry.newInstance("viewer", subjects, resources);
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .set(policyEntry)
                .setRevision(1L)
                .build();
    }

    private static JsonArray expectedIds(final int... thingOrdinals) {
        return Arrays.stream(thingOrdinals)
                .mapToObj(i -> "thing:" + i)
                .map(SearchActorIT::wrapAsSearchResult)
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonValue wrapAsSearchResult(final CharSequence thingId) {
        return JsonFactory.readFrom(String.format("{\"thingId\":\"%s\"}", thingId));
    }

    private static Thing template(final Thing thing, final int i, final CharSequence attribute) {
        return thing.toBuilder()
                .setId(ThingId.of("thing", String.valueOf(i)))
                .setAttribute(JsonPointer.of(attribute), JsonValue.of(i))
                .build();
    }

}
