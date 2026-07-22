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
package org.eclipse.ditto.gateway.service.endpoints.routes.timeseries;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.SortOrder;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;

/**
 * Route exposing timeseries reads as a top-level resource alongside {@code /things} and
 * {@code /policies}, matching the IOT-495 Phase 1 URL shape:
 * <pre>
 *   GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{property-pointer}
 *       ?from=&lt;ISO-8601&gt;&amp;to=&lt;ISO-8601&gt;[&amp;limit=&lt;n&gt;]
 * </pre>
 * The route translates query / path parameters into a {@link RetrieveTimeseries} command and
 * forwards it through the standard request pipeline so authorisation enforcement (READ_TS) and
 * adapter dispatch happen on the timeseries service. {@code property-pointer} may itself contain
 * slashes (e.g. {@code temperature/avg}) — the path matcher captures the entire trailing segment.
 * <p>
 * Two REST shapes funnel into the same {@link RetrieveTimeseries}: the single-property URL above and
 * a thing-level {@code GET /timeseries/things/{thingId}?paths=<p1>,<p2>,...} for multi-property reads.
 * <p>
 * All query parameters that affect <em>which</em> data is returned ({@code from/to/step/agg/fill/tz/
 * percentile/limit/cursor/order/tagFilter}) live on {@link TimeseriesQuery} and therefore round-trip identically across HTTP,
 * WebSocket and Connectivity. {@code timeFormat} is the deliberate exception: it only changes how the
 * already-computed timestamps are <em>rendered</em> (ISO vs. epoch-ms), so it is an HTTP-edge-only
 * presentation transform and is intentionally kept off the model — do not migrate it onto
 * {@code TimeseriesQuery}, which would wrongly imply cross-transport query semantics.
 */
public final class TimeseriesRoute extends AbstractRoute {

    public static final String PATH_TIMESERIES = "timeseries";

    private static final String PATH_THINGS = "things";
    private static final String PATH_FEATURES = "features";
    private static final String PATH_PROPERTIES = "properties";

    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_STEP = "step";
    private static final String PARAM_AGG = "agg";
    private static final String PARAM_FILL = "fill";
    private static final String PARAM_TZ = "tz";
    private static final String PARAM_PERCENTILE = "percentile";
    private static final String PARAM_CURSOR = "cursor";
    private static final String PARAM_ORDER = "order";
    private static final String PARAM_TAG_FILTER = "tagFilter";
    private static final String PARAM_PATHS = "paths";
    private static final String PARAM_TIME_FORMAT = "timeFormat";

    /** Short duration form used by {@code step} and relative time offsets, e.g. {@code 30s,5m,1h,1d,1w}. */
    private static final Pattern SHORT_DURATION = Pattern.compile("(\\d+)([smhdw])");

    public TimeseriesRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the timeseries routes under {@code /timeseries/things/{id}}: the single-property
     * {@code .../features/{f}/properties/{path}} shape and the multi-property {@code ?paths=...} shape.
     *
     * @param ctx the request context.
     * @param dittoHeaders the (already authenticated) Ditto headers carrying the auth context.
     * @return the route.
     */
    public Route buildTimeseriesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_TIMESERIES), () ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS), () ->
                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), thingIdString -> {
                            final ThingId thingId = ThingId.of(thingIdString);
                            return concat(
                                    // GET /timeseries/things/{id}/features/{f}/properties/{path}
                                    singlePropertyRoute(ctx, dittoHeaders, thingId),
                                    // GET /timeseries/things/{id}?paths=<p1>,<p2>,...  (multi-property)
                                    pathEndOrSingleSlash(() ->
                                            parameter(PARAM_PATHS, pathsCsv ->
                                                    runTimeseriesQuery(ctx, dittoHeaders, thingId,
                                                            parsePathsParam(pathsCsv))))
                            );
                        })
                )
        );
    }

    private Route singlePropertyRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_FEATURES), () ->
                rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()),
                        featureId -> rawPathPrefix(
                                PathMatchers.slash()
                                        .concat(PATH_PROPERTIES)
                                        .concat(PathMatchers.slash())
                                        .concat(PathMatchers.remaining())
                                        .map(p -> UriEncoding.decode(p, UriEncoding.EncodingType.RFC3986)),
                                propertyPointerString -> {
                                    // Re-assemble the full Ditto pointer so the command — and the
                                    // read-path enforcement check — see the same resource key a normal
                                    // RetrieveFeatureProperty would, identical across HTTP/WS/Connectivity.
                                    final JsonPointer fullPath = JsonPointer.of(
                                            "/features/" + featureId + "/properties/" + propertyPointerString);
                                    return runTimeseriesQuery(ctx, dittoHeaders, thingId,
                                            Collections.singletonList(fullPath));
                                }
                        )
                )
        );
    }

    /**
     * Shared query handler: parses the common query parameters (time range, downsampling,
     * aggregation, fill, timezone, percentile, limit, timeFormat) and dispatches a single
     * {@link RetrieveTimeseries} carrying the given paths. The single-property and multi-property
     * routes both funnel through here so their behaviour is identical.
     */
    private Route runTimeseriesQuery(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId, final List<JsonPointer> paths) {
        return get(() -> parameter(PARAM_FROM, fromString ->
                parameter(PARAM_TO, toString ->
                        parameterOptional(PARAM_LIMIT, limitOpt ->
                                parameterOptional(PARAM_STEP, stepOpt ->
                                        parameterOptional(PARAM_AGG, aggOpt ->
                                                parameterOptional(PARAM_FILL, fillOpt ->
                                                        parameterOptional(PARAM_TZ, tzOpt ->
                                                                parameterOptional(PARAM_PERCENTILE, pctOpt ->
                                                                        parameterOptional(PARAM_CURSOR, cursorOpt ->
                                                                                parameterOptional(PARAM_ORDER, orderOpt ->
                                                                                        parameterOptional(PARAM_TAG_FILTER, tagFilterOpt ->
                                                                                                parameterOptional(PARAM_TIME_FORMAT, tfOpt ->
                                                                                                        dispatchQuery(ctx, dittoHeaders, thingId,
                                                                                                                paths, fromString, toString,
                                                                                                                limitOpt, stepOpt, aggOpt,
                                                                                                                fillOpt, tzOpt, pctOpt,
                                                                                                                cursorOpt, orderOpt, tagFilterOpt, tfOpt)
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ));
    }

    private Route dispatchQuery(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId, final List<JsonPointer> paths, final String fromString,
            final String toString, final Optional<String> limitOpt, final Optional<String> stepOpt,
            final Optional<String> aggOpt, final Optional<String> fillOpt, final Optional<String> tzOpt,
            final Optional<String> pctOpt, final Optional<String> cursorOpt,
            final Optional<String> orderOpt, final Optional<String> tagFilterOpt,
            final Optional<String> timeFormatOpt) {

        final RetrieveTimeseries command = buildRetrieveTimeseries(thingId, paths, fromString, toString,
                limitOpt, stepOpt, aggOpt, fillOpt, tzOpt, pctOpt, cursorOpt, orderOpt, tagFilterOpt,
                dittoHeaders);
        if (parseTimeFormatIsMillis(timeFormatOpt)) {
            // timeFormat=ms: render timestamps as epoch milliseconds. The model stays ISO-canonical;
            // the conversion is a presentation transform applied only at the HTTP edge.
            return handlePerRequest(ctx, command, (responseValue, response) ->
                    response.withEntity(ContentTypes.APPLICATION_JSON,
                            convertTimestampsToMillis(responseValue).toString()));
        }
        return handlePerRequest(ctx, command);
    }

    private static RetrieveTimeseries buildRetrieveTimeseries(final ThingId thingId,
            final List<JsonPointer> paths,
            final String fromString,
            final String toString,
            final Optional<String> limitOpt,
            final Optional<String> stepOpt,
            final Optional<String> aggOpt,
            final Optional<String> fillOpt,
            final Optional<String> tzOpt,
            final Optional<String> percentileOpt,
            final Optional<String> cursorOpt,
            final Optional<String> orderOpt,
            final Optional<String> tagFilterOpt,
            final DittoHeaders dittoHeaders) {
        // Anchor both relative bounds to a single "now" so e.g. from=now-1h, to=now span exactly 1h.
        final Instant now = Instant.now();
        final Instant from = parseTimeParam(PARAM_FROM, fromString, now);
        final Instant to = parseTimeParam(PARAM_TO, toString, now);
        final Integer limit = limitOpt.map(s -> parseIntegerParam(PARAM_LIMIT, s)).orElse(null);
        final Duration step = stepOpt.map(TimeseriesRoute::parseStepParam).orElse(null);
        final Aggregation aggregation = aggOpt.map(TimeseriesRoute::parseAggregationParam).orElse(null);
        final FillStrategy fillStrategy = fillOpt.map(TimeseriesRoute::parseFillParam).orElse(null);
        final ZoneId timezone = tzOpt.map(TimeseriesRoute::parseTimezoneParam).orElse(null);
        final Double percentile = percentileOpt.map(TimeseriesRoute::parsePercentileParam).orElse(null);
        // The cursor is an opaque blob passed straight through; its format and the "cursor only for a
        // single-path raw read" rule are validated in TimeseriesQuery.of so every transport agrees.
        final String cursor = cursorOpt.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        final SortOrder order = orderOpt.map(String::trim).filter(s -> !s.isEmpty())
                .map(TimeseriesRoute::parseOrderParam).orElse(null);
        final Map<String, String> tagFilters = tagFilterOpt.map(String::trim).filter(s -> !s.isEmpty())
                .map(TimeseriesRoute::parseTagFiltersParam).orElseGet(Map::of);
        // Semantic validation (e.g. percentile requires a value, group aggregations require a step,
        // order=desc only for raw reads) lives in TimeseriesQuery.of so it applies uniformly across
        // HTTP / WebSocket / Connectivity.
        final TimeseriesQuery query = TimeseriesQuery.of(
                thingId, paths, from, to, step, aggregation, fillStrategy, limit, timezone, percentile,
                cursor, order);
        final TimeseriesQuery queryWithTags = tagFilters.isEmpty() ? query : query.withTagFilters(tagFilters);
        return RetrieveTimeseries.of(queryWithTags, dittoHeaders);
    }

    /** Parses an absolute ISO-8601 instant or a relative expression ({@code now}, {@code now-24h}). */
    private static Instant parseTimeParam(final String name, final String value, final Instant now) {
        final String v = value.trim();
        if ("now".equals(v)) {
            return now;
        }
        if (v.length() > 4 && (v.startsWith("now-") || v.startsWith("now+"))) {
            final boolean minus = v.charAt(3) == '-';
            final Duration offset = parseShortDuration(name, v.substring(4));
            return minus ? now.minus(offset) : now.plus(offset);
        }
        try {
            return Instant.parse(v);
        } catch (final DateTimeParseException e) {
            // Surface the same shape used elsewhere in Ditto for query-string parse failures so
            // clients get a uniform 400 instead of a 500 / generic stack trace.
            throw invalidParam(name, value,
                    "Expected an ISO-8601 instant (e.g. \"2026-01-15T10:30:00Z\") or a relative " +
                            "expression (e.g. \"now\", \"now-24h\").");
        }
    }

    /** Parses a {@code step} such as {@code 30s}, {@code 5m}, {@code 1h}, {@code 1d} or ISO-8601 ({@code PT1H}). */
    private static Duration parseStepParam(final String value) {
        return parseShortDuration(PARAM_STEP, value);
    }

    private static Duration parseShortDuration(final String name, final String raw) {
        final String v = raw.trim();
        final Matcher matcher = SHORT_DURATION.matcher(v);
        if (matcher.matches()) {
            final long n = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(n);
                case "m" -> Duration.ofMinutes(n);
                case "h" -> Duration.ofHours(n);
                case "d" -> Duration.ofDays(n);
                case "w" -> Duration.ofDays(7 * n);
                default -> throw invalidParam(name, raw, "Unsupported duration unit.");
            };
        }
        try {
            return Duration.parse(v);
        } catch (final DateTimeParseException e) {
            throw invalidParam(name, raw,
                    "Expected a duration like \"30s\", \"5m\", \"1h\", \"1d\" or ISO-8601 (e.g. \"PT1H\").");
        }
    }

    private static Aggregation parseAggregationParam(final String value) {
        return Aggregation.forName(value.trim()).orElseThrow(() -> invalidParam(PARAM_AGG, value,
                "Expected one of: avg, min, max, sum, count, first, last, derivative, rate, " +
                        "integral, stddev, percentile."));
    }

    private static FillStrategy parseFillParam(final String value) {
        return FillStrategy.forName(value.trim()).orElseThrow(() -> invalidParam(PARAM_FILL, value,
                "Expected one of: null, previous, zero."));
    }

    private static ZoneId parseTimezoneParam(final String value) {
        try {
            return ZoneId.of(value.trim());
        } catch (final DateTimeException e) {
            throw invalidParam(PARAM_TZ, value,
                    "Expected an IANA time-zone ID, e.g. \"Europe/Berlin\" or \"UTC\".");
        }
    }

    private static SortOrder parseOrderParam(final String value) {
        return SortOrder.forName(value.trim()).orElseThrow(() -> invalidParam(PARAM_ORDER, value,
                "Expected one of: asc, desc."));
    }

    /**
     * Parses the {@code tagFilter} parameter — a comma-separated list of {@code key:value} pairs
     * (split on the first colon so values may contain colons) — into a tag-filter map.
     */
    private static Map<String, String> parseTagFiltersParam(final String csv) {
        final Map<String, String> filters = new LinkedHashMap<>();
        for (final String raw : csv.split(",")) {
            final String pair = raw.trim();
            if (pair.isEmpty()) {
                continue;
            }
            final int colon = pair.indexOf(':');
            if (colon <= 0 || colon == pair.length() - 1) {
                throw invalidParam(PARAM_TAG_FILTER, csv,
                        "Expected comma-separated key:value pairs, e.g. building:A,floor:2.");
            }
            filters.put(pair.substring(0, colon).trim(), pair.substring(colon + 1).trim());
        }
        if (filters.isEmpty()) {
            throw invalidParam(PARAM_TAG_FILTER, csv,
                    "Expected at least one key:value pair, e.g. building:A.");
        }
        return filters;
    }

    private static Double parsePercentileParam(final String value) {
        try {
            // Parse only; the 0-100 range check lives in TimeseriesQuery.of so every transport
            // reports the same timeseries:query.invalid error rather than a transport-specific one.
            return Double.parseDouble(value.trim());
        } catch (final NumberFormatException e) {
            throw invalidParam(PARAM_PERCENTILE, value, "Expected a number between 0 and 100.");
        }
    }

    /** Parses the comma-separated {@code paths} parameter into a non-empty list of JSON pointers. */
    private static List<JsonPointer> parsePathsParam(final String csv) {
        final List<JsonPointer> paths = new ArrayList<>();
        for (final String raw : csv.split(",")) {
            final String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                paths.add(JsonPointer.of(trimmed));
            }
        }
        if (paths.isEmpty()) {
            throw invalidParam(PARAM_PATHS, csv, "Expected one or more comma-separated JSON pointers, " +
                    "e.g. /features/env/properties/temperature,/attributes/battery.");
        }
        return paths;
    }

    /** Returns {@code true} for {@code timeFormat=ms}, {@code false} for {@code iso} (the default). */
    private static boolean parseTimeFormatIsMillis(final Optional<String> timeFormatOpt) {
        final String value = timeFormatOpt.map(String::trim).filter(s -> !s.isEmpty()).orElse("iso");
        switch (value) {
            case "iso":
                return false;
            case "ms":
                return true;
            default:
                throw invalidParam(PARAM_TIME_FORMAT, value, "Expected \"iso\" (default) or \"ms\".");
        }
    }

    /**
     * Rewrites every {@code data[].t} timestamp in the response entity from an ISO-8601 string to
     * epoch milliseconds. The entity is the per-path results array
     * ({@code [{path, result, data:[{t, v}]}, ...]}).
     */
    private static JsonValue convertTimestampsToMillis(final JsonValue entity) {
        if (!entity.isArray()) {
            return entity;
        }
        final JsonArrayBuilder seriesBuilder = JsonFactory.newArrayBuilder();
        for (final JsonValue seriesValue : entity.asArray()) {
            seriesBuilder.add(seriesValue.isObject()
                    ? convertSeriesTimestamps(seriesValue.asObject())
                    : seriesValue);
        }
        return seriesBuilder.build();
    }

    private static JsonObject convertSeriesTimestamps(final JsonObject series) {
        final Optional<JsonArray> dataOpt =
                series.getValue("data").filter(JsonValue::isArray).map(JsonValue::asArray);
        if (dataOpt.isEmpty()) {
            return series;
        }
        final JsonArrayBuilder dataBuilder = JsonFactory.newArrayBuilder();
        for (final JsonValue pointValue : dataOpt.get()) {
            dataBuilder.add(pointValue.isObject() ? convertPointTimestamp(pointValue.asObject()) : pointValue);
        }
        return series.setValue("data", dataBuilder.build());
    }

    private static JsonObject convertPointTimestamp(final JsonObject point) {
        return point.getValue("t")
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(iso -> point.setValue("t", Instant.parse(iso).toEpochMilli()))
                .orElse(point);
    }

    /** Same idea for integer parameters. */
    private static Integer parseIntegerParam(final String name, final String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            throw invalidParam(name, value, "Expected an integer.");
        }
    }

    /** Builds the standard Ditto 400 error shape for an invalid query parameter. */
    private static org.eclipse.ditto.json.JsonParseException invalidParam(final String name,
            final String value, final String description) {
        return org.eclipse.ditto.json.JsonParseException.newBuilder()
                .message("Query parameter <" + name + "> has an invalid value: <" + value + ">.")
                .description(description)
                .build();
    }
}
