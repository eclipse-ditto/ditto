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
package org.eclipse.ditto.timeseries.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Describes a single-Thing timeseries query: which paths to retrieve, the time range, and the
 * optional downsampling, aggregation, fill, limit and timezone parameters.
 * <p>
 * Cross-Thing aggregation queries (with RQL filter and {@code groupBy}) are represented by a
 * separate type and are not modelled here.
 *
 * @since 4.0.0
 */
public interface TimeseriesQuery extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@code TimeseriesQuery} with the given fields. Optional fields may be
     * {@code null}.
     *
     * @param thingId the Thing to query.
     * @param paths the paths within the Thing whose timeseries are requested. Must not be
     * {@code null}; may be empty.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @param step downsampling interval; may be {@code null} for raw queries.
     * @param aggregation the aggregation function to apply per bucket; may be {@code null}.
     * @param fillStrategy how empty buckets are filled when downsampling; may be {@code null}.
     * @param limit a maximum number of data points to return; may be {@code null}.
     * @param timezone the timezone used to align step boundaries; may be {@code null} (UTC is used).
     * @return the new query.
     * @throws NullPointerException if any non-{@code @Nullable} argument is {@code null}.
     */
    static TimeseriesQuery of(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            @Nullable final Duration step,
            @Nullable final Aggregation aggregation,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer limit,
            @Nullable final ZoneId timezone) {

        return ImmutableTimeseriesQuery.of(
                thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, null, null,
                null);
    }

    /**
     * Returns a new {@code TimeseriesQuery} including the {@code percentile} parameter (required when
     * {@code aggregation} is {@link Aggregation#PERCENTILE}). Optional fields may be {@code null}.
     *
     * @param thingId the Thing to query.
     * @param paths the paths within the Thing whose timeseries are requested.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @param step downsampling interval; may be {@code null} for raw queries.
     * @param aggregation the aggregation function to apply per bucket; may be {@code null}.
     * @param fillStrategy how empty buckets are filled when downsampling; may be {@code null}.
     * @param limit a maximum number of data points to return; may be {@code null}.
     * @param timezone the timezone used to align step boundaries; may be {@code null} (UTC is used).
     * @param percentile the percentile in {@code [0, 100]} for {@link Aggregation#PERCENTILE}; may be
     * {@code null} for other aggregations.
     * @return the new query.
     * @throws NullPointerException if any non-{@code @Nullable} argument is {@code null}.
     */
    static TimeseriesQuery of(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            @Nullable final Duration step,
            @Nullable final Aggregation aggregation,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer limit,
            @Nullable final ZoneId timezone,
            @Nullable final Double percentile) {

        return ImmutableTimeseriesQuery.of(
                thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, percentile,
                null, null);
    }

    /**
     * Returns a new {@code TimeseriesQuery} including the {@code cursor} pagination parameter.
     * Optional fields may be {@code null}.
     * <p>
     * A {@code cursor} resumes a raw read after a previous page (keyset pagination) and is only valid
     * for a single-path raw query — it must not be combined with {@code aggregation}, {@code step} or
     * {@code fillStrategy}, nor with a multi-path request. Those combinations are rejected with
     * {@link TimeseriesQueryInvalidException} (HTTP 400).
     *
     * @param thingId the Thing to query.
     * @param paths the paths within the Thing whose timeseries are requested.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @param step downsampling interval; may be {@code null} for raw queries.
     * @param aggregation the aggregation function to apply per bucket; may be {@code null}.
     * @param fillStrategy how empty buckets are filled when downsampling; may be {@code null}.
     * @param limit a maximum number of data points to return (the page size); may be {@code null}.
     * @param timezone the timezone used to align step boundaries; may be {@code null} (UTC is used).
     * @param percentile the percentile in {@code [0, 100]} for {@link Aggregation#PERCENTILE}; may be
     * {@code null} for other aggregations.
     * @param cursor an opaque pagination cursor from a previous response's {@code nextCursor}; may be
     * {@code null} to start from the beginning of the range.
     * @return the new query.
     * @throws NullPointerException if any non-{@code @Nullable} argument is {@code null}.
     */
    static TimeseriesQuery of(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            @Nullable final Duration step,
            @Nullable final Aggregation aggregation,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer limit,
            @Nullable final ZoneId timezone,
            @Nullable final Double percentile,
            @Nullable final String cursor) {

        return ImmutableTimeseriesQuery.of(
                thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, percentile,
                cursor, null);
    }

    /**
     * Returns a new {@code TimeseriesQuery} including the {@code order} parameter controlling the
     * chronological direction of a raw read. Optional fields may be {@code null}.
     * <p>
     * {@link SortOrder#DESC} (newest first) is only supported for raw reads — it must not be combined
     * with {@code step}, {@code aggregation} or {@code fillStrategy}, which is rejected with
     * {@link TimeseriesQueryInvalidException} (HTTP 400). {@code null} defaults to
     * {@link SortOrder#ASC}.
     *
     * @param thingId the Thing to query.
     * @param paths the paths within the Thing whose timeseries are requested.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @param step downsampling interval; may be {@code null} for raw queries.
     * @param aggregation the aggregation function to apply per bucket; may be {@code null}.
     * @param fillStrategy how empty buckets are filled when downsampling; may be {@code null}.
     * @param limit a maximum number of data points to return (the page size); may be {@code null}.
     * @param timezone the timezone used to align step boundaries; may be {@code null} (UTC is used).
     * @param percentile the percentile in {@code [0, 100]} for {@link Aggregation#PERCENTILE}; may be
     * {@code null} for other aggregations.
     * @param cursor an opaque pagination cursor from a previous response's {@code nextCursor}; may be
     * {@code null} to start from the beginning of the range.
     * @param order the chronological order of a raw read; may be {@code null} for the default
     * {@link SortOrder#ASC}.
     * @return the new query.
     * @throws NullPointerException if any non-{@code @Nullable} argument is {@code null}.
     */
    static TimeseriesQuery of(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            @Nullable final Duration step,
            @Nullable final Aggregation aggregation,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer limit,
            @Nullable final ZoneId timezone,
            @Nullable final Double percentile,
            @Nullable final String cursor,
            @Nullable final SortOrder order) {

        return ImmutableTimeseriesQuery.of(
                thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, percentile,
                cursor, order);
    }

    /**
     * Returns a {@code TimeseriesQuery} with only the required fields populated (raw query).
     *
     * @param thingId the Thing to query.
     * @param paths the paths within the Thing whose timeseries are requested.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @return the new query.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static TimeseriesQuery of(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to) {

        return ImmutableTimeseriesQuery.of(
                thingId, paths, from, to, null, null, null, null, null, null, null, null);
    }

    /**
     * Parses a {@code TimeseriesQuery} from the given JSON object.
     *
     * @param jsonObject the JSON object representing a query.
     * @return the parsed query.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if a field has an invalid value.
     */
    static TimeseriesQuery fromJson(final JsonObject jsonObject) {
        return ImmutableTimeseriesQuery.fromJson(jsonObject);
    }

    /**
     * @return the Thing this query targets.
     */
    ThingId getThingId();

    /**
     * @return the paths within the Thing whose timeseries are requested. Always non-null; may be
     * empty. The returned list is unmodifiable.
     */
    List<JsonPointer> getPaths();

    /**
     * @return inclusive lower bound of the time range.
     */
    Instant getFrom();

    /**
     * @return exclusive upper bound of the time range.
     */
    Instant getTo();

    /**
     * @return the downsampling interval if set, or empty for a raw query.
     */
    Optional<Duration> getStep();

    /**
     * @return the aggregation function if set.
     */
    Optional<Aggregation> getAggregation();

    /**
     * @return the fill strategy for empty buckets if set.
     */
    Optional<FillStrategy> getFillStrategy();

    /**
     * @return the maximum number of data points if set.
     */
    Optional<Integer> getLimit();

    /**
     * @return the timezone used for step alignment if set; UTC is the default when absent.
     */
    Optional<ZoneId> getTimezone();

    /**
     * @return the percentile in {@code [0, 100]} for {@link Aggregation#PERCENTILE}, if set.
     */
    Optional<Double> getPercentile();

    /**
     * @return the opaque pagination cursor if set — a keyset position from a previous response's
     * {@code nextCursor}, resuming a raw read after that page. Empty for the first page. See
     * {@link TimeseriesCursor}.
     */
    Optional<String> getCursor();

    /**
     * @return the chronological order of a raw read if set. Empty means the default
     * {@link SortOrder#ASC} (oldest first).
     */
    Optional<SortOrder> getOrder();

    /**
     * @return the tag filters restricting the query to points whose resolved tags match every
     * given key/value pair (logical AND). Always non-null; may be empty (no tag filtering). The
     * returned map is unmodifiable.
     */
    Map<String, String> getTagFilters();

    /**
     * Returns a copy of this query with the given tag filters. Only points whose stored tags match
     * every key/value pair are returned. Modelled as a wither rather than another {@code of(...)}
     * parameter to keep the factory signatures manageable.
     *
     * @param tagFilters the tag key/value pairs to match (logical AND); may be empty to clear.
     * @return a copy of this query carrying the given tag filters.
     * @throws NullPointerException if {@code tagFilters} is {@code null}.
     */
    TimeseriesQuery withTagFilters(Map<String, String> tagFilters);

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link TimeseriesQuery}.
     */
    final class JsonFields {

        /**
         * The Thing ID.
         */
        public static final JsonFieldDefinition<String> THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The list of paths to retrieve, encoded as a JSON array of strings.
         */
        public static final JsonFieldDefinition<JsonArray> PATHS =
                JsonFactory.newJsonArrayFieldDefinition("paths", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The ISO-8601 timestamp marking the inclusive start of the time range.
         */
        public static final JsonFieldDefinition<String> FROM =
                JsonFactory.newStringFieldDefinition("from", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The ISO-8601 timestamp marking the exclusive end of the time range.
         */
        public static final JsonFieldDefinition<String> TO =
                JsonFactory.newStringFieldDefinition("to", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The downsampling interval as an ISO-8601 duration string (e.g. {@code PT1H}). Optional.
         */
        public static final JsonFieldDefinition<String> STEP =
                JsonFactory.newStringFieldDefinition("step", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The aggregation function name in wire format (e.g. {@code avg}). Optional.
         */
        public static final JsonFieldDefinition<String> AGGREGATION =
                JsonFactory.newStringFieldDefinition("aggregation", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The fill-strategy name in wire format (e.g. {@code previous}). Optional.
         */
        public static final JsonFieldDefinition<String> FILL_STRATEGY =
                JsonFactory.newStringFieldDefinition("fillStrategy", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The maximum number of data points to return. Optional.
         */
        public static final JsonFieldDefinition<Integer> LIMIT =
                JsonFactory.newIntFieldDefinition("limit", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The timezone identifier for step alignment (e.g. {@code Europe/Berlin}). Optional.
         */
        public static final JsonFieldDefinition<String> TIMEZONE =
                JsonFactory.newStringFieldDefinition("timezone", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The percentile value (0-100) for the {@code percentile} aggregation. Optional.
         */
        public static final JsonFieldDefinition<Double> PERCENTILE =
                JsonFactory.newDoubleFieldDefinition("percentile", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The opaque pagination cursor. Optional.
         */
        public static final JsonFieldDefinition<String> CURSOR =
                JsonFactory.newStringFieldDefinition("cursor", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The sort order (wire form {@code asc}/{@code desc}). Optional; defaults to {@code asc}.
         */
        public static final JsonFieldDefinition<String> ORDER =
                JsonFactory.newStringFieldDefinition("order", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The tag filters as a JSON object of key/value pairs. Optional.
         */
        public static final JsonFieldDefinition<JsonObject> TAG_FILTERS =
                JsonFactory.newJsonObjectFieldDefinition("tagFilters", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
