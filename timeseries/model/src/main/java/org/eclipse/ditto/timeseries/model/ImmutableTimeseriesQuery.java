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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * An immutable implementation of {@link TimeseriesQuery}.
 */
@Immutable
final class ImmutableTimeseriesQuery implements TimeseriesQuery {

    /**
     * Upper bound on the number of paths a single query may request. A multi-path read fans out to
     * one scan per path, each bounded independently by the adapter's per-path ceiling, so the
     * request-level memory is the path count times that ceiling. This cap keeps a pathological
     * request from multiplying the ceiling without limit; legitimate multi-property reads are well
     * under it.
     */
    private static final int MAX_PATHS = 100;

    private final ThingId thingId;
    private final List<JsonPointer> paths;
    private final Instant from;
    private final Instant to;
    @Nullable private final Duration step;
    @Nullable private final Aggregation aggregation;
    @Nullable private final FillStrategy fillStrategy;
    @Nullable private final Integer limit;
    @Nullable private final ZoneId timezone;
    @Nullable private final Double percentile;

    private ImmutableTimeseriesQuery(final ThingId thingId,
            final List<JsonPointer> paths,
            final Instant from,
            final Instant to,
            @Nullable final Duration step,
            @Nullable final Aggregation aggregation,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Integer limit,
            @Nullable final ZoneId timezone,
            @Nullable final Double percentile) {

        this.thingId = thingId;
        this.paths = paths;
        this.from = from;
        this.to = to;
        this.step = step;
        this.aggregation = aggregation;
        this.fillStrategy = fillStrategy;
        this.limit = limit;
        this.timezone = timezone;
        this.percentile = percentile;
    }

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

        checkNotNull(thingId, "thingId");
        checkNotNull(paths, "paths");
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        validateSemantics(paths, aggregation, step, fillStrategy, percentile);

        final List<JsonPointer> defensivePaths =
                Collections.unmodifiableList(new ArrayList<>(paths));

        return new ImmutableTimeseriesQuery(
                thingId, defensivePaths, from, to, step, aggregation, fillStrategy, limit, timezone,
                percentile);
    }

    /**
     * Enforces the semantic contract of the aggregation parameters at the model layer (so every
     * transport rejects the same inputs identically) by throwing a {@link TimeseriesQueryInvalidException}
     * (HTTP 400). Parse-level errors (bad enum names, malformed durations) are handled earlier, at the
     * transport boundary.
     */
    private static void validateSemantics(final List<JsonPointer> paths,
            @Nullable final Aggregation aggregation,
            @Nullable final Duration step,
            @Nullable final FillStrategy fillStrategy,
            @Nullable final Double percentile) {

        if (paths.size() > MAX_PATHS) {
            throw TimeseriesQueryInvalidException.newBuilder(
                    "A query may request at most <" + MAX_PATHS + "> paths but <" + paths.size() +
                            "> were given. Split the request into smaller batches.").build();
        }
        if (percentile != null && (percentile < 0.0 || percentile > 100.0)) {
            throw TimeseriesQueryInvalidException.newBuilder(
                    "Parameter <percentile> must be between 0 and 100 but was <" + percentile + ">.")
                    .build();
        }
        if (aggregation != null) {
            if (aggregation.requiresStep() && step == null) {
                throw TimeseriesQueryInvalidException.newBuilder(
                        "Aggregation <" + aggregation.getName() +
                                "> requires a <step> parameter for downsampling.").build();
            }
            if (aggregation == Aggregation.PERCENTILE && percentile == null) {
                throw TimeseriesQueryInvalidException.newBuilder(
                        "Aggregation <percentile> requires a <percentile> value between 0 and 100.")
                        .build();
            }
        }
        if (fillStrategy == FillStrategy.LINEAR) {
            // The enum constant exists for forward compatibility, but linear interpolation is not
            // implemented yet (it lands with the fill/tz refinement). Reject explicitly rather than
            // silently degrading to null-fill, which would mislead callers about the returned shape.
            throw TimeseriesQueryInvalidException.newBuilder(
                    "Fill strategy <linear> is not yet supported. Use <null>, <previous> or <zero>.")
                    .build();
        }
    }

    static TimeseriesQuery fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JsonFields.THING_ID));
        final List<JsonPointer> paths = pathsFromJson(jsonObject.getValueOrThrow(JsonFields.PATHS));
        final Instant from = parseInstant(jsonObject.getValueOrThrow(JsonFields.FROM), "from");
        final Instant to = parseInstant(jsonObject.getValueOrThrow(JsonFields.TO), "to");

        final Duration step = jsonObject.getValue(JsonFields.STEP)
                .map(s -> parseDuration(s, "step"))
                .orElse(null);
        final Aggregation aggregation = jsonObject.getValue(JsonFields.AGGREGATION)
                .map(s -> parseAggregation(s))
                .orElse(null);
        final FillStrategy fillStrategy = jsonObject.getValue(JsonFields.FILL_STRATEGY)
                .map(s -> parseFillStrategy(s))
                .orElse(null);
        final Integer limit = jsonObject.getValue(JsonFields.LIMIT).orElse(null);
        final ZoneId timezone = jsonObject.getValue(JsonFields.TIMEZONE)
                .map(s -> parseZoneId(s))
                .orElse(null);
        final Double percentile = jsonObject.getValue(JsonFields.PERCENTILE).orElse(null);

        return of(thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, percentile);
    }

    private static List<JsonPointer> pathsFromJson(final JsonArray array) {
        final List<JsonPointer> result = new ArrayList<>(array.getSize());
        for (final JsonValue value : array) {
            if (!value.isString()) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message("Element of <paths> must be a JSON string but was: " + value)
                        .build());
            }
            result.add(JsonPointer.of(value.asString()));
        }
        return result;
    }

    private static Instant parseInstant(final String raw, final String fieldName) {
        try {
            return Instant.parse(raw);
        } catch (final DateTimeParseException e) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("Field <" + fieldName + "> is not a valid ISO-8601 instant: <" + raw + ">.")
                    .description("Expected an ISO-8601 instant, e.g. \"2026-01-15T10:30:00Z\".")
                    .cause(e)
                    .build());
        }
    }

    private static Duration parseDuration(final String raw, final String fieldName) {
        try {
            return Duration.parse(raw);
        } catch (final DateTimeParseException e) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("Field <" + fieldName + "> is not a valid ISO-8601 duration: <" + raw + ">.")
                    .description("Expected an ISO-8601 duration, e.g. \"PT1H\" or \"PT5M\".")
                    .cause(e)
                    .build());
        }
    }

    private static Aggregation parseAggregation(final String raw) {
        return Aggregation.forName(raw).orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                .message("Field <aggregation> has an unknown value: <" + raw + ">.")
                .description("Expected one of the supported aggregation function names (e.g. \"avg\").")
                .build()));
    }

    private static FillStrategy parseFillStrategy(final String raw) {
        return FillStrategy.forName(raw).orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                .message("Field <fillStrategy> has an unknown value: <" + raw + ">.")
                .description("Expected one of: \"null\", \"previous\", \"linear\", \"zero\".")
                .build()));
    }

    private static ZoneId parseZoneId(final String raw) {
        try {
            return ZoneId.of(raw);
        } catch (final DateTimeException e) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("Field <timezone> is not a valid time-zone ID: <" + raw + ">.")
                    .description("Expected an IANA time-zone ID, e.g. \"Europe/Berlin\" or \"UTC\".")
                    .cause(e)
                    .build());
        }
    }

    @Override
    public ThingId getThingId() {
        return thingId;
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
    public Optional<Duration> getStep() {
        return Optional.ofNullable(step);
    }

    @Override
    public Optional<Aggregation> getAggregation() {
        return Optional.ofNullable(aggregation);
    }

    @Override
    public Optional<FillStrategy> getFillStrategy() {
        return Optional.ofNullable(fillStrategy);
    }

    @Override
    public Optional<Integer> getLimit() {
        return Optional.ofNullable(limit);
    }

    @Override
    public Optional<ZoneId> getTimezone() {
        return Optional.ofNullable(timezone);
    }

    @Override
    public Optional<Double> getPercentile() {
        return Optional.ofNullable(percentile);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.THING_ID, thingId.toString())
                .set(JsonFields.PATHS, pathsToJson())
                .set(JsonFields.FROM, from.toString())
                .set(JsonFields.TO, to.toString());

        if (step != null) {
            builder.set(JsonFields.STEP, step.toString());
        }
        if (aggregation != null) {
            builder.set(JsonFields.AGGREGATION, aggregation.getName());
        }
        if (fillStrategy != null) {
            builder.set(JsonFields.FILL_STRATEGY, fillStrategy.getName());
        }
        if (limit != null) {
            builder.set(JsonFields.LIMIT, limit);
        }
        if (timezone != null) {
            builder.set(JsonFields.TIMEZONE, timezone.toString());
        }
        if (percentile != null) {
            builder.set(JsonFields.PERCENTILE, percentile);
        }

        return builder.build();
    }

    private JsonArray pathsToJson() {
        final JsonArrayBuilder builder = JsonFactory.newArrayBuilder();
        for (final JsonPointer pointer : paths) {
            builder.add(pointer.toString());
        }
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTimeseriesQuery)) {
            return false;
        }
        final ImmutableTimeseriesQuery that = (ImmutableTimeseriesQuery) o;
        return Objects.equals(thingId, that.thingId) &&
                Objects.equals(paths, that.paths) &&
                Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(step, that.step) &&
                aggregation == that.aggregation &&
                fillStrategy == that.fillStrategy &&
                Objects.equals(limit, that.limit) &&
                Objects.equals(timezone, that.timezone) &&
                Objects.equals(percentile, that.percentile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone,
                percentile);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", paths=" + paths +
                ", from=" + from +
                ", to=" + to +
                ", step=" + step +
                ", aggregation=" + aggregation +
                ", fillStrategy=" + fillStrategy +
                ", limit=" + limit +
                ", timezone=" + timezone +
                ", percentile=" + percentile +
                "]";
    }
}
