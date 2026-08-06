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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link CrossThingTimeseriesQuery}.
 * <p>
 * All semantic validation lives in {@link #of} so that it applies identically across HTTP,
 * WebSocket and Connectivity — the same discipline {@code ImmutableTimeseriesQuery} follows.
 *
 * @since 4.0.0
 */
@Immutable
final class ImmutableCrossThingTimeseriesQuery implements CrossThingTimeseriesQuery {

    private final String namespace;
    private final List<JsonPointer> paths;
    private final Instant from;
    private final Instant to;
    private final Duration step;
    private final Aggregation aggregation;
    private final List<GroupBy> groupBy;
    @Nullable private final String filter;
    @Nullable private final ZoneId timezone;
    @Nullable private final FillStrategy fillStrategy;
    @Nullable private final Integer maxGroups;

    private ImmutableCrossThingTimeseriesQuery(final String namespace,
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

        this.namespace = namespace;
        // Deduplicated, order preserved. A repeated path is harmless to the caller's intent but was
        // not harmless downstream: the gate compares the number of *permitted* paths (a Set) against
        // the number of *requested* paths, so "?paths=/a,/a" counted 1 against 2 and produced a 403
        // that read as a permission denial. It also emitted a redundant $or clause in the access
        // filter and a duplicate result series.
        this.paths = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(paths)));
        this.from = from;
        this.to = to;
        this.step = step;
        this.aggregation = aggregation;
        this.groupBy = Collections.unmodifiableList(new ArrayList<>(groupBy));
        this.filter = filter;
        this.timezone = timezone;
        this.fillStrategy = fillStrategy;
        this.maxGroups = maxGroups;
    }

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

        checkNotNull(namespace, "namespace");
        checkNotNull(paths, "paths");
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        checkNotNull(step, "step");
        checkNotNull(aggregation, "aggregation");
        checkNotNull(groupBy, "groupBy");

        if (namespace.trim().isEmpty()) {
            throw invalid("A cross-Thing timeseries query requires a non-empty 'namespaces' value.");
        }
        if (paths.isEmpty()) {
            throw invalid("A cross-Thing timeseries query requires at least one path in 'paths'.");
        }
        // Same bound the single-Thing query enforces. It matters more here, not less: each path is a
        // separate grouped scan over every Thing in the namespace, so the path count multiplies a
        // fan-out that is already namespace-wide.
        if (paths.size() > TimeseriesQuery.MAX_PATHS) {
            throw invalid("A query may request at most <" + TimeseriesQuery.MAX_PATHS + "> paths but <" +
                    paths.size() + "> were given. Split the request into smaller batches.");
        }
        if (!from.isBefore(to)) {
            throw invalid("'from' (" + from + ") must be strictly before 'to' (" + to + ").");
        }
        if (step.isZero() || step.isNegative()) {
            throw invalid("'step' must be a positive duration, was <" + step + ">.");
        }
        if (step.getNano() != 0) {
            throw invalid("The 'step' must be a whole number of seconds, but was <" + step +
                    ">. A fractional step cannot be expressed as a bucket width: the backend would " +
                    "truncate it to a zero-sized bin and the fill grid would never advance.");
        }
        // A cross-Thing read without downsampling would stream every raw point of every matching
        // Thing through the service — unbounded by construction. Requiring a bucketed aggregation
        // keeps the result size a function of the time range, not of the tenant's Thing count.
        if (!aggregation.requiresStep()) {
            throw invalid("Cross-Thing aggregation supports the per-bucket aggregations " +
                    "(avg, min, max, sum, count, first, last, stddev); <" + aggregation.getName() +
                    "> is a window function and is only available on single-Thing queries.");
        }
        validateNoDuplicateDimensions(groupBy);
        final Integer validatedMaxGroups = validateMaxGroups(maxGroups);

        return new ImmutableCrossThingTimeseriesQuery(namespace.trim(), paths, from, to, step,
                aggregation, groupBy, filter, timezone, fillStrategy, validatedMaxGroups);
    }

    private static void validateNoDuplicateDimensions(final List<GroupBy> groupBy) {
        final Set<GroupBy> seen = new LinkedHashSet<>();
        for (final GroupBy dimension : groupBy) {
            if (!seen.add(dimension)) {
                throw invalid("Duplicate groupBy dimension <" + dimension + ">.");
            }
        }
    }

    @Nullable
    private static Integer validateMaxGroups(@Nullable final Integer maxGroups) {
        if (maxGroups == null) {
            return null;
        }
        if (maxGroups <= 0) {
            throw invalid("'maxGroups' must be positive, was <" + maxGroups + ">.");
        }
        if (maxGroups > CrossThingTimeseriesQuery.MAX_GROUPS_CEILING) {
            throw invalid("'maxGroups' must not exceed " + CrossThingTimeseriesQuery.MAX_GROUPS_CEILING + ", was <" +
                    maxGroups + ">.");
        }
        return maxGroups;
    }

    private static TimeseriesQueryInvalidException invalid(final String message) {
        return TimeseriesQueryInvalidException.newBuilder(message).build();
    }

    static CrossThingTimeseriesQuery fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final String namespace = jsonObject.getValueOrThrow(JsonFields.NAMESPACE);
        final List<JsonPointer> paths = pathsFromJson(jsonObject.getValueOrThrow(JsonFields.PATHS));
        final Instant from = parseInstant(jsonObject.getValueOrThrow(JsonFields.FROM), "from");
        final Instant to = parseInstant(jsonObject.getValueOrThrow(JsonFields.TO), "to");
        final Duration step = parseDuration(jsonObject.getValueOrThrow(JsonFields.STEP), "step");
        final Aggregation aggregation = parseAggregation(jsonObject.getValueOrThrow(JsonFields.AGGREGATION));
        final List<GroupBy> groupBy = jsonObject.getValue(JsonFields.GROUP_BY)
                .map(ImmutableCrossThingTimeseriesQuery::groupByFromJson)
                .orElseGet(Collections::emptyList);
        final String filter = jsonObject.getValue(JsonFields.FILTER).orElse(null);
        final ZoneId timezone = jsonObject.getValue(JsonFields.TIMEZONE)
                .map(ImmutableCrossThingTimeseriesQuery::parseZoneId)
                .orElse(null);
        final FillStrategy fillStrategy = jsonObject.getValue(JsonFields.FILL_STRATEGY)
                .map(ImmutableCrossThingTimeseriesQuery::parseFillStrategy)
                .orElse(null);
        final Integer maxGroups = jsonObject.getValue(JsonFields.MAX_GROUPS).orElse(null);

        return of(namespace, paths, from, to, step, aggregation, groupBy, filter, timezone,
                fillStrategy, maxGroups);
    }

    private static List<GroupBy> groupByFromJson(final JsonArray array) {
        final List<GroupBy> result = new ArrayList<>(array.getSize());
        for (final JsonValue value : array) {
            result.add(GroupBy.parse(value.isString() ? value.asString() : value.formatAsString()));
        }
        return result;
    }


    private static List<JsonPointer> pathsFromJson(final JsonArray array) {
        final List<JsonPointer> result = new ArrayList<>(array.getSize());
        for (final JsonValue value : array) {
            result.add(JsonPointer.of(value.isString() ? value.asString() : value.formatAsString()));
        }
        return result;
    }

    private static Instant parseInstant(final String raw, final String fieldName) {
        try {
            return Instant.parse(raw);
        } catch (final DateTimeParseException e) {
            throw invalid("Field '" + fieldName + "' is not a valid ISO-8601 instant: <" + raw + ">.");
        }
    }

    private static Duration parseDuration(final String raw, final String fieldName) {
        try {
            return Duration.parse(raw);
        } catch (final DateTimeParseException e) {
            throw invalid("Field '" + fieldName + "' is not a valid ISO-8601 duration: <" + raw + ">.");
        }
    }

    private static Aggregation parseAggregation(final String raw) {
        return Aggregation.forName(raw)
                .orElseThrow(() -> invalid("Unknown aggregation <" + raw + ">."));
    }

    private static FillStrategy parseFillStrategy(final String raw) {
        return FillStrategy.forName(raw)
                .orElseThrow(() -> invalid("Unknown fill strategy <" + raw + ">."));
    }

    private static ZoneId parseZoneId(final String raw) {
        try {
            return ZoneId.of(raw);
        } catch (final DateTimeException e) {
            throw invalid("Unknown timezone <" + raw + ">.");
        }
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<JsonPointer> getPaths() {
        return paths;
    }

    @Override
    public Instant getFrom() {
        return from;
    }

    @Override
    public Instant getTo() {
        return to;
    }

    @Override
    public Duration getStep() {
        return step;
    }

    @Override
    public Aggregation getAggregation() {
        return aggregation;
    }

    @Override
    public List<GroupBy> getGroupBy() {
        return groupBy;
    }

    @Override
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    @Override
    public Optional<ZoneId> getTimezone() {
        return Optional.ofNullable(timezone);
    }

    @Override
    public Optional<FillStrategy> getFillStrategy() {
        return Optional.ofNullable(fillStrategy);
    }

    @Override
    public Optional<Integer> getMaxGroups() {
        return Optional.ofNullable(maxGroups);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.NAMESPACE, namespace)
                .set(JsonFields.PATHS, paths.stream()
                        .map(JsonPointer::toString)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray()))
                .set(JsonFields.FROM, from.toString())
                .set(JsonFields.TO, to.toString())
                .set(JsonFields.STEP, step.toString())
                .set(JsonFields.AGGREGATION, aggregation.getName());

        if (!groupBy.isEmpty()) {
            builder.set(JsonFields.GROUP_BY, groupBy.stream()
                    .map(GroupBy::toString)
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray()));
        }
        if (filter != null) {
            builder.set(JsonFields.FILTER, filter);
        }
        if (timezone != null) {
            builder.set(JsonFields.TIMEZONE, timezone.toString());
        }
        if (fillStrategy != null) {
            builder.set(JsonFields.FILL_STRATEGY, fillStrategy.getName());
        }
        if (maxGroups != null) {
            builder.set(JsonFields.MAX_GROUPS, maxGroups);
        }
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableCrossThingTimeseriesQuery that = (ImmutableCrossThingTimeseriesQuery) o;
        return Objects.equals(namespace, that.namespace)
                && Objects.equals(paths, that.paths)
                && Objects.equals(from, that.from)
                && Objects.equals(to, that.to)
                && Objects.equals(step, that.step)
                && aggregation == that.aggregation
                && Objects.equals(groupBy, that.groupBy)
                && Objects.equals(filter, that.filter)
                && Objects.equals(timezone, that.timezone)
                && fillStrategy == that.fillStrategy
                && Objects.equals(maxGroups, that.maxGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, paths, from, to, step, aggregation, groupBy, filter,
                timezone, fillStrategy, maxGroups);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [namespace=" + namespace
                + ", paths=" + paths
                + ", from=" + from
                + ", to=" + to
                + ", step=" + step
                + ", aggregation=" + aggregation
                + ", groupBy=" + groupBy
                + ", filter=" + filter
                + ", timezone=" + timezone
                + ", fillStrategy=" + fillStrategy
                + ", maxGroups=" + maxGroups
                + "]";
    }

    /**
     * JSON field definitions of a {@code CrossThingTimeseriesQuery}.
     */
    static final class JsonFields {

        static final JsonFieldDefinition<String> NAMESPACE =
                JsonFactory.newStringFieldDefinition("namespace", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> PATHS =
                JsonFactory.newJsonArrayFieldDefinition("paths", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> FROM =
                JsonFactory.newStringFieldDefinition("from", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> TO =
                JsonFactory.newStringFieldDefinition("to", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> STEP =
                JsonFactory.newStringFieldDefinition("step", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> AGGREGATION =
                JsonFactory.newStringFieldDefinition("aggregation", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> GROUP_BY =
                JsonFactory.newJsonArrayFieldDefinition("groupBy", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> FILTER =
                JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> TIMEZONE =
                JsonFactory.newStringFieldDefinition("timezone", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> FILL_STRATEGY =
                JsonFactory.newStringFieldDefinition("fillStrategy", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<Integer> MAX_GROUPS =
                JsonFactory.newIntFieldDefinition("maxGroups", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
