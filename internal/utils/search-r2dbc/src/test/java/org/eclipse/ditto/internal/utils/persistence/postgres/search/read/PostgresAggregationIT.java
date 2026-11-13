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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operator-aggregation-metrics integration test (Phase E) against a real PostgreSQL (PG 16) via Testcontainers. Data is
 * written by the REAL write path ({@link PostgresSearchUpdaterFlow}) and aggregated through
 * {@link PostgresThingsAggregationPersistence}, covering grouped counts, namespace {@code $match} scoping, a metric with
 * RQL filter criteria, the documented text-extraction type behavior of group values, and that a per-metric
 * {@code index-hint} is ignored (WARN once per metric, result unchanged).
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresAggregationIT {

    private static final String NS = "org.eclipse.ditto";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final Map<String, String> MAPPINGS =
            Map.of("thingId", "_id", "namespace", "_namespace");
    private static final String LOCATION_PATH = "attributes/coffeemaker/location";

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static DittoPostgresClient client;
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private PostgresSearchUpdaterFlow flow;
    private PostgresThingsAggregationPersistence aggregation;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresAggregationIT", t);
        }
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n");
        client = DittoPostgresClient.newInstance(DefaultPostgresConfig.of(config.getConfig("ditto")));
        connectionFactory = client.getConnectionPool();
        system = ActorSystem.create("PostgresAggregationIT");
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
    public void bootstrap() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.of(client);
        aggregation = PostgresThingsAggregationPersistence.of(client, MAPPINGS);
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================ grouped counts

    @Test
    public void groupedCountsByLocation() {
        write(location("l-1", "Berlin"), 1L);
        write(location("l-2", "Berlin"), 1L);
        write(location("l-3", "Sofia"), 1L);

        final Map<String, Long> counts = countsByGroup(aggregate(metric("by-loc", null, Set.of())), "location");
        assertThat(counts).containsOnly(Map.entry("Berlin", 2L), Map.entry("Sofia", 1L));
    }

    @Test
    public void namespaceMatchScopesTheAggregation() {
        write(location(ThingId.of("ns.a", "a1"), "Berlin"), 1L);
        write(location(ThingId.of("ns.a", "a2"), "Berlin"), 1L);
        write(location(ThingId.of("ns.b", "b1"), "Berlin"), 1L);

        // namespace $match term -> only ns.a rows counted.
        final Map<String, Long> counts =
                countsByGroup(aggregate(metric("by-loc-ns", null, Set.of("ns.a"))), "location");
        assertThat(counts).containsOnly(Map.entry("Berlin", 2L));
    }

    @Test
    public void filterCriteriaRestrictsCountedRows() {
        write(locationColor("f-1", "Berlin", "red"), 1L);
        write(locationColor("f-2", "Berlin", "blue"), 1L);
        write(locationColor("f-3", "Sofia", "red"), 1L);

        final Map<String, Long> counts = countsByGroup(
                aggregate(metric("by-loc-red", "eq(attributes/coffeemaker/color,\"red\")", Set.of())), "location");
        assertThat(counts).containsOnly(Map.entry("Berlin", 1L), Map.entry("Sofia", 1L));
    }

    // ============================================================================================ group-value type

    @Test
    public void numericGroupValueEmittedAsJsonStringViaTextExtraction() {
        write(floor("n-1", 3), 1L);
        write(floor("n-2", 3), 1L);
        write(floor("n-3", 7), 1L);

        final List<AggregateThingsMetricsResponse> responses = aggregate(
                AggregateThingsMetrics.of("by-floor", Map.of("floor", "attributes/floor"), null,
                        List.of(), null, HEADERS));

        // #>> text extraction: the _id.floor value is a JSON STRING ("3"), NOT a JSON number (documented divergence).
        final JsonObject anElement = responses.get(0).toJson()
                .getValue("aggregation").map(JsonValue::asObject).orElseThrow();
        final JsonValue floorValue = anElement.getValue("_id").map(JsonValue::asObject).orElseThrow()
                .getValue("floor").orElseThrow();
        assertThat(floorValue.isString()).isTrue();

        // ...and the sole consumer stringifies identically to Mongo (getGroupedBy via formatAsString).
        final Map<String, Long> counts = countsByGroup(responses, "floor");
        assertThat(counts).containsOnly(Map.entry("3", 2L), Map.entry("7", 1L));
    }

    // ================================================================================= zero-match no-group suppression

    @Test
    public void emptyGroupingByZeroMatchesEmitsNoElements() {
        write(location("z-1", "Berlin"), 1L);

        // No group-by keys + a filter matching nothing -> the GROUP-BY-less count(*) would otherwise return a lone
        // {"_id":{},"count":0} row; the persistence must suppress it (Mongo $group zero-input parity, see
        // PostgresAggregationQuery javadoc / reconcileVanishedBuckets rationale).
        final AggregateThingsMetrics command = AggregateThingsMetrics.of("no-group-vanished", Map.of(),
                "eq(attributes/nonExistentField,\"impossible-value\")", List.of(), HEADERS);

        final List<JsonObject> elements = run(aggregation.aggregateThings(command));
        assertThat(elements).isEmpty();
    }

    @Test
    public void emptyGroupingByWithMatchesEmitsExactlyOneElementWithTotalCount() {
        write(location("p-1", "Berlin"), 1L);
        write(location("p-2", "Sofia"), 1L);
        write(location("p-3", "Sofia"), 1L);

        // No group-by keys, at least one match -> exactly one element, the genuine all-rows group.
        final AggregateThingsMetrics command = AggregateThingsMetrics.of("no-group-present", Map.of(),
                null, List.of(), HEADERS);

        final List<JsonObject> elements = run(aggregation.aggregateThings(command));
        assertThat(elements).hasSize(1);
        final AggregateThingsMetricsResponse response =
                AggregateThingsMetricsResponse.of(elements.get(0), HEADERS, command.getMetricName());
        assertThat(response.getGroupedBy()).isEmpty();
        assertThat(response.getResult()).contains(3L);
    }

    // ============================================================================================ index-hint WARN

    @Test
    public void perMetricIndexHintIsIgnoredAndWarnedOncePerMetric() {
        write(location("h-1", "Berlin"), 1L);
        write(location("h-2", "Sofia"), 1L);

        final Logger persistenceLogger =
                (Logger) LoggerFactory.getLogger(PostgresThingsAggregationPersistence.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        persistenceLogger.addAppender(appender);
        try {
            final AggregateThingsMetrics hinted = AggregateThingsMetrics.of("hinted-metric",
                    Map.of("location", LOCATION_PATH), null, List.of(),
                    JsonFactory.newValue("someIndexName"), HEADERS);

            // Two invocations of the SAME metric -> hint ignored both times, WARNed exactly once.
            final Map<String, Long> first = countsByGroup(aggregate(hinted), "location");
            final Map<String, Long> second = countsByGroup(aggregate(hinted), "location");
            assertThat(first).containsOnly(Map.entry("Berlin", 1L), Map.entry("Sofia", 1L));
            assertThat(second).isEqualTo(first); // hint changes nothing.

            final long warnCount = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains("index-hint")
                            && e.getFormattedMessage().contains("hinted-metric"))
                    .count();
            assertThat(warnCount).isEqualTo(1L);
        } finally {
            persistenceLogger.detachAppender(appender);
        }
    }

    // ============================================================================================ helpers

    private AggregateThingsMetrics metric(final String name, final String filter, final Set<String> namespaces) {
        return AggregateThingsMetrics.of(name, Map.of("location", LOCATION_PATH), filter,
                List.copyOf(namespaces), null, HEADERS);
    }

    private List<AggregateThingsMetricsResponse> aggregate(final AggregateThingsMetrics command) {
        final List<JsonObject> elements = run(aggregation.aggregateThings(command));
        return elements.stream()
                .map(json -> AggregateThingsMetricsResponse.of(json, HEADERS, command.getMetricName()))
                .toList();
    }

    private static Map<String, Long> countsByGroup(final List<AggregateThingsMetricsResponse> responses,
            final String groupKey) {
        final Map<String, Long> counts = new HashMap<>();
        for (final AggregateThingsMetricsResponse response : responses) {
            final String value = response.getGroupedBy().get(groupKey);
            counts.put(value, response.getResult().orElse(0L));
        }
        return counts;
    }

    // ---- document construction ----

    private static JsonObject coffeemaker(final JsonObject inner) {
        return JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder().set("coffeemaker", inner).build())
                .build();
    }

    private SearchIndexDocument location(final String name, final String location) {
        return location(ThingId.of(NS, name), location);
    }

    private SearchIndexDocument location(final ThingId thingId, final String location) {
        return doc(thingId, coffeemaker(JsonObject.newBuilder().set("location", location).build()));
    }

    private SearchIndexDocument locationColor(final String name, final String location, final String color) {
        return doc(ThingId.of(NS, name),
                coffeemaker(JsonObject.newBuilder().set("location", location).set("color", color).build()));
    }

    private SearchIndexDocument floor(final String name, final int floor) {
        return doc(ThingId.of(NS, name),
                JsonObject.newBuilder().set("attributes", JsonObject.newBuilder().set("floor", floor).build()).build());
    }

    private SearchIndexDocument doc(final ThingId thingId, final JsonObject body) {
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", 1)
                .setAll(body)
                .build();
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(1L)
                .policyId(PolicyId.of(NS, "policy"))
                .policyRevision(1L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of(NS, "policy"), 1L)))
                .thing(thing)
                .build();
    }

    private static Metadata metadata(final ThingId thingId, final long revision) {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of(NS, "policy"), 1L);
        return Metadata.of(thingId, revision, policyTag, null, Set.of(policyTag), null);
    }

    private void write(final SearchIndexDocument document, final long revision) {
        run(ThingWriteModel.of(metadata(document.thingId(), revision), document));
    }

    private void run(final AbstractWriteModel model) {
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

    private <T> List<T> run(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.seq(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
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
