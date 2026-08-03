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
package org.eclipse.ditto.timeseries.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.test.docker.mongo.MongoDbResource;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Integration test for {@link MongoDbTimeseriesAdapter}'s cross-Thing surface
 * ({@code queryCrossThing} and {@code discoverContributors}) against a <b>real</b> MongoDB.
 * <p>
 * This exists because {@code MongoDbTimeseriesAdapterCrossThingTest} can only assert the
 * <em>shape</em> of the aggregation pipeline against a mocked driver. It cannot prove that the
 * two-stage {@code $group} actually folds buckets into series correctly, that the computed values are
 * right, or — most importantly — that the per-path authorization filter really excludes the data it
 * claims to. Those are exactly the properties whose failure would be invisible: a wrong filter still
 * returns a plausible number.
 * <p>
 * Runs against a throwaway MongoDB container; set
 * {@code TIMESERIES_MONGODB_TEST_URI=mongodb://localhost:27017} to use an existing instance instead.
 * Cross-Thing queries span a whole namespace, so unlike
 * {@code MongoDbTimeseriesAdapterIT} — which isolates tests by using a random <em>Thing name</em>
 * inside one shared namespace — every test here gets its own <b>namespace</b>, and therefore its own
 * {@code ts_<namespace>} collection. Sharing a namespace would let one test's leftover Things
 * contribute to another test's aggregate. The database is dropped in {@code @AfterClass}.
 */
public final class MongoDbTimeseriesAdapterCrossThingIT {

    private static final String ENV_VAR = "TIMESERIES_MONGODB_TEST_URI";
    private static final String DATABASE = "ditto_ts_crossthing_it";

    private static final JsonPointer FLOW =
            JsonPointer.of("/features/circuit/properties/flowTemperature");
    private static final JsonPointer RETURN =
            JsonPointer.of("/features/circuit/properties/returnTemperature");

    private static final Instant HOUR = Instant.parse("2026-01-14T10:00:00Z");
    private static final Instant HOUR_END = Instant.parse("2026-01-14T11:00:00Z");
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final String UNIT = "cel";

    /** Optional override pointing at an existing MongoDB; when set, no container is started. */
    private static final String URI_OVERRIDE = System.getenv(ENV_VAR);
    /**
     * Null when {@value ENV_VAR} is set. Constructed lazily like this because
     * {@code new MongoDbResource()} already reaches for the Docker daemon, so an unconditional field
     * would fail class initialisation on a machine without Docker even though the override means no
     * container is needed.
     */
    @Nullable
    private static final MongoDbResource CONTAINER = isOverridden() ? null : new MongoDbResource();

    private static String uri;

    private MongoDbTimeseriesAdapter adapter;
    private String namespace;
    private ThingId t1;
    private ThingId t2;
    private ThingId t3;

    /**
     * Starts a throwaway MongoDB container for the class. Replaced by a no-op rule — so no container
     * is started at all — when {@value ENV_VAR} points at an already-running instance.
     * <p>
     * Running by default is deliberate. This IT previously required {@value ENV_VAR} to be set,
     * which meant CI always skipped it; the harness then broke unnoticed (the database name was
     * passed via a config key nothing reads) and every case here errored the moment it was enabled.
     * A test that only runs when someone remembers to opt in provides no regression protection.
     */
    @ClassRule
    public static final TestRule MONGO = CONTAINER == null ? (base, description) -> base : CONTAINER;

    @BeforeClass
    public static void resolveUri() {
        uri = isOverridden()
                ? URI_OVERRIDE
                : "mongodb://" + requireContainer().getBindIp() + ":" + requireContainer().getPort();
    }

    private static boolean isOverridden() {
        return URI_OVERRIDE != null && !URI_OVERRIDE.isEmpty();
    }

    private static MongoDbResource requireContainer() {
        if (CONTAINER == null) {
            throw new IllegalStateException("No container was started; " + ENV_VAR + " is set.");
        }
        return CONTAINER;
    }

    @Before
    public void setUp() throws Exception {
        adapter = new MongoDbTimeseriesAdapter();
        // One namespace per test => one collection per test => no cross-test contamination of an
        // aggregate that is, by definition, namespace-wide.
        namespace = "it.ts.n" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        t1 = ThingId.of(namespace, "sensor-1");
        t2 = ThingId.of(namespace, "sensor-2");
        t3 = ThingId.of(namespace, "sensor-3");

        // The database name must be a path segment of the URI: DefaultMongoDbConfig reads only
        // `ditto.mongodb.uri`, so a separate `ditto.mongodb.database` key is silently ignored and
        // getDefaultDatabase() then resolves to null.
        final Config rootConfig = ConfigFactory.parseString(String.format(
                "ditto.mongodb.uri = \"%s\"\n", MongoDbItUris.withDatabase(uri, DATABASE)));
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(rootConfig));
        final MongoDbTimeseriesAdapterConfig config = DefaultMongoDbTimeseriesAdapterConfig.of(
                mongoDbConfig, "ts_", Granularity.SECONDS);
        adapter.initialize(config).toCompletableFuture().get();
    }

    @After
    public void tearDown() throws Exception {
        if (adapter != null) {
            adapter.shutdown().toCompletableFuture().get();
        }
    }

    @AfterClass
    public static void dropTestDatabase() throws Exception {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        final MongoClient client = MongoClients.create(new ConnectionString(uri));
        try {
            final java.util.concurrent.CompletableFuture<Void> done =
                    new java.util.concurrent.CompletableFuture<>();
            client.getDatabase(DATABASE).drop().subscribe(new Subscriber<Void>() {
                @Override public void onSubscribe(final Subscription s) { s.request(Long.MAX_VALUE); }
                @Override public void onNext(final Void item) { /* discard */ }
                @Override public void onError(final Throwable t) { done.completeExceptionally(t); }
                @Override public void onComplete() { done.complete(null); }
            });
            done.get();
        } finally {
            client.close();
        }
    }

    @Test
    public void adapterAdvertisesNativeCrossThingSupport() {
        assertThat(adapter.capabilities().supportsNativeCrossThingQuery()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Folding across Things — what the mocked pipeline test cannot compute
    // ---------------------------------------------------------------------------------------------

    /**
     * Without {@code groupBy} the points of <em>all</em> Things must collapse into a single series.
     * This is the two-stage {@code $group} doing its job for real: bucket first, then fold the
     * buckets of one series into one document.
     */
    @Test
    public void aggregatesPointsOfSeveralThingsIntoOneSeries() throws Exception {
        writeSpreadAcrossThreeThings();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG), null);

        assertThat(results).hasSize(1);
        assertThat((Object) results.get(0).getPath()).isEqualTo(FLOW);
        assertThat(results.get(0).getGroup()).isEmpty();
        assertThat(results.get(0).getData()).hasSize(1);
        // 7 points across 3 Things: 10,20,30,40,50,60,90 -> 300/7.
        assertThat(doubleAt(results.get(0), 0)).isCloseTo(42.857142857142854, within(1e-9));
    }

    /**
     * Every bucketed aggregation, checked against the arithmetic rather than against another
     * MongoDB expression. {@code first}/{@code last} are the interesting pair: they depend on the
     * pipeline sorting by timestamp <em>across</em> Things, so they would silently return an
     * arbitrary Thing's value if the sort were dropped.
     */
    @Test
    public void computesEveryBucketedAggregationCorrectlyAcrossThings() throws Exception {
        writeSpreadAcrossThreeThings();

        assertThat(singleValue(Aggregation.COUNT)).isCloseTo(7.0, within(1e-9));
        assertThat(singleValue(Aggregation.SUM)).isCloseTo(300.0, within(1e-9));
        assertThat(singleValue(Aggregation.AVG)).isCloseTo(42.857142857142854, within(1e-9));
        assertThat(singleValue(Aggregation.MIN)).isCloseTo(10.0, within(1e-9));
        assertThat(singleValue(Aggregation.MAX)).isCloseTo(90.0, within(1e-9));
        // Earliest point overall is t1's at :00; latest is t3's at :30.
        assertThat(singleValue(Aggregation.FIRST)).isCloseTo(10.0, within(1e-9));
        assertThat(singleValue(Aggregation.LAST)).isCloseTo(90.0, within(1e-9));
        // Sample standard deviation ($stdDevSamp), not population.
        assertThat(singleValue(Aggregation.STDDEV)).isCloseTo(26.90370836538197, within(1e-9));
    }

    @Test
    public void groupByThingIdYieldsOneSeriesPerThingWithItsOwnValues() throws Exception {
        writeSpreadAcrossThreeThings();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.singletonList(GroupBy.thingId()),
                        null, null, null),
                null);

        final Map<String, AggregatedTimeseriesResult> byThing = new LinkedHashMap<>();
        for (final AggregatedTimeseriesResult r : results) {
            byThing.put(r.getGroup().get("thingId"), r);
        }
        assertThat(byThing.keySet()).containsExactlyInAnyOrder(
                t1.toString(), t2.toString(), t3.toString());
        assertThat(doubleAt(byThing.get(t1.toString()), 0)).isCloseTo(20.0, within(1e-9)); // 10,20,30
        assertThat(doubleAt(byThing.get(t2.toString()), 0)).isCloseTo(50.0, within(1e-9)); // 40,50,60
        assertThat(doubleAt(byThing.get(t3.toString()), 0)).isCloseTo(90.0, within(1e-9)); // 90
    }

    @Test
    public void reportsOnePerPathSeriesForAMultiPathQuery() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 10.0),
                point(t2, FLOW, 10, 30.0),
                point(t1, RETURN, 0, 100.0),
                point(t2, RETURN, 10, 200.0))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Arrays.asList(FLOW, RETURN), Aggregation.AVG), null);

        assertThat(results).hasSize(2);
        assertThat(doubleAt(seriesFor(results, FLOW), 0)).isCloseTo(20.0, within(1e-9));
        assertThat(doubleAt(seriesFor(results, RETURN), 0)).isCloseTo(150.0, within(1e-9));
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization: the per-path allow-list, exercised through a real $match
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code null} means "the caller may read every Thing in the namespace" — the only case where an
     * unfiltered scan is correct.
     */
    @Test
    public void nullAllowListReadsEveryThingInTheNamespace() throws Exception {
        writeSpreadAcrossThreeThings();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.COUNT), null);

        assertThat(doubleAt(results.get(0), 0)).isCloseTo(7.0, within(1e-9));
    }

    /**
     * The core of the feature: {@code READ_TS} is grantable per property, so a Thing permitted on one
     * requested path and denied another must contribute to the first and not the second. Asserted on
     * the <em>values</em>, because that is the only way to see that the denied Thing's points were
     * genuinely excluded from the fold rather than merely absent from some label.
     */
    @Test
    public void restrictsEachPathToTheThingsPermittedForThatPath() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 10.0),
                point(t1, FLOW, 10, 20.0),
                point(t2, FLOW, 20, 3000.0),      // t2 denied on FLOW -> must not shift the average
                point(t1, RETURN, 0, 100.0),
                point(t2, RETURN, 10, 200.0)      // t2 permitted on RETURN
        )).toCompletableFuture().get();

        final Map<JsonPointer, Collection<ThingId>> allowed = new LinkedHashMap<>();
        allowed.put(FLOW, Collections.singletonList(t1));
        allowed.put(RETURN, Arrays.asList(t1, t2));

        final List<AggregatedTimeseriesResult> results = run(
                query(Arrays.asList(FLOW, RETURN), Aggregation.AVG), allowed);

        assertThat(results).hasSize(2);
        // avg(10,20) = 15 — NOT avg(10,20,3000).
        assertThat(doubleAt(seriesFor(results, FLOW), 0)).isCloseTo(15.0, within(1e-9));
        assertThat(doubleAt(seriesFor(results, RETURN), 0)).isCloseTo(150.0, within(1e-9));
    }

    /**
     * Fail-closed rule 1: a path <b>absent</b> from the map contributes nothing. Reading an absent
     * entry as "unrestricted" is the dangerous misinterpretation, so it gets its own test.
     */
    @Test
    public void aPathAbsentFromTheAllowListContributesNothing() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 10.0),
                point(t1, RETURN, 0, 100.0))).toCompletableFuture().get();

        final Map<JsonPointer, Collection<ThingId>> onlyFlow = new LinkedHashMap<>();
        onlyFlow.put(FLOW, Collections.singletonList(t1));
        // RETURN deliberately not mentioned at all.

        final List<AggregatedTimeseriesResult> results = run(
                query(Arrays.asList(FLOW, RETURN), Aggregation.AVG), onlyFlow);

        assertThat(results).hasSize(1);
        assertThat((Object) results.get(0).getPath()).isEqualTo(FLOW);
    }

    /** Fail-closed rule 2: a path mapped to an <b>empty</b> collection contributes nothing. */
    @Test
    public void aPathMappedToNoThingsContributesNothing() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 10.0),
                point(t1, RETURN, 0, 100.0))).toCompletableFuture().get();

        final Map<JsonPointer, Collection<ThingId>> emptyForReturn = new LinkedHashMap<>();
        emptyForReturn.put(FLOW, Collections.singletonList(t1));
        emptyForReturn.put(RETURN, Collections.<ThingId>emptyList());

        final List<AggregatedTimeseriesResult> results = run(
                query(Arrays.asList(FLOW, RETURN), Aggregation.AVG), emptyForReturn);

        assertThat(results).hasSize(1);
        assertThat((Object) results.get(0).getPath()).isEqualTo(FLOW);
    }

    /**
     * Fail-closed rule 3, and the one that matters most: when no path has any permitted Thing the
     * result must be empty. If the {@code $or} of per-path clauses collapsed to "no filter" instead
     * of "match nothing", this would quietly return the entire namespace.
     */
    @Test
    public void anEmptyAllowListLeaksNothing() throws Exception {
        writeSpreadAcrossThreeThings();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG),
                new LinkedHashMap<JsonPointer, Collection<ThingId>>());

        assertThat(results).isEmpty();
    }

    /** An allow-list naming a Thing that has no data must not invent a series. */
    @Test
    public void permittingAThingWithNoDataYieldsNoSeries() throws Exception {
        adapter.write(point(t1, FLOW, 0, 10.0)).toCompletableFuture().get();

        final Map<JsonPointer, Collection<ThingId>> allowed = new LinkedHashMap<>();
        allowed.put(FLOW, Collections.singletonList(t3));

        assertThat(run(query(Collections.singletonList(FLOW), Aggregation.AVG), allowed)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // discoverContributors — the input the withheld-count accuracy depends on
    // ---------------------------------------------------------------------------------------------

    /**
     * Contributors are grouped per path, not flattened. A Thing must appear only under the paths it
     * actually has data for, otherwise the caller would report it as "withheld" from a path it would
     * have contributed nothing to anyway.
     */
    @Test
    public void discoverContributorsGroupsThingsByPath() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 1.0),
                point(t1, RETURN, 0, 2.0),
                point(t2, FLOW, 0, 3.0),        // t2: FLOW only
                point(t3, RETURN, 0, 4.0)       // t3: RETURN only
        )).toCompletableFuture().get();

        final Map<JsonPointer, List<ThingId>> contributors = adapter.discoverContributors(
                query(Arrays.asList(FLOW, RETURN), Aggregation.AVG), 100)
                .toCompletableFuture().get();

        assertThat(contributors.get(FLOW)).containsExactlyInAnyOrder(t1, t2);
        assertThat(contributors.get(RETURN)).containsExactlyInAnyOrder(t1, t3);
    }

    /** A Thing whose only points fall outside the window is not a contributor. */
    @Test
    public void discoverContributorsHonoursTheTimeRange() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 1.0),
                // t2's point is a day later — outside [HOUR, HOUR_END).
                TimeseriesDataPoint.of(t2, FLOW, HOUR.plus(1, ChronoUnit.DAYS),
                        JsonValue.of(2.0), 1L, Collections.<String, String>emptyMap(), UNIT)
        )).toCompletableFuture().get();

        final Map<JsonPointer, List<ThingId>> contributors = adapter.discoverContributors(
                query(Collections.singletonList(FLOW), Aggregation.AVG), 100)
                .toCompletableFuture().get();

        assertThat(contributors.get(FLOW)).containsExactly(t1);
    }

    @Test
    public void discoverContributorsHonoursTagFilters() throws Exception {
        adapter.writeBatch(Arrays.asList(
                pointTagged(t1, FLOW, 0, 1.0, "building", "A"),
                pointTagged(t2, FLOW, 0, 2.0, "building", "B"))).toCompletableFuture().get();

        final Map<JsonPointer, List<ThingId>> contributors = adapter.discoverContributors(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.<GroupBy>emptyList(),
                        "eq(building,'A')", null, null), 100)
                .toCompletableFuture().get();

        assertThat(contributors.get(FLOW)).containsExactly(t1);
    }

    /**
     * The cap must let {@code limit + 1} distinct Things through. Returning exactly {@code limit}
     * would make "at the ceiling" indistinguishable from "over it", and the caller would authorize a
     * truncated contributor set — silently admitting whatever fell off the end.
     */
    @Test
    public void discoverContributorsAllowsOneThingBeyondTheLimitSoOverflowIsDetectable()
            throws Exception {

        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 1.0),
                point(t2, FLOW, 1, 2.0),
                point(t3, FLOW, 2, 3.0))).toCompletableFuture().get();

        final Map<JsonPointer, List<ThingId>> contributors = adapter.discoverContributors(
                query(Collections.singletonList(FLOW), Aggregation.AVG), 2)
                .toCompletableFuture().get();

        assertThat(distinct(contributors)).hasSize(3);
    }

    // ---------------------------------------------------------------------------------------------
    // Guard rails, tags and rendering
    // ---------------------------------------------------------------------------------------------

    /** Exceeding {@code maxGroups} must fail rather than truncate — through a real pipeline. */
    @Test
    public void exceedingMaxGroupsFailsInsteadOfTruncating() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 1.0),
                point(t2, FLOW, 1, 2.0),
                point(t3, FLOW, 2, 3.0))).toCompletableFuture().get();

        final CrossThingTimeseriesQuery threeGroupsCapAtTwo = query(
                Collections.singletonList(FLOW), Aggregation.AVG,
                Collections.singletonList(GroupBy.thingId()),
                null, null, 2);

        assertThatThrownBy(() -> adapter.queryCrossThing(threeGroupsCapAtTwo, null)
                .toCompletableFuture().get())
                .hasRootCauseInstanceOf(TimeseriesQueryInvalidException.class);
    }

    @Test
    public void groupsAtExactlyTheMaxGroupsLimitSucceed() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 1.0),
                point(t2, FLOW, 1, 2.0),
                point(t3, FLOW, 2, 3.0))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.singletonList(GroupBy.thingId()),
                        null, null, 3),
                null);

        assertThat(results).hasSize(3);
    }

    /**
     * Tags are frozen on each point at ingest, so grouping by a tag partitions by the state of the
     * world <em>when recorded</em>. Both series here belong to the same Thing, which "moved" from
     * building A to B mid-window — proving the semantics are point-in-time and not a lookup of the
     * Thing's current attributes.
     */
    @Test
    public void groupByTagPartitionsByTheTagValueFrozenOnEachPoint() throws Exception {
        adapter.writeBatch(Arrays.asList(
                pointTagged(t1, FLOW, 0, 10.0, "building", "A"),
                pointTagged(t1, FLOW, 10, 20.0, "building", "A"),
                pointTagged(t1, FLOW, 20, 90.0, "building", "B"))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.singletonList(GroupBy.tag("building")),
                        null, null, null),
                null);

        final Map<String, AggregatedTimeseriesResult> byBuilding = new LinkedHashMap<>();
        for (final AggregatedTimeseriesResult r : results) {
            byBuilding.put(r.getGroup().get("building"), r);
        }
        assertThat(byBuilding.keySet()).containsExactlyInAnyOrder("A", "B");
        assertThat(doubleAt(byBuilding.get("A"), 0)).isCloseTo(15.0, within(1e-9));
        assertThat(doubleAt(byBuilding.get("B"), 0)).isCloseTo(90.0, within(1e-9));
    }

    @Test
    public void tagFilterSelectsPointsAcrossThings() throws Exception {
        adapter.writeBatch(Arrays.asList(
                pointTagged(t1, FLOW, 0, 10.0, "building", "A"),
                pointTagged(t2, FLOW, 10, 20.0, "building", "A"),
                pointTagged(t2, FLOW, 20, 9000.0, "building", "B"))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.<GroupBy>emptyList(),
                        "eq(building,'A')", null, null),
                null);

        assertThat(results).hasSize(1);
        assertThat(doubleAt(results.get(0), 0)).isCloseTo(15.0, within(1e-9));
    }

    /**
     * A group spans many points whose tags may differ, so an aggregated result reports no tags at
     * all — the group identity is the right place to look instead.
     */
    @Test
    public void aggregatedResultsCarryTheUnitButNoTags() throws Exception {
        adapter.writeBatch(Arrays.asList(
                pointTagged(t1, FLOW, 0, 10.0, "building", "A"),
                pointTagged(t2, FLOW, 10, 20.0, "building", "B"))).toCompletableFuture().get();

        final AggregatedTimeseriesResult result = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG), null).get(0);

        assertThat(result.getMeta().getUnit()).contains(UNIT);
        assertThat(result.getMeta().getTags()).isEmpty();
        assertThat(result.getMeta().getCount()).isEqualTo(result.getData().size());
    }

    /** Buckets with no data across any Thing are interpolated and flagged, not silently omitted. */
    @Test
    public void fillLinearInterpolatesEmptyBucketsAndFlagsThem() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 5, 10.0),
                point(t2, FLOW, 45, 40.0))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.<GroupBy>emptyList(), null,
                        FillStrategy.LINEAR, null,
                        Duration.ofMinutes(20)),
                null);

        final List<TimeseriesDataValue> data = results.get(0).getData();
        assertThat(data).hasSize(3);
        assertThat(data.get(0).isGap()).isFalse();
        assertThat(data.get(1).isGap()).isTrue();
        assertThat(data.get(2).isGap()).isFalse();
        // Halfway between 10 and 40.
        assertThat(doubleAt(results.get(0), 1)).isCloseTo(25.0, within(1e-9));
    }

    @Test
    public void withoutFillEmptyBucketsAreAbsent() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 5, 10.0),
                point(t2, FLOW, 45, 40.0))).toCompletableFuture().get();

        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG,
                        Collections.<GroupBy>emptyList(), null,
                        null, null, Duration.ofMinutes(20)),
                null);

        assertThat(results.get(0).getData()).hasSize(2);
    }

    @Test
    public void aNamespaceWithNoDataYieldsNoSeriesRatherThanAnError() throws Exception {
        final List<AggregatedTimeseriesResult> results = run(
                query(Collections.singletonList(FLOW), Aggregation.AVG), null);

        assertThat(results).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures & helpers
    // ---------------------------------------------------------------------------------------------

    /** t1: 10,20,30 at :00,:10,:20 — t2: 40,50,60 at :05,:15,:25 — t3: 90 at :30. */
    private void writeSpreadAcrossThreeThings() throws Exception {
        adapter.writeBatch(Arrays.asList(
                point(t1, FLOW, 0, 10.0),
                point(t1, FLOW, 10, 20.0),
                point(t1, FLOW, 20, 30.0),
                point(t2, FLOW, 5, 40.0),
                point(t2, FLOW, 15, 50.0),
                point(t2, FLOW, 25, 60.0),
                point(t3, FLOW, 30, 90.0))).toCompletableFuture().get();
    }

    private double singleValue(final Aggregation aggregation) throws Exception {
        final List<AggregatedTimeseriesResult> results =
                run(query(Collections.singletonList(FLOW), aggregation), null);
        assertThat(results).hasSize(1);
        return doubleAt(results.get(0), 0);
    }

    private List<AggregatedTimeseriesResult> run(final CrossThingTimeseriesQuery query,
            @Nullable final Map<JsonPointer, Collection<ThingId>> permittedThingsPerPath)
            throws Exception {

        return adapter.queryCrossThing(query, permittedThingsPerPath).toCompletableFuture().get();
    }

    private CrossThingTimeseriesQuery query(final List<JsonPointer> paths,
            final Aggregation aggregation) {

        return query(paths, aggregation, Collections.<GroupBy>emptyList(),
                null, null, null);
    }

    private CrossThingTimeseriesQuery query(final List<JsonPointer> paths,
            final Aggregation aggregation,
            final List<GroupBy> groupBy,
            @Nullable final String filter,
            @Nullable final FillStrategy fill,
            @Nullable final Integer maxGroups) {

        return query(paths, aggregation, groupBy, filter, fill, maxGroups, ONE_HOUR);
    }

    private CrossThingTimeseriesQuery query(final List<JsonPointer> paths,
            final Aggregation aggregation,
            final List<GroupBy> groupBy,
            @Nullable final String filter,
            @Nullable final FillStrategy fill,
            @Nullable final Integer maxGroups,
            final Duration step) {

        return CrossThingTimeseriesQuery.of(namespace, paths, HOUR, HOUR_END, step, aggregation,
                groupBy, filter, null, fill, maxGroups);
    }

    private TimeseriesDataPoint point(final ThingId thingId, final JsonPointer path,
            final int minute, final double value) {

        return TimeseriesDataPoint.of(thingId, path, HOUR.plus(minute, ChronoUnit.MINUTES),
                JsonValue.of(value), 1L, Collections.<String, String>emptyMap(), UNIT);
    }

    private TimeseriesDataPoint pointTagged(final ThingId thingId, final JsonPointer path,
            final int minute, final double value, final String tagKey, final String tagValue) {

        return TimeseriesDataPoint.of(thingId, path, HOUR.plus(minute, ChronoUnit.MINUTES),
                JsonValue.of(value), 1L, Collections.singletonMap(tagKey, tagValue), UNIT);
    }

    private static AggregatedTimeseriesResult seriesFor(
            final List<AggregatedTimeseriesResult> results, final JsonPointer path) {

        for (final AggregatedTimeseriesResult result : results) {
            if (result.getPath().equals(path)) {
                return result;
            }
        }
        throw new AssertionError("No series for path <" + path + "> in " + results);
    }

    private static double doubleAt(final AggregatedTimeseriesResult result, final int index) {
        return result.getData().get(index).getValue()
                .orElseThrow(() -> new AssertionError("Bucket " + index + " carries no value"))
                .asDouble();
    }

    private static List<ThingId> distinct(final Map<JsonPointer, List<ThingId>> contributors) {
        final List<ThingId> all = new ArrayList<>();
        for (final List<ThingId> perPath : contributors.values()) {
            for (final ThingId thingId : perPath) {
                if (!all.contains(thingId)) {
                    all.add(thingId);
                }
            }
        }
        return all;
    }
}
