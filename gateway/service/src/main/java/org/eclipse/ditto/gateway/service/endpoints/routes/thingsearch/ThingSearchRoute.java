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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidator;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidatorFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;

/**
 * Builder for creating Pekko HTTP routes for {@code /search/things}.
 */
public final class ThingSearchRoute extends AbstractRoute {

    public static final String PATH_SEARCH = "search";
    public static final String PATH_THINGS = "things";

    private static final String PATH_COUNT = "count";

    @Nullable
    private final NamespaceAccessValidatorFactory validatorFactory;

    /**
     * Constructs a {@code ThingSearchRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public ThingSearchRoute(final RouteBaseProperties routeBaseProperties) {
        this(routeBaseProperties, null);
    }

    /**
     * Constructs a {@code ThingSearchRoute} object with namespace access enforcement.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param validatorFactory the factory for creating namespace access validators, may be null.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public ThingSearchRoute(final RouteBaseProperties routeBaseProperties,
            @Nullable final NamespaceAccessValidatorFactory validatorFactory) {
        super(routeBaseProperties);
        this.validatorFactory = validatorFactory;
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
        return concat(
                // GET things/count?filter=<filterString>&namespaces=<namespacesString>
                get(() -> thingSearchParameterOptional(
                        params -> {
                            final Set<String> requestedNamespaces =
                                    calculateNamespaces(params.get(ThingSearchParameter.NAMESPACES));
                            final Set<String> filteredNamespaces =
                                    applyNamespaceAccessControl(requestedNamespaces, dittoHeaders);
                            return handlePerRequest(ctx,
                                    CountThings.of(calculateFilter(params.get(ThingSearchParameter.FILTER)),
                                            filteredNamespaces,
                                            dittoHeaders));
                        })),
                // POST things/count
                post(() -> ensureMediaTypeFormUrlEncodedThenExtractData(
                        ctx,
                        dittoHeaders,
                        formFields -> {
                            final Set<String> requestedNamespaces = calculateNamespaces(
                                    formFields.getOrDefault(ThingSearchParameter.NAMESPACES.toString(), List.of()));
                            final Set<String> filteredNamespaces =
                                    applyNamespaceAccessControl(requestedNamespaces, dittoHeaders);
                            return handlePerRequest(
                                    ctx,
                                    CountThings.of(
                                            calculateFilter(
                                                    formFields.getOrDefault(ThingSearchParameter.FILTER.toString(),
                                                            List.of())),
                                            filteredNamespaces,
                                            dittoHeaders)
                            );
                        }
                ))
        );
    }

    /*
     * Describes {@code /search/things} route.
     *
     * @return {@code /search/things} route.
     */
    private Route searchThings(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return concat(
                // GET /search/things?filter=<filterString>
                //           &options=<optionsString>
                //           &fields=<fieldsString>
                //           &namespaces=<namespacesString>
                get(() -> thingSearchParameterOptional(
                                params -> {
                                    final Set<String> requestedNamespaces =
                                            calculateNamespaces(params.get(ThingSearchParameter.NAMESPACES));
                                    final Set<String> filteredNamespaces =
                                            applyNamespaceAccessControl(requestedNamespaces, dittoHeaders);
                                    return handlePerRequest(ctx,
                                            QueryThings.of(
                                                    calculateFilter(params.get(ThingSearchParameter.FILTER)),
                                                    calculateOptions(params.get(ThingSearchParameter.OPTION)),
                                                    AbstractRoute.calculateSelectedFields(params.get(ThingSearchParameter.FIELDS))
                                                            .orElse(null),
                                                    filteredNamespaces,
                                                    dittoHeaders
                                            )
                                    );
                                }
                        )
                ),
                // POST /search/things
                post(() -> ensureMediaTypeFormUrlEncodedThenExtractData(
                        ctx,
                        dittoHeaders,
                        formFields -> {
                            final Set<String> requestedNamespaces = calculateNamespaces(
                                    formFields.getOrDefault(ThingSearchParameter.NAMESPACES.toString(), List.of()));
                            final Set<String> filteredNamespaces =
                                    applyNamespaceAccessControl(requestedNamespaces, dittoHeaders);
                            return handlePerRequest(ctx,
                                    QueryThings.of(
                                            calculateFilter(
                                                    formFields.getOrDefault(ThingSearchParameter.FILTER.toString(),
                                                            List.of())),
                                            calculateOptions(
                                                    formFields.getOrDefault(ThingSearchParameter.OPTION.toString(),
                                                            List.of())),
                                            AbstractRoute.calculateSelectedFields(
                                                    formFields.getOrDefault(ThingSearchParameter.FIELDS.toString(),
                                                            List.of())).orElse(null),
                                            filteredNamespaces,
                                            calculateSearchPostDittoHeaders(dittoHeaders,
                                                    formFields.getOrDefault(DittoHeaderDefinition.CONDITION.getKey(),
                                                            List.of()))
                                    )
                            );
                        }
                ))
        );

    }

    private Route thingSearchParameterOptional(
            final Function<EnumMap<ThingSearchParameter, List<String>>, Route> inner) {
        return thingSearchParameterOptionalImpl(ThingSearchParameter.values(),
                new EnumMap<>(ThingSearchParameter.class), inner);
    }

    private Route thingSearchParameterOptionalImpl(final ThingSearchParameter[] values,
            final EnumMap<ThingSearchParameter, List<String>> accumulator,
            final Function<EnumMap<ThingSearchParameter, List<String>>, Route> inner) {
        if (accumulator.size() >= values.length) {
            return inner.apply(accumulator);
        } else {
            final ThingSearchParameter parameter = values[accumulator.size()];
            return parameterList(parameter.toString(), parameterValues -> {
                accumulator.put(parameter, parameterValues);
                return thingSearchParameterOptionalImpl(values, accumulator, inner);
            });
        }
    }

    @Nullable
    private static String calculateFilter(final List<String> filterString) {
        if (filterString.isEmpty()) {
            return null;
        }
        return filterString.stream()
                .collect(Collectors.joining(",", "and(", ")"));
    }

    @Nullable
    public static Set<String> calculateNamespaces(final List<String> namespacesStrings) {
        final Function<String, Stream<String>> splitAndRemoveEmpty =
                s -> Arrays.stream(s.split(","))
                        .filter(segment -> !segment.isEmpty());

        // if no namespaces are given explicitly via query parameter,
        // return null to signify the lack of namespace restriction
        if (namespacesStrings.isEmpty()) {
            return null;
        }
        return namespacesStrings.stream()
                .flatMap(splitAndRemoveEmpty)
                .collect(Collectors.toSet());
    }

    @Nullable
    public static List<String> calculateOptions(final List<String> optionsString) {
        if (optionsString.isEmpty()) {
            return null;
        }
        return optionsString
                .stream()
                .flatMap(s -> Arrays.stream(s.split("\\),")))
                .map(s -> s.endsWith(")") ? s : s + ")")
                .toList();
    }

    private static DittoHeaders calculateSearchPostDittoHeaders(final DittoHeaders dittoHeaders,
            final List<String> conditionsString) {

        if (conditionsString.isEmpty()) {
            return dittoHeaders;
        } else {
            return dittoHeaders.toBuilder()
                    .condition(
                            conditionsString
                                    .stream()
                                    .collect(Collectors.joining(",", "and(", ")"))
                    )
                    .build();
        }
    }

    @Nullable
    private Set<String> applyNamespaceAccessControl(@Nullable final Set<String> requestedNamespaces,
            final DittoHeaders dittoHeaders) {
        if (validatorFactory == null) {
            return requestedNamespaces;
        }

        final NamespaceAccessValidator validator =
                validatorFactory.createValidator(dittoHeaders);

        if (requestedNamespaces != null) {
            return validator.filterAllowedNamespaces(requestedNamespaces);
        } else {
            return validator.getApplicableNamespacePatterns().orElse(null);
        }
    }

}
