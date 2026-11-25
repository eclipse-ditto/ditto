/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQuery;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tests the exists and empty field expressions against a real PostgreSQL via Testcontainers: the PostgreSQL
 * counterpart of the MongoDB {@code ExistsIT} of {@code thingsearch/service}, with the same five things, the same
 * criteria and the same expected results, so that both backends answer {@code exists()} and {@code empty()} alike —
 * on the complete {@code attributes} and {@code features} objects too. Things are written by the real write path
 * ({@link PostgresSearchUpdaterFlow}) and read through {@link PostgresThingsSearchPersistence}; every criteria is
 * evaluated twice, without authorization subjects (sudo) and as a subject that may read the whole thing.
 * <p>
 * Skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresExistsIT {

    private static final String NS = "org.eclipse.ditto";
    private static final String GRANT_KEY = "·g";
    private static final String SUBJECT = "s1";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();

    private static final String THING1 = "thing1";
    private static final String THING1_KNOWN_ATTR = "attr1/a/b/c";
    private static final String THING1_KNOWN_ATTR_VALUE = "thing1";
    private static final String THING1_KNOWN_FEATURE_ID = "feature1";
    private static final String THING1_KNOWN_PROPERTY = "property/a/b/c";
    private static final long THING1_KNOWN_PROPERTY_VALUE = 1;
    private static final String THING2 = "thing2";
    private static final String THING2_KNOWN_ATTR = "attr1/a/b/d";
    private static final String THING2_KNOWN_ATTR_VALUE = "thing2";
    private static final String THING2_KNOWN_FEATURE_ID = "feature2";
    private static final String THING2_KNOWN_PROPERTY = "property/a/b/d";
    private static final long THING2_KNOWN_PROPERTY_VALUE = 2;
    private static final String THING3 = "thing3";
    private static final String THING4 = "thing4";
    private static final String THING5 = "thing5";

    private static final String THINGS_KNOWN_ATTR = "attr1/a/b";
    private static final String THINGS_UNKNOWN_ATTR = "attr2";

    private static final String TAGS1 = "tags1";
    private static final String TAGS2 = "tags2";
    private static final String TAGS3 = "tags3";
    private static final String TAGS4 = "tags4";
    private static final String TAGS5 = "tags5";

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static DittoPostgresClient client;
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private final CriteriaFactory cf = CriteriaFactory.getInstance();
    private final ThingsFieldExpressionFactory ef =
            ThingsFieldExpressionFactory.of(Map.of("thingId", "_id", "namespace", "_namespace"));

    private PostgresSearchUpdaterFlow flow;
    private PostgresThingsSearchPersistence persistence;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresExistsIT", t);
        }
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        client = DittoPostgresClient.newInstance(DefaultPostgresConfig.of(config.getConfig("ditto")));
        connectionFactory = client.getConnectionPool();
        system = ActorSystem.create("PostgresExistsIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        if (client != null) {
            client.close();
        }
        POSTGRES.stop();
    }

    @Before
    public void createTestData() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.of(client);
        persistence = PostgresThingsSearchPersistence.of(client);
        insertThings();
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    @Test
    public void existsByKnownFeatureId() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureId(THING2_KNOWN_FEATURE_ID));
        assertFound(crit, THING2, THING3);
    }

    @Test
    public void existsByKnownFeatureIdAndProperty() {
        final Criteria crit =
                cf.existsCriteria(ef.existsByFeatureProperty(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY));
        assertFound(crit, THING1);
    }

    @Test
    public void existsByKnownFeatureIdAndDesiredProperty() {
        final Criteria crit =
                cf.existsCriteria(ef.existsByFeatureDesiredProperty(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY));
        assertFound(crit, THING1);
    }

    @Test
    public void existsByProperties() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperties(THING1_KNOWN_FEATURE_ID));
        assertFound(crit, THING1);
    }

    @Test
    public void existsByDesiredProperties() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureDesiredProperties(THING2_KNOWN_FEATURE_ID));
        assertFound(crit, THING2, THING3);
    }

    @Test
    public void existsByExactAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THING2_KNOWN_ATTR));
        assertFound(crit, THING2);
    }

    @Test
    public void existsByKnownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_KNOWN_ATTR));
        assertFound(crit, THING1, THING2);
    }

    @Test
    public void existsByUnknownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_UNKNOWN_ATTR));
        assertFound(crit);
    }

    @Test
    public void nullAndEmptyValuesExist() {
        for (final String tagName : List.of(TAGS1, TAGS2, TAGS3, TAGS4, TAGS5)) {
            assertFound(cf.existsCriteria(ef.existsByAttribute(tagName)), THING1);
        }
    }

    @Test
    public void existsByAnyFeature() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureId("*"));
        assertFound(crit, THING1, THING2, THING3);
    }

    @Test
    public void existsByAttributes() {
        final Criteria crit = cf.existsCriteria(ef.existsBy("attributes"));
        assertFound(crit, THING1, THING2, THING4);
    }

    @Test
    public void notExistsByAttributes() {
        final Criteria crit = cf.nor(cf.existsCriteria(ef.existsBy("attributes")));
        assertFound(crit, THING3, THING5);
    }

    @Test
    public void emptyByAttributes() {
        final Criteria crit = cf.emptyCriteria(ef.existsBy("attributes"));
        assertFound(crit, THING3, THING4, THING5);
    }

    @Test
    public void existsByFeatures() {
        final Criteria crit = cf.existsCriteria(ef.existsBy("features"));
        assertFound(crit, THING1, THING2, THING3, THING4);
    }

    @Test
    public void notExistsByFeatures() {
        final Criteria crit = cf.nor(cf.existsCriteria(ef.existsBy("features")));
        assertFound(crit, THING5);
    }

    @Test
    public void emptyByFeatures() {
        final Criteria crit = cf.emptyCriteria(ef.existsBy("features"));
        assertFound(crit, THING4, THING5);
    }

    private void assertFound(final Criteria criteria, final String... expectedThingNames) {
        assertThat(find(criteria, null)).as("sudo").containsExactlyInAnyOrder(expectedThingNames);
        assertThat(find(criteria, List.of(SUBJECT))).as("as %s", SUBJECT)
                .containsExactlyInAnyOrder(expectedThingNames);
    }

    private List<String> find(final Criteria criteria, @Nullable final List<String> subjects) {
        final PostgresQuery query =
                new PostgresQuery(criteria, List.of(new SortOption(ef.sortByThingId(), SortDirection.ASC)), 100, 0);
        final ResultList<TimestampedThingId> result =
                runSingle(persistence.findAll(query, subjects, null, HEADERS));
        return result.stream().map(t -> t.thingId().getName()).collect(Collectors.toList());
    }

    private void insertThings() {
        final Attributes attributes1 = createAttributes(THING1_KNOWN_ATTR, THING1_KNOWN_ATTR_VALUE).toBuilder()
                .setAll(createEmptyAttributes())
                .build();
        final Features features1 =
                createFeatures(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY, THING1_KNOWN_PROPERTY_VALUE);

        final Attributes attributes2 = createAttributes(THING2_KNOWN_ATTR, THING2_KNOWN_ATTR_VALUE);
        final Features features2 =
                createFeatures(THING2_KNOWN_FEATURE_ID, THING2_KNOWN_PROPERTY, THING2_KNOWN_PROPERTY_VALUE);

        persistThing(THING1, attributes1, features1);
        persistThing(THING2, attributes2, features2);
        persistThing(THING3, null, features2);
        persistThing(THING4, ThingsModelFactory.emptyAttributes(), ThingsModelFactory.emptyFeatures());
        persistThing(THING5, null, null);
    }

    private void persistThing(final String name, @Nullable final Attributes attributes,
            @Nullable final Features features) {

        final ThingId thingId = ThingId.of(NS, name);
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of(NS, "policy"), 1L);
        final JsonObjectBuilder thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", 1);
        if (attributes != null) {
            thing.set("attributes", attributes.toJson());
        }
        if (features != null) {
            thing.set("features", features.toJson());
        }
        final SearchIndexDocument document = SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(1L)
                .policyId(policyTag.getEntityId())
                .policyRevision(1L)
                .referencedPolicies(Set.of(policyTag))
                .globalRead(List.of(SUBJECT))
                .thing(thing.build())
                .policyAuth(JsonObject.newBuilder()
                        .set(GRANT_KEY, JsonObject.newBuilder().set(SUBJECT, JsonObject.empty()).build())
                        .build())
                .build();
        final AbstractWriteModel model =
                ThingWriteModel.of(Metadata.of(thingId, 1L, policyTag, null, Set.of(policyTag), null), document);
        try {
            Source.single(new UpdaterData(model, model))
                    .via(flow.create())
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("write failed", e);
        }
    }

    private static Attributes createAttributes(final String attributeKey, final String attributeValue) {
        return Attributes.newBuilder()
                .set(JsonPointer.of(attributeKey), attributeValue)
                .build();
    }

    private static Features createFeatures(final String featureId, final CharSequence propertyKey,
            final long propertyValue) {

        final Feature feature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty(propertyKey, propertyValue)
                .setDesiredProperty(propertyKey, propertyValue);
        return Features.newBuilder().set(feature).build();
    }

    private static JsonObject createEmptyAttributes() {
        return JsonObject.newBuilder()
                .set(TAGS1, JsonObject.empty())
                .set(TAGS2, JsonFactory.nullLiteral())
                .set(TAGS3, JsonObject.newBuilder().set("foo", JsonFactory.nullLiteral()).build())
                .set(TAGS4, JsonObject.newBuilder().set("foo", JsonObject.empty()).build())
                .set(TAGS5, JsonArray.empty())
                .build();
    }

    private <T> T runSingle(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.head(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
