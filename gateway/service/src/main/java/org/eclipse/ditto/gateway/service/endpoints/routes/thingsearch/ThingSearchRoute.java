/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;

import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /search/things}.
 */
public final class ThingSearchRoute extends AbstractRoute {

    public static final String PATH_SEARCH = "search";
    public static final String PATH_THINGS = "things";

    private static final String PATH_COUNT = "count";

    /**
     * Constructs a {@code ThingSearchRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public ThingSearchRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the {@code /search} route.
     *
     * @return the {@code /search}} route.
     */
    public Route buildSearchRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return Directives.rawPathPrefix(PathMatchers.slash().concat(PATH_SEARCH), () ->
                Directives.rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS),
                        () -> // /search/things
                                concat(
                                        // /search/things/count
                                        path(PATH_COUNT, () -> countThings(ctx, dittoHeaders)),
                                        // /search/things
                                        pathEndOrSingleSlash(() -> searchThings(ctx, dittoHeaders))
                                )
                )
        );
    }

    /*
     * Describes {@code /search/things/count} route.
     *
     * @return {@code /search/things/count} route.
     */
    private Route countThings(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        // GET things/count?filter=<filterString>&namespaces=<namespacesString>
        return get(() -> thingSearchParameterOptional(params -> handlePerRequest(ctx,
                CountThings.of(calculateFilter(params.get(ThingSearchParameter.FILTER)),
                        calculateNamespaces(params.get(ThingSearchParameter.NAMESPACES)),
                        dittoHeaders))));
    }

    /*
     * Describes {@code /search/things} route.
     *
     * @return {@code /search/things} route.
     */
    private Route searchThings(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        // GET things?filter=<filterString>
        //           &options=<optionsString>
        //           &fields=<fieldsString>
        //           &namespaces=<namespacesString>
        //           &nextPageKey=<nextPageKey>
        return get(() -> thingSearchParameterOptional(params -> handlePerRequest(ctx,
                QueryThings.of(calculateFilter(params.get(ThingSearchParameter.FILTER)),
                        calculateOptions(params.get(ThingSearchParameter.OPTION)),
                        AbstractRoute.calculateSelectedFields(params.get(ThingSearchParameter.FIELDS))
                                .orElse(null),
                        calculateNamespaces(params.get(ThingSearchParameter.NAMESPACES)),
                        dittoHeaders))));
    }

    private Route thingSearchParameterOptional(
            final Function<EnumMap<ThingSearchParameter, Optional<String>>, Route> inner) {
        return thingSearchParameterOptionalImpl(ThingSearchParameter.values(),
                new EnumMap<>(ThingSearchParameter.class), inner);
    }

    private Route thingSearchParameterOptionalImpl(final ThingSearchParameter[] values,
            final EnumMap<ThingSearchParameter, Optional<String>> accumulator,
            final Function<EnumMap<ThingSearchParameter, Optional<String>>, Route> inner) {
        if (accumulator.size() >= values.length) {
            return inner.apply(accumulator);
        } else {
            final ThingSearchParameter parameter = values[accumulator.size()];
            return parameterOptional(parameter.toString(), parameterValueOptional -> {
                accumulator.put(parameter, parameterValueOptional);
                return thingSearchParameterOptionalImpl(values, accumulator, inner);
            });
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String calculateFilter(final Optional<String> filterString) {
        return filterString.orElse(null);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Set<String> calculateNamespaces(final Optional<String> namespacesString) {
        final Function<String, Set<String>> splitAndRemoveEmpty =
                s -> Arrays.stream(s.split(","))
                        .filter(segment -> !segment.isEmpty())
                        .collect(Collectors.toSet());

        // if no namespaces are given explicitly via query parameter,
        // return null to signify the lack of namespace restriction
        return namespacesString.map(splitAndRemoveEmpty).orElse(null);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static List<String> calculateOptions(final Optional<String> optionsString) {
        return optionsString
                .map(s -> Arrays.asList(s.split(",")))
                .orElse(null);
    }

}
