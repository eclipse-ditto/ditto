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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
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
 * Single-path-per-call REST shape; multi-path queries continue to use the WebSocket envelope.
 */
public final class TimeseriesRoute extends AbstractRoute {

    public static final String PATH_TIMESERIES = "timeseries";

    private static final String PATH_THINGS = "things";
    private static final String PATH_FEATURES = "features";
    private static final String PATH_PROPERTIES = "properties";

    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";
    private static final String PARAM_LIMIT = "limit";

    public TimeseriesRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the {@code /timeseries/things/{id}/features/{f}/properties/{path}} route.
     *
     * @param ctx the request context.
     * @param dittoHeaders the (already authenticated) Ditto headers carrying the auth context.
     * @return the route.
     */
    public Route buildTimeseriesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_TIMESERIES), () ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS), () ->
                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()),
                                thingIdString -> rawPathPrefix(PathMatchers.slash().concat(PATH_FEATURES), () ->
                                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()),
                                                featureId -> rawPathPrefix(
                                                        PathMatchers.slash()
                                                                .concat(PATH_PROPERTIES)
                                                                .concat(PathMatchers.slash())
                                                                .concat(PathMatchers.remaining())
                                                                .map(p -> UriEncoding.decode(p,
                                                                        UriEncoding.EncodingType.RFC3986)),
                                                        propertyPointerString ->
                                                                propertyTimeseries(ctx, dittoHeaders,
                                                                        thingIdString, featureId,
                                                                        propertyPointerString)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private Route propertyTimeseries(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingIdString, final String featureId, final String propertyPointer) {
        final ThingId thingId = ThingId.of(thingIdString);
        // The wire-side path Ditto uses is the full pointer
        // /features/<featureId>/properties/<propertyPointer> — re-assemble it here so the
        // command sent to the timeseries service is identical to what the WebSocket envelope
        // would carry. This also keeps the read-path enforcement check looking at the same
        // resource key as a normal RetrieveFeatureProperty would.
        final JsonPointer fullPath = JsonPointer.of(
                "/features/" + featureId + "/properties/" + propertyPointer);

        return get(() -> parameter(PARAM_FROM, fromString ->
                parameter(PARAM_TO, toString ->
                        parameterOptional(PARAM_LIMIT, limitOpt ->
                                handlePerRequest(ctx, buildRetrieveTimeseries(thingId, fullPath,
                                        fromString, toString, limitOpt, dittoHeaders))
                        )
                )
        ));
    }

    private static RetrieveTimeseries buildRetrieveTimeseries(final ThingId thingId,
            final JsonPointer fullPath,
            final String fromString,
            final String toString,
            final Optional<String> limitOpt,
            final DittoHeaders dittoHeaders) {
        final List<JsonPointer> paths = Collections.singletonList(fullPath);
        final Instant from = parseInstantParam(PARAM_FROM, fromString);
        final Instant to = parseInstantParam(PARAM_TO, toString);
        final Integer limit = limitOpt.map(s -> parseIntegerParam(PARAM_LIMIT, s)).orElse(null);
        // step / aggregation / fillStrategy / timezone are unset — Phase 1 read path is raw.
        final TimeseriesQuery query = TimeseriesQuery.of(thingId, paths, from, to, null, null, null, limit, null);
        return RetrieveTimeseries.of(query, dittoHeaders);
    }

    /** Translates query-string ISO-8601 parsing failures into the standard Ditto 400 error shape. */
    private static Instant parseInstantParam(final String name, final String value) {
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException e) {
            // Surface the same shape used elsewhere in Ditto for query-string parse failures so
            // clients get a uniform 400 instead of a 500 / generic stack trace.
            throw org.eclipse.ditto.json.JsonParseException.newBuilder()
                    .message("Query parameter <" + name + "> is not a valid ISO-8601 instant: <" + value + ">.")
                    .description("Expected an ISO-8601 instant, e.g. \"2026-01-15T10:30:00Z\".")
                    .build();
        }
    }

    /** Same idea for integer parameters. */
    private static Integer parseIntegerParam(final String name, final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw org.eclipse.ditto.json.JsonParseException.newBuilder()
                    .message("Query parameter <" + name + "> is not a valid integer: <" + value + ">.")
                    .build();
        }
    }
}
