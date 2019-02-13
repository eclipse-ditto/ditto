/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch;

import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchParameter.FIELDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchParameter.FILTER;
import static org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchParameter.NAMESPACES;
import static org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchParameter.OPTION;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.Directives;
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
     * Constructs the {@code /search/things} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ThingSearchRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        super(proxyActor, actorSystem);
    }

    /**
     * Builds the {@code /search} route.
     *
     * @return the {@code /search}} route.
     */
    public Route buildSearchRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return Directives.rawPathPrefix(CustomPathMatchers.mergeDoubleSlashes().concat(PATH_SEARCH), () ->
                Directives.rawPathPrefix(CustomPathMatchers.mergeDoubleSlashes().concat(PATH_THINGS),
                        () -> // /search/things
                                Directives.route(
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
        return get(() -> // GET things/count?filter=<filterString>&namespaces=<namespacesString>
                parameterOptional(FILTER.toString(), filterString ->
                        parameterOptional(NAMESPACES.toString(), namespacesString ->
                                handlePerRequest(ctx, CountThings.of(calculateFilter(filterString),
                                        calculateNamespaces(namespacesString), dittoHeaders))
                        )
                )
        );
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
        return get(() -> thingSearchParameterOptional(params ->
                handlePerRequest(ctx, QueryThings.of(
                        calculateFilter(params.get(FILTER)),
                        calculateOptions(params.get(OPTION)),
                        AbstractRoute.calculateSelectedFields(params.get(FIELDS))
                                .orElse(null),
                        calculateNamespaces(params.get(NAMESPACES)),
                        params.get(ThingSearchParameter.NEXT_PAGE_KEY).orElse(null),
                        dittoHeaders))));
    }

    private static Route thingSearchParameterOptional(
            final Function<EnumMap<ThingSearchParameter, Optional<String>>, Route> inner) {

        return thingSearchParameterOptionalImpl(ThingSearchParameter.values(),
                new EnumMap<>(ThingSearchParameter.class), inner);
    }

    private static Route thingSearchParameterOptionalImpl(final ThingSearchParameter[] values,
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
        final Set<String> defaultNamespaces = null;

        return namespacesString.map(splitAndRemoveEmpty).orElse(defaultNamespaces);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static List<String> calculateOptions(final Optional<String> optionsString) {
        return optionsString
                .map(s -> Arrays.asList(s.split(",")))
                .orElse(null);
    }

}
