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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.Test;

/**
 * Unit tests for {@link MongoDbTimeseriesAdapter}'s cross-Thing aggregation — the pipeline shape and
 * the service-side fold of the returned series documents. Exercised without a live MongoDB; the
 * end-to-end behaviour against a real Time Series collection is covered by
 * {@code MongoDbTimeseriesAdapterIT}.
 */
public final class MongoDbTimeseriesAdapterCrossThingTest {

    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final JsonPointer PATH =
            JsonPointer.of("/features/circuit/properties/flowTemperature");
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T06:00:00Z");
    private static final Duration STEP = Duration.ofHours(1);
    /** The single Thing these pipeline-shape tests authorize on {@link #PATH}. */
    private static final ThingId PERMITTED_THING = ThingId.of(NAMESPACE, "circuit-1");
    /** Stand-in for the adapter's configured max-query-result-size; high enough not to interfere. */
    private static final int MAX_POINTS = 100_000;

    private static CrossThingTimeseriesQuery query(final List<GroupBy> groupBy,
            @Nullable final String filter,
            @Nullable final FillStrategy fill,
            @Nullable final Integer maxGroups) {

        return CrossThingTimeseriesQuery.of(NAMESPACE, Collections.singletonList(PATH), FROM, TO,
                STEP, Aggregation.AVG, groupBy, filter, null, fill, maxGroups);
    }

    // ---------------------------------------------------------------------------------------------
    // Pipeline shape
    // ---------------------------------------------------------------------------------------------

    /**
     * The pipeline must be: match → sort → group(bucket) → sort → group(series) → sort → limit.
     * The second group is what makes the driver return one document per series instead of one per
     * bucket, and the trailing limit is what makes a group-cap overflow detectable.
     */
    @Test
    public void pipelineFoldsBucketsIntoSeriesAndBoundsGroups() {
        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(
                query(Collections.singletonList(GroupBy.tag("building")),
                        null, null, 5),
                permitted(), 5);

        assertThat(pipeline).hasSize(7);
        final String rendered = render(pipeline);
        assertThat(rendered).contains("$match");
        assertThat(rendered).contains("$group");
        assertThat(rendered).contains("$limit");
        // maxGroups + 1: one extra row so an overflow can be reported rather than truncated.
        assertThat(render(pipeline.get(6))).contains("6");
    }

    @Test
    public void pipelineFiltersByRequestedPath() {
        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(
                query(Collections.<GroupBy>emptyList(), null,
                        null, null),
                permitted(), 1000);

        assertThat(render(pipeline.get(0))).contains(PATH.toString());
    }

    @Test
    public void pipelineFiltersByTagsInMetaField() {
        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(
                query(Collections.<GroupBy>emptyList(),
                        "eq(building,'A')", null, null),
                permitted(), 1000);

        // Tags live in the Time Series metaField, so the predicate must address meta.tags.<key>
        // to stay index-supported.
        assertThat(render(pipeline.get(0))).contains("meta.tags.building");
    }

    @Test
    public void pipelineAppliesPerPathAllowListWhenGiven() {
        final Map<JsonPointer, Collection<ThingId>> allow = Map.of(PATH,
                Arrays.asList(ThingId.of(NAMESPACE, "heatsource-1"),
                        ThingId.of(NAMESPACE, "heatsource-2")));

        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(
                query(Collections.<GroupBy>emptyList(), null,
                        null, null),
                allow, 1000);

        final String match = render(pipeline.get(0));
        assertThat(match).contains("meta.thingId");
        assertThat(match).contains("heatsource-1");
        assertThat(match).contains("heatsource-2");
        assertThat(match).contains(PATH.toString());
    }

    /**
     * The core of path-granular access: each readable path gets its own {@code (path, thingIds)} clause,
     * OR'd together — so a Thing withheld from one path still contributes to another.
     */
    @Test
    public void pipelineEmitsOneClausePerPathWithItsOwnThings() {
        final JsonPointer other = JsonPointer.of("/features/circuit/properties/returnTemperature");
        final ThingId a = ThingId.of(NAMESPACE, "heatsource-a");
        final ThingId b = ThingId.of(NAMESPACE, "heatsource-b");
        final Map<JsonPointer, Collection<ThingId>> allow = new LinkedHashMap<>();
        allow.put(PATH, Arrays.asList(a, b));
        allow.put(other, Collections.singletonList(a));

        final CrossThingTimeseriesQuery q = CrossThingTimeseriesQuery.of(NAMESPACE,
                Arrays.asList(PATH, other), FROM, TO, STEP, Aggregation.AVG,
                Collections.<GroupBy>emptyList(), null,
                null, null, null);

        final String match = render(MongoDbTimeseriesAdapter.crossThingPipeline(q, allow, 1000).get(0));
        assertThat(match).contains("$or");
        assertThat(match).contains(PATH.toString());
        assertThat(match).contains(other.toString());
        assertThat(match).contains("heatsource-a");
        assertThat(match).contains("heatsource-b");
    }

    /** A path with no permitted Things must contribute no clause at all — never an unfiltered one. */
    @Test
    public void pipelineOmitsPathsWithNoPermittedThings() {
        final JsonPointer other = JsonPointer.of("/features/circuit/properties/returnTemperature");
        final Map<JsonPointer, Collection<ThingId>> allow = new LinkedHashMap<>();
        allow.put(PATH, Collections.singletonList(ThingId.of(NAMESPACE, "heatsource-a")));
        allow.put(other, Collections.emptyList());

        final CrossThingTimeseriesQuery q = CrossThingTimeseriesQuery.of(NAMESPACE,
                Arrays.asList(PATH, other), FROM, TO, STEP, Aggregation.AVG,
                Collections.<GroupBy>emptyList(), null,
                null, null, null);

        final String match = render(MongoDbTimeseriesAdapter.crossThingPipeline(q, allow, 1000).get(0));
        assertThat(match).contains(PATH.toString());
        assertThat(match).doesNotContain(other.toString());
    }

    @Test
    public void pipelineAlwaysConstrainsThingIdsToTheAllowList() {
        // There is no "namespace-wide" mode: the allow-list is required, so the $match must always
        // carry an explicit meta.thingId constraint. A pipeline without one would scan the whole
        // namespace regardless of what the caller was authorized for.
        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(
                query(Collections.<GroupBy>emptyList(), null,
                        null, null),
                permitted(), 1000);

        final String match = render(pipeline.get(0));
        assertThat(match).contains("meta.thingId");
        assertThat(match).contains(PERMITTED_THING.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Fold of returned series documents
    // ---------------------------------------------------------------------------------------------

    @Test
    public void foldBuildsOneResultPerSeriesWithGroupIdentity() {
        final List<Document> seriesDocs = Arrays.asList(
                seriesDoc("A", Arrays.asList(bucket(FROM, 21.0), bucket(FROM.plus(STEP), 22.0))),
                seriesDoc("B", Collections.singletonList(bucket(FROM, 30.0))));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, null, null),
                        seriesDocs, 1000, MAX_POINTS);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getGroup()).containsEntry("building", "A");
        assertThat((Object) results.get(0).getPath()).isEqualTo(PATH);
        assertThat(results.get(0).getData()).hasSize(2);
        assertThat(results.get(0).getMeta().getUnit()).contains("cel");
        assertThat(results.get(1).getGroup()).containsEntry("building", "B");
        assertThat(results.get(1).getData()).hasSize(1);
    }

    /**
     * {@code $group} output order is not contractual, so the fold must sort each series' buckets
     * itself — the fill grid depends on ascending order actually holding.
     */
    @Test
    public void foldSortsBucketsChronologicallyEvenIfMongoReturnsThemOutOfOrder() {
        final List<Document> seriesDocs = Collections.singletonList(seriesDoc("A", Arrays.asList(
                bucket(FROM.plus(Duration.ofHours(2)), 23.0),
                bucket(FROM, 21.0),
                bucket(FROM.plus(STEP), 22.0))));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, null, null),
                        seriesDocs, 1000, MAX_POINTS);

        final List<Instant> timestamps = new ArrayList<>();
        for (final TimeseriesDataValue value : results.get(0).getData()) {
            timestamps.add(value.getTimestamp());
        }
        assertThat(timestamps).containsExactly(FROM, FROM.plus(STEP), FROM.plus(Duration.ofHours(2)));
    }

    @Test
    public void foldAppliesGapFillPerSeries() {
        // Buckets 0 and 3 present, 1 and 2 missing → linear fill must interpolate the two gaps.
        final List<Document> seriesDocs = Collections.singletonList(seriesDoc("A", Arrays.asList(
                bucket(FROM, 10.0),
                bucket(FROM.plus(Duration.ofHours(3)), 40.0))));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, FillStrategy.LINEAR, null),
                        seriesDocs, 1000, MAX_POINTS);

        final List<TimeseriesDataValue> data = results.get(0).getData();
        assertThat(data).hasSize(4);
        assertThat(data.get(1).isGap()).isTrue();
        assertThat(data.get(2).isGap()).isTrue();
        assertThat(data.get(1).getValue().map(JsonValue::asDouble)).contains(20.0);
        assertThat(data.get(2).getValue().map(JsonValue::asDouble)).contains(30.0);
    }

    @Test
    public void foldWithoutGroupByProducesSingleSeriesWithEmptyGroup() {
        final List<Document> seriesDocs = Collections.singletonList(
                new Document("_id", new Document("p", PATH.toString()))
                        .append("points", Collections.singletonList(bucket(FROM, 21.0)))
                        .append("unit", "cel"));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.<GroupBy>emptyList(), null,
                                null, null),
                        seriesDocs, 1000, MAX_POINTS);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getGroup()).isEmpty();
    }

    /**
     * A silently truncated result set would look like a complete answer and misreport anything the
     * caller computes on top of it, so exceeding the cap must fail.
     */
    @Test
    public void foldRejectsMoreGroupsThanTheCap() {
        final List<Document> seriesDocs = Arrays.asList(
                seriesDoc("A", Collections.singletonList(bucket(FROM, 1.0))),
                seriesDoc("B", Collections.singletonList(bucket(FROM, 2.0))),
                seriesDoc("C", Collections.singletonList(bucket(FROM, 3.0))));

        assertThatExceptionOfType(TimeseriesQueryInvalidException.class)
                .isThrownBy(() -> MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, null, 2),
                        seriesDocs, 2, MAX_POINTS))
                .withMessageContaining("more than 2 distinct groups");
    }

    @Test
    public void foldAcceptsExactlyTheCap() {
        final List<Document> seriesDocs = Arrays.asList(
                seriesDoc("A", Collections.singletonList(bucket(FROM, 1.0))),
                seriesDoc("B", Collections.singletonList(bucket(FROM, 2.0))));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, null, 2),
                        seriesDocs, 2, MAX_POINTS);

        assertThat(results).hasSize(2);
    }

    /**
     * A point that carries no value for a grouped tag must still produce a series with the same set
     * of keys, so clients can rely on a uniform shape.
     */
    @Test
    public void foldSurfacesMissingTagValueAsEmptyString() {
        final List<Document> seriesDocs = Collections.singletonList(
                new Document("_id", new Document("p", PATH.toString()).append("g_building", null))
                        .append("points", Collections.singletonList(bucket(FROM, 21.0)))
                        .append("unit", "cel"));

        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.singletonList(GroupBy.tag("building")),
                                null, null, null),
                        seriesDocs, 1000, MAX_POINTS);

        assertThat(results.get(0).getGroup()).containsEntry("building", "");
    }

    @Test
    public void foldHandlesEmptySeriesList() {
        final List<AggregatedTimeseriesResult> results =
                MongoDbTimeseriesAdapter.buildCrossThingResults(
                        query(Collections.<GroupBy>emptyList(), null,
                                null, null),
                        Collections.<Document>emptyList(), 1000, MAX_POINTS);

        assertThat(results).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static Document seriesDoc(final String building, final List<Document> points) {
        return new Document("_id",
                new Document("p", PATH.toString()).append("g_building", building))
                .append("points", points)
                .append("unit", "cel");
    }

    private static Document bucket(final Instant timestamp, final double value) {
        // The driver hands back BSON dates as java.util.Date.
        return new Document("t", Date.from(timestamp)).append("v", value);
    }

    private static String render(final List<Bson> pipeline) {
        final StringBuilder builder = new StringBuilder();
        for (final Bson stage : pipeline) {
            builder.append(render(stage)).append('\n');
        }
        return builder.toString();
    }

    private static String render(final Bson stage) {
        return stage.toBsonDocument(Document.class,
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }

    /** Guards against the ZoneId overload silently changing bucket alignment. */
    @Test
    public void pipelineCarriesTimezoneIntoDateTrunc() {
        final CrossThingTimeseriesQuery tzQuery = CrossThingTimeseriesQuery.of(NAMESPACE,
                Collections.singletonList(PATH), FROM, TO, Duration.ofDays(1), Aggregation.AVG,
                Collections.<GroupBy>emptyList(), null,
                ZoneId.of("Europe/Berlin"), null, null);

        final List<Bson> pipeline = MongoDbTimeseriesAdapter.crossThingPipeline(tzQuery, permitted(), 1000);

        assertThat(render(pipeline)).contains("Europe/Berlin");
    }

    /**
     * The allow-list these pipeline-shape tests run with: one Thing permitted on {@link #PATH}.
     * The adapter takes no "unrestricted" sentinel, so even a test that is not about authorization
     * has to say what is readable.
     */
    private static Map<JsonPointer, Collection<ThingId>> permitted() {
        final Map<JsonPointer, Collection<ThingId>> allowed = new LinkedHashMap<>();
        allowed.put(PATH, Collections.singletonList(PERMITTED_THING));
        return allowed;
    }
}
