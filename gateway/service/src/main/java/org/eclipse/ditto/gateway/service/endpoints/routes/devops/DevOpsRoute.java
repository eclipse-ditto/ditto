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
package org.eclipse.ditto.gateway.service.endpoints.routes.devops;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevOpsOAuth2AuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.api.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.QueryParametersToHeadersMap;
import org.eclipse.ditto.base.service.devops.DevOpsCommandsActor;
import org.eclipse.ditto.base.api.common.RetrieveConfig;
import org.eclipse.ditto.base.api.devops.signals.commands.ChangeLogLevel;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfig;

import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;

/**
 * Builder for creating Akka HTTP routes for {@code /devops}.
 */
public final class DevOpsRoute extends AbstractRoute {

    /**
     * Public endpoint of DevOps.
     */
    private static final String PATH_DEVOPS = "devops";

    /**
     * Actor path of DevOpsCommandsActor for ALL services. Not starting DevOpsCommandsActor at this path results
     * in the service not getting any RetrieveConfig commands.
     */
    public static final String DEVOPS_COMMANDS_ACTOR_SELECTION = "/user/devOpsCommandsActor";

    private static final String PATH_LOGGING = "logging";
    private static final String PATH_PIGGYBACK = "piggyback";
    private static final String PATH_CONFIG = "config";

    /**
     * Path parameter for retrieving config.
     */
    private static final String PATH_PARAMETER = "path";

    private final HttpConfig httpConfig;
    private final DevopsAuthenticationDirective devOpsAuthenticationDirective;

    /**
     * Constructs a {@code DevOpsRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param devOpsAuthenticationDirective the authentication handler for the Devops directive.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public DevOpsRoute(final RouteBaseProperties routeBaseProperties,
            final DevopsAuthenticationDirective devOpsAuthenticationDirective) {

        super(routeBaseProperties);
        httpConfig = routeBaseProperties.getHttpConfig();
        this.devOpsAuthenticationDirective = devOpsAuthenticationDirective;
    }

    /**
     * @return the {@code /devops} route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public Route buildDevOpsRoute(final RequestContext ctx, final Map<String, String> queryParameters) {
        checkNotNull(ctx, "ctx");
        checkNotNull(queryParameters, "queryParameters");

        return rawPathPrefix(PathMatchers.slash().concat(PATH_DEVOPS), () -> {// /devops
            return devOpsAuthenticationDirective.authenticateDevOps(DevOpsOAuth2AuthenticationDirective.REALM_DEVOPS,
                    concat(
                            rawPathPrefix(PathMatchers.slash().concat(PATH_LOGGING),
                                    () -> // /devops/logging
                                            logging(ctx, createHeaders(queryParameters))
                            ),
                            rawPathPrefix(PathMatchers.slash().concat(PATH_PIGGYBACK),
                                    () -> // /devops/piggyback
                                            piggyback(ctx, createHeaders(queryParameters))
                            ),
                            rawPathPrefix(PathMatchers.slash().concat(PATH_CONFIG),
                                    () -> // /devops/config
                                            config(ctx, createHeaders(queryParameters)))
                    )
            );
        });
    }

    /*
     * @return {@code /devops/logging} route.
     */
    private Route logging(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return buildRouteWithOptionalServiceNameAndInstance(ctx, dittoHeaders, this::routeLogging);
    }

    /*
     * @return {@code /devops/piggyback} route.
     */
    private Route piggyback(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return buildRouteWithOptionalServiceNameAndInstance(ctx, dittoHeaders, this::routePiggyback);
    }

    /*
     * @return {@code /devops/config} route.
     */
    private Route config(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return buildRouteWithOptionalServiceNameAndInstance(ctx, dittoHeaders, this::routeConfig);
    }

    /*
     * @return {@code /devops/<logging|piggyback>/} route.
     */
    private Route buildRouteWithOptionalServiceNameAndInstance(final RequestContext ctx,
            final DittoHeaders dittoHeaders, final RouteBuilderWithOptionalServiceNameAndInstance routeBuilder) {

        return concat(
                // /devops/<logging|piggyback>/<serviceName>
                buildRouteWithServiceNameAndOptionalInstance(ctx, dittoHeaders, routeBuilder),
                // /devops/<logging|piggyback>/
                routeBuilder.build(ctx, null, null, dittoHeaders)
        );
    }

    /*
     * @return {@code /devops/<logging|piggyback>/<serviceName>} route.
     */
    private Route buildRouteWithServiceNameAndOptionalInstance(final RequestContext ctx,
            final DittoHeaders dittoHeaders, final RouteBuilderWithOptionalServiceNameAndInstance routeBuilder) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), serviceName ->
                concat(
                        // /devops/<logging|piggyback>/<serviceName>/<instance>
                        buildRouteWithServiceNameAndInstance(ctx, serviceName, dittoHeaders, routeBuilder),
                        // /devops/<logging|piggyback>/<serviceName>
                        routeBuilder.build(ctx, serviceName, null, dittoHeaders)
                )
        );
    }

    /*
     * @return {@code /devops/<logging|piggyback>/<serviceName>/<instance>} route.
     */
    private Route buildRouteWithServiceNameAndInstance(final RequestContext ctx,
            final String serviceName,
            final DittoHeaders dittoHeaders,
            final RouteBuilderWithOptionalServiceNameAndInstance routeBuilder) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), instance ->
                // /devops/<logging|piggyback>/<serviceName>/<instance>
                routeBuilder.build(ctx, serviceName, instance, dittoHeaders)
        );
    }

    private Route routeLogging(final RequestContext ctx,
            final String serviceName,
            final String instance,
            final DittoHeaders dittoHeaders) {

        return concat(
                get(() ->
                        handlePerRequest(ctx,
                                RetrieveLoggerConfig.ofAllKnownLoggers(serviceName, instance, dittoHeaders),
                                transformResponse(serviceName, instance)
                        )
                ),
                put(() ->
                        extractDataBytes(payloadSource ->
                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                        loggerConfigJson ->
                                                ChangeLogLevel.of(serviceName, instance,
                                                        ImmutableLoggerConfig.fromJson(loggerConfigJson), dittoHeaders),
                                        transformResponse(serviceName, instance)
                                )
                        )
                )
        );
    }

    private Route routePiggyback(final RequestContext ctx,
            @Nullable final String serviceName,
            @Nullable final String instance,
            final DittoHeaders dittoHeaders) {

        return post(() ->
                extractDataBytes(payloadSource ->
                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                piggybackCommandJson -> {
                                    final JsonObject parsedJson = DittoJsonException.wrapJsonRuntimeException(() ->
                                            JsonFactory.readFrom(piggybackCommandJson).asObject());

                                    final String serviceName1;
                                    final String instance1;

                                    // serviceName and instance from URL are preferred
                                    if (serviceName == null) {
                                        serviceName1 = parsedJson.getValue(DevOpsCommand.JsonFields.JSON_SERVICE_NAME)
                                                .orElse(serviceName);
                                    } else {
                                        serviceName1 = serviceName;
                                    }

                                    if (instance == null) {
                                        instance1 = parsedJson.getValue(DevOpsCommand.JsonFields.JSON_INSTANCE)
                                                .orElse(instance);
                                    } else {
                                        instance1 = instance;
                                    }

                                    return ExecutePiggybackCommand.of(serviceName1, instance1,
                                            parsedJson.getValueOrThrow(
                                                    ExecutePiggybackCommand.JSON_TARGET_ACTORSELECTION),
                                            parsedJson.getValueOrThrow(
                                                    ExecutePiggybackCommand.JSON_PIGGYBACK_COMMAND),
                                            parsedJson.getValue("headers")
                                                    .filter(JsonValue::isObject)
                                                    .map(JsonValue::asObject)
                                                    .map(DittoHeaders::newBuilder)
                                                    .map(head -> head.putHeaders(dittoHeaders))
                                                    .map((java.util.function.Function<DittoHeadersBuilder, DittoHeaders>)
                                                            DittoHeadersBuilder::build)
                                                    .orElse(dittoHeaders));
                                }
                        )
                )
        );
    }

    private Route routeConfig(final RequestContext ctx,
            final String serviceName,
            final String instance,
            final DittoHeaders dittoHeaders) {

        final DittoHeaders headersWithAggregate = dittoHeaders.toBuilder()
                .putHeader(DevOpsCommandsActor.AGGREGATE_HEADER,
                        String.valueOf(serviceName == null || instance == null))
                .build();

        return get(() -> parameterOptional(PATH_PARAMETER, path ->
                handlePerRequest(ctx,
                        ExecutePiggybackCommand.of(serviceName,
                                instance,
                                DEVOPS_COMMANDS_ACTOR_SELECTION,
                                RetrieveConfig.of(path.orElse(null), headersWithAggregate).toJson(),
                                headersWithAggregate))));
    }

    private static Function<JsonValue, JsonValue> transformResponse(final CharSequence serviceName,
            final CharSequence instance) {

        final JsonPointer transformerPointer = transformerPointer(serviceName, instance);
        if (transformerPointer.isEmpty()) {
            return resp -> resp;
        }
        return resp -> resp.asObject()
                .getValue(transformerPointer)
                .orElse(JsonFactory.nullObject());
    }

    private static JsonPointer transformerPointer(@Nullable final CharSequence serviceName,
            @Nullable final CharSequence instance) {

        JsonPointer newPointer = JsonPointer.empty();
        if (serviceName != null) {
            newPointer = newPointer.append(JsonPointer.of(serviceName));
        }
        if (instance != null) {
            newPointer = newPointer.append(JsonPointer.of(instance));
        }
        return newPointer;
    }

    private DittoHeaders createHeaders(final Map<String, String> queryParameters) {
        final QueryParametersToHeadersMap queryParamsToHeaders = QueryParametersToHeadersMap.getInstance(httpConfig);

        return DittoHeaders.newBuilder()
                .randomCorrelationId()
                .putHeaders(queryParamsToHeaders.apply(queryParameters))
                .build();
    }

    @FunctionalInterface
    private interface RouteBuilderWithOptionalServiceNameAndInstance {

        Route build(RequestContext ctx, String serviceName, String instance, DittoHeaders dittoHeaders);

    }

}
