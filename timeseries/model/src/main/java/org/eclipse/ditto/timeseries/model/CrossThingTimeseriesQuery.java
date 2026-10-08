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

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Describes a timeseries aggregation spanning <em>many</em> Things within a single namespace —
 * the counterpart to the single-Thing {@link TimeseriesQuery}.
 *
 * <h2>Why this is a separate type</h2>
 * A cross-Thing query has no {@code thingId}, always aggregates (raw cross-Thing reads are
 * unbounded by construction), and returns one series per <em>group</em> rather than per Thing. Those
 * differences are load-bearing enough that overloading {@link TimeseriesQuery} — whose
 * {@code getThingId()} is non-null and whose result type is keyed by Thing — would weaken both.
 *
 * <h2>{@code filter} selects points, not Things</h2>
 * Tag filters match the tags <em>frozen at ingest</em> on each data point, so they select points by
 * the state of the world when the measurement was recorded. That is deliberately different from
 * selecting Things by their <em>current</em> attributes: for a Thing moved from building A to
 * building B, {@code tagFilter=building:A} returns only the points recorded while it was in A,
 * whereas a state-based selection would return its whole history. Both are useful; they are not
 * interchangeable.
 *
 * <h2>Authorization is never encoded here</h2>
 * The query carries no notion of who may read it. Access is decided against live policy state at
 * execution time, so a subject granted access today sees history ingested before the grant, and a
 * revoked subject immediately loses history it could previously read. Nothing about authorization is
 * stored alongside the data points.
 *
 * @since 4.0.0
 */
public interface CrossThingTimeseriesQuery extends Jsonifiable<JsonObject> {

    /**
     * Hard upper bound on {@code maxGroups}. A cross-Thing aggregation materialises one series per
     * group in service heap, so the cap is bounded independently of what a caller asks for.
     */
    int MAX_GROUPS_CEILING = 10_000;

    /**
     * The {@code maxGroups} applied when a caller does not specify one.
     */
    int DEFAULT_MAX_GROUPS = 1_000;

    /**
     * Returns a new {@code CrossThingTimeseriesQuery}.
     *
     * @param namespace the namespace whose Things are aggregated. Cross-namespace queries are not
     * supported; storage is partitioned per namespace.
     * @param paths the paths within each Thing to aggregate. Must not be empty.
     * @param from inclusive lower bound of the time range.
     * @param to exclusive upper bound of the time range.
     * @param step the bucket width. Required — a cross-Thing query always downsamples.
     * @param aggregation the per-bucket aggregation. Required, and must be one of the bucketed
     * aggregations ({@link Aggregation#requiresStep()}).
     * @param groupBy the grouping dimensions; may be empty, meaning one series per path across all
     * matching Things.
     * @param filter an RQL predicate over ingest-time tags, or {@code null} for no filtering.
     * @param timezone timezone for calendar-aligned bucket boundaries; may be {@code null} for UTC.
     * @param fillStrategy how empty buckets are filled; may be {@code null} for no filling.
     * @param maxGroups a cap on the number of distinct groups returned; may be {@code null} for the
     * backend default. Exceeding the cap fails the query rather than truncating it.
     * @return the new query.
     * @throws NullPointerException if a required argument is {@code null}.
     * @throws TimeseriesQueryInvalidException if the combination of parameters is not valid.
     */
    static CrossThingTimeseriesQuery of(final String namespace,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            final Duration step,
            final Aggregation aggregation,
            final List<GroupBy> groupBy,
            @Nullable final String filter,
            @Nullable final ZoneId timezone,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer maxGroups) {

        return ImmutableCrossThingTimeseriesQuery.of(namespace, paths, from, to, step, aggregation,
                groupBy, filter, timezone, fillStrategy, maxGroups);
    }

    /**
     * Parses a {@code CrossThingTimeseriesQuery} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the parsed query.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if a field has an invalid value.
     */
    static CrossThingTimeseriesQuery fromJson(final JsonObject jsonObject) {
        return ImmutableCrossThingTimeseriesQuery.fromJson(jsonObject);
    }

    /**
     * @return the namespace whose Things are aggregated.
     */
    String getNamespace();

    /**
     * @return the paths aggregated within each Thing; never empty.
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
     * @return the bucket width; always present.
     */
    Duration getStep();

    /**
     * @return the per-bucket aggregation; always present.
     */
    Aggregation getAggregation();

    /**
     * @return the grouping dimensions, in declaration order; may be empty.
     */
    List<GroupBy> getGroupBy();

    /**
     * @return the ingest-time tag predicates, ANDed; may be empty.
     */
    Optional<String> getFilter();

    /**
     * @return the timezone for calendar-aligned bucketing, if set.
     */
    Optional<ZoneId> getTimezone();

    /**
     * @return the gap-fill strategy, if set.
     */
    Optional<FillStrategy> getFillStrategy();

    /**
     * @return the caller-supplied cap on distinct groups, if set.
     */
    Optional<Integer> getMaxGroups();
}
