/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.CustomAggregationMetricConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsAggregationPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsAggregationPersistence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.reactivestreams.Publisher;

import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.result.InsertManyResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;

@RunWith(Parameterized.class)
@Ignore("TODO temporarily ignored for release")
public class AggregateThingsMetricsActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();
    private static final TransitionWalker.ReachedState<RunningMongodProcess> RUNNING_MONGOD_PROCESS =
            Mongod.instance().start(Version.Main.V7_0);
    private static ActorSystem SYSTEM = ActorSystem.create();
    private static final LoggingAdapter LOG = SYSTEM.log();
    private static DittoMongoClient mongoClient;
    private static DittoSearchConfig searchConfig;
    private static ThingsAggregationPersistence persistence;

    @BeforeClass
    public static void initTestData() {
        final ServerAddress serverAddress = RUNNING_MONGOD_PROCESS.current().getServerAddress();
        mongoClient = provideClientWrapper(serverAddress, "testSearchDB");
        persistence = MongoThingsAggregationPersistence.of(mongoClient, searchConfig, LOG);
        LOG.info("Mongo started at: {}", serverAddress);
        List<Map<String, String>> paramList = List.of(
                Map.of(
                        "thingId", "org.eclipse.ditto:thing1",
                        "serial", "41",
                        "model", "Speaking coffee machine",
                        "location", "Berlin",
                        "readySince", "2020-03-12T09:12:13.072565678Z",
                        "readyUntil", "2020-03-12T09:12:13.072565678Z"
                ),
                Map.of(
                        "thingId", "org.eclipse.ditto:thing2",
                        "serial", "42",
                        "model", "Speaking coffee machine",
                        "location", "Sofia",
                        "readySince", "2024-03-12T09:12:13.072565678Z",
                        "readyUntil", "2224-03-12T09:12:13.072565678Z"
                ),
                Map.of(
                        "thingId", "org.eclipse.ditto:thing3",
                        "serial", "43",
                        "model", "Speaking coffee machine",
                        "location", "Immenstaad",
                        "readySince", "2024-03-12T09:12:13.072565678Z",
                        "readyUntil", "2224-03-12T09:12:13.072565678Z"
                )
        );
        insert("search",
                loadDocumentsFromResource("aggregation-metrics-test-data.json", paramList).toArray(new Document[0]));
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        final Config config = ConfigFactory.load("aggregation-metric-test.conf");
        final DefaultScopedConfig scopedConfig = DefaultScopedConfig.dittoScoped(config);
        searchConfig = DittoSearchConfig.of(scopedConfig);
        return searchConfig.getOperatorMetricsConfig().getCustomAggregationMetricConfigs().entrySet().stream()
                .map(entry -> new Object[]{entry.getKey(), entry.getValue()})  // {name, config}
                .toList();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(SYSTEM);
        if (RUNNING_MONGOD_PROCESS != null) {
            RUNNING_MONGOD_PROCESS.current().stop();
        }
        SYSTEM = null;
    }

    private static DittoMongoClient provideClientWrapper(final ServerAddress serverAddress, final String dbName) {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + serverAddress + "/" + dbName)
                .build();
    }

    private final CustomAggregationMetricConfig config;

    @SuppressWarnings("unused")
    public AggregateThingsMetricsActorTest(String metricName, CustomAggregationMetricConfig config) {
        this.config = config;
    }

  /**
   * This test is dynamic and will run for each custom aggregation metric defined in
   * the configuration file `aggregation-metric-test.conf` under
   * `ditto.search.operator-metrics.custom-aggregation-metrics`.
   */
    @Test
    public void testAggregationMetric() {
        new TestKit(SYSTEM) {{

            final var actor = SYSTEM.actorOf(AggregateThingsMetricsActor.props(persistence));

            final AggregateThingsMetrics command = AggregateThingsMetrics.of(
                    config.getMetricName(),
                    config.getGroupBy(),
                    config.getFilter().orElse(null),
                    config.getNamespaces(),
                    DittoHeaders.newBuilder()
                            .correlationId(AggregateThingsMetrics.class.getSimpleName() + "-" + UUID.randomUUID())
                            .build()
            );

            actor.tell(command, getRef());
            config.getTags().entrySet().stream().filter(entry -> entry.getKey().startsWith("expectedResult"))
                    .forEach(entry -> {
                        String expectedResult = entry.getValue();
                        final AggregateThingsMetricsResponse response =
                                expectMsgClass(Duration.ofSeconds(5), AggregateThingsMetricsResponse.class);
                        LOG.info("Aggregation {}: {}", entry.getKey(), response);
                        assertThat(response.getMetricName()).isEqualTo(config.getMetricName());
                        assertThat(response.getResult()).isPresent();
                        config.getGroupBy().keySet().forEach(key ->
                                assertThat(response.getGroupedBy()).containsKey(key)
                        );
                        if (expectedResult != null) {
                            assertThat(response.getResult().get()).isEqualTo(Integer.parseInt(expectedResult));
                        }

                    });
            expectNoMsg();
        }};
    }

    private static void insert(final CharSequence collection, final Document... documents) {
        final Publisher<InsertManyResult> insertManyResultPublisher =
                mongoClient.getCollection(collection)
                        .insertMany(Arrays.asList(documents), new InsertManyOptions().ordered(false));
        Source.fromPublisher(insertManyResultPublisher)
                .runWith(Sink.head(), SYSTEM).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        LOG.error(throwable, "Insert failed: {}", throwable.getMessage());
                    } else {
                        LOG.info("Insert successful: {}", result.getInsertedIds());
                    }
                }).toCompletableFuture().join();
    }

    private static List<Document> loadDocumentsFromResource(final String resourcePath,
            final List<Map<String, String>> parameterList) {
        try (InputStream inputStream = AggregateThingsMetricsActorTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            List<Document> documents = new ArrayList<>();
            for (Map<String, String> params : parameterList) {
                String filled = template;
                for (var entry : params.entrySet()) {
                    filled = filled.replace("${" + entry.getKey() + "}", entry.getValue());
                }
                documents.add(Document.parse(filled));
            }
            return documents;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource: " + resourcePath, e);
        }
    }
}
