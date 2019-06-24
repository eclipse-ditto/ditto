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
package org.eclipse.ditto.services.gateway.endpoints.routes.devops;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevOpsBasicAuthenticationDirective.REALM_DEVOPS;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.DevOpsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.DevOpsBasicAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.signals.commands.common.RetrieveConfig;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.devops.ExecutePiggybackCommand;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;

/**
 * Builder for creating Akka HTTP routes for {@code /devops}.
 */
public final class DevOpsRoute extends AbstractRoute {

    /**
     * Public endpoint of DevOps.
     */
    public static final String PATH_DEVOPS = "devops";

    private static final String PATH_LOGGING = "logging";
    private static final String PATH_PIGGYBACK = "piggyback";
    private static final String PATH_CONFIG = "config";

    /**
     * Timeout in milliseconds of how long to wait for all responses before returning.
     */
    private static final String TIMEOUT_PARAMETER = "timeout";

    /**
     * Path parameter for retrieving config.
     */
    private static final String PATH_PARAMETER = "path";

    /**
     * Actor path of DevOpsCommandsActor for ALL services. Not starting DevOpsCommandsActor at this path results
     * in the service not getting any RetrieveConfig commands.
     */
    private static final String DEVOPS_COMMANDS_ACTOR_SELECTION = "/user/devOpsCommandsActor";

    private final DevOpsConfig devOpsConfig;

    /**
     * Constructs the {@code /devops} route builder.
     *
     * @param actorSystem the Actor System.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param devOpsConfig the configuration settings of the Gateway service's DevOps endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public DevOpsRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final DevOpsConfig devOpsConfig,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, headerTranslator);
        this.devOpsConfig = checkNotNull(devOpsConfig, "DevOpsConfig");
    }

    /**
     * @return the {@code /devops} route.
     */
    public Route buildDevOpsRoute(final RequestContext ctx) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_DEVOPS), () -> {// /devops
            final DevOpsBasicAuthenticationDirective devOpsBasicAuthenticationDirective =
                    DevOpsBasicAuthenticationDirective.getInstance(devOpsConfig);
            return devOpsBasicAuthenticationDirective.authenticateDevOpsBasic(REALM_DEVOPS,
                    parameterOptional(Unmarshaller.sync(Long::parseLong), TIMEOUT_PARAMETER, optionalTimeout ->
                            concat(
                                    rawPathPrefix(mergeDoubleSlashes().concat(PATH_LOGGING),
                                            () -> // /devops/logging
                                                    logging(ctx, createHeaders(optionalTimeout))
                                    ),
                                    rawPathPrefix(mergeDoubleSlashes().concat(PATH_PIGGYBACK),
                                            () -> // /devops/piggyback
                                                    piggyback(ctx, createHeaders(optionalTimeout))
                                    ),
                                    rawPathPrefix(mergeDoubleSlashes().concat(PATH_CONFIG),
                                            () -> // /devops/config
                                                    config(ctx, createHeaders(optionalTimeout)))
                            )
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

        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), serviceName ->
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

        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), instance ->
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
                                    final JsonObject parsedJson =
                                            JsonFactory.readFrom(piggybackCommandJson).asObject();

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
                        ExecutePiggybackCommand.of(
                                serviceName, instance, DEVOPS_COMMANDS_ACTOR_SELECTION,
                                RetrieveConfig.of(path.orElse(null), headersWithAggregate).toJson(),
                                headersWithAggregate
                        )
                )
        ));

    }

    private static Function<JsonValue, JsonValue> transformResponse(final CharSequence serviceName,
            final String instance) {

        final JsonPointer transformerPointer = transformerPointer(serviceName, instance);
        if (transformerPointer.isEmpty()) {
            return resp -> resp;
        }
        return resp -> resp.asObject()
                .getValue(transformerPointer)
                .orElse(JsonFactory.nullObject());
    }

    private static JsonPointer transformerPointer(@Nullable final CharSequence serviceName,
            @Nullable final String instance) {

        JsonPointer newPointer = JsonPointer.empty();
        if (serviceName != null) {
            newPointer = newPointer.append(JsonPointer.of(serviceName));
        }
        if (instance != null) {
            newPointer = newPointer.append(JsonPointer.of(instance));
        }
        return newPointer;
    }

    private static DittoHeaders createHeaders(final Optional<Long> optionalTimeout) {
        final DittoHeadersBuilder headersBuilder =
                DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString());
        optionalTimeout.ifPresent(t -> headersBuilder.putHeader(TIMEOUT_PARAMETER, Long.toString(t)));
        return headersBuilder.build();
    }

    @FunctionalInterface
    private interface RouteBuilderWithOptionalServiceNameAndInstance {

        Route build(RequestContext ctx, String serviceName, String instance, DittoHeaders dittoHeaders);

    }

}
