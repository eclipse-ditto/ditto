/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.devops;

import static akka.http.javadsl.server.Directives.extractDataBytes;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.post;
import static akka.http.javadsl.server.Directives.put;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

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
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
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
     * Public endpoint of devops.
     */
    public static final String PATH_DEVOPS = "devops";

    private static final String PATH_LOGGING = "logging";
    private static final String PATH_PIGGYBACK = "piggyback";

    /**
     * Timeout in milliseconds of how long to wait for all responses before returning.
     */
    private static final String TIMEOUT_PARAMETER = "timeout";

    /**
     * Constructs the {@code /devops} route builder.
     *
     * @param actorSystem the Actor System.
     */
    public DevOpsRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        super(proxyActor, actorSystem);
    }

    /**
     * @return the {@code /devops} route.
     */
    public Route buildDevopsRoute(final RequestContext ctx) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_DEVOPS), () -> // /devops
                parameterOptional(Unmarshaller.sync(Long::parseLong), TIMEOUT_PARAMETER, optionalTimeout ->
                        route(
                                rawPathPrefix(mergeDoubleSlashes().concat(PATH_LOGGING), () -> // /devops/logging
                                        logging(ctx, createHeaders(optionalTimeout))
                                ),
                                rawPathPrefix(mergeDoubleSlashes().concat(PATH_PIGGYBACK), () -> // /devops/piggyback
                                        piggyback(ctx, createHeaders(optionalTimeout))
                                )
                        )
                )
        );
    }

    /*
     * @return {@code /devops/logging} route.
     */
    private Route logging(final RequestContext ctx, final DittoHeaders dittoHeaders) {

        return route(
                // /devops/logging/<serviceName>
                loggingServiceName(ctx, dittoHeaders),
                // /devops/logging
                routeLogging(ctx, dittoHeaders, null, null)
        );
    }

    /*
     * @return {@code /devops/logging/<serviceName>} route.
     */
    private Route loggingServiceName(final RequestContext ctx, final DittoHeaders dittoHeaders) {

        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), serviceName ->
                route(
                        // /devops/logging/<serviceName>/<instance>
                        loggingInstance(ctx, dittoHeaders, serviceName),
                        // /devops/logging/<serviceName>
                        routeLogging(ctx, dittoHeaders, serviceName, null)
                )
        );
    }

    /*
     * @return {@code /devops/logging/<serviceName>/<instance>} route.
     */
    private Route loggingInstance(final RequestContext ctx, final DittoHeaders dittoHeaders, final String serviceName) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()),
                // /devops/logging/<serviceName>/<instance>
                instance ->
                        routeLogging(ctx, dittoHeaders, serviceName, Integer.parseInt(instance))
        );
    }

    /*
    * @return {@code /devops/piggyback} route.
    */
    private Route piggyback(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return route(
                // /devops/piggyback/<serviceName>
                piggybackServiceName(ctx, dittoHeaders),
                // /devops/piggyback
                routePiggyback(ctx, null, null, dittoHeaders)

        );
    }

    /*
     * @return {@code /devops/piggyback/<serviceName>} route.
     */
    private Route piggybackServiceName(final RequestContext ctx, final DittoHeaders dittoHeaders) {

        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), serviceName ->
                route(
                        // /devops/piggyback/<serviceName>/<instance>
                        piggybackInstance(ctx, serviceName, dittoHeaders),
                        // /devops/piggyback/<serviceName>
                        routePiggyback(ctx, serviceName, null, dittoHeaders)
                )
        );
    }

    /*
     * @return {@code /devops/piggyback/<serviceName>/<instance>} route.
     */
    private Route piggybackInstance(final RequestContext ctx, final String serviceName,
            final DittoHeaders dittoHeaders) {

        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), instance ->
                // /devops/piggyback/<serviceName>/<instance>
                routePiggyback(ctx, serviceName, Integer.parseInt(instance), dittoHeaders)
        );
    }

    private Route routeLogging(final RequestContext ctx, final DittoHeaders dittoHeaders, final String serviceName,
            final Integer instance) {

        return route(
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

    private Route routePiggyback(final RequestContext ctx, @Nullable final String serviceName,
            @Nullable final Integer instance, final DittoHeaders dittoHeaders) {
        return post(() ->
                extractDataBytes(payloadSource ->
                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                piggybackCommandJson -> {
                                    final JsonObject parsedJson =
                                            JsonFactory.readFrom(piggybackCommandJson).asObject();

                                    return ExecutePiggybackCommand.of(serviceName, instance,
                                            parsedJson.getValueOrThrow(
                                                    ExecutePiggybackCommand.JSON_TARGET_ACTORSELECTION),
                                            parsedJson.getValueOrThrow(
                                                    ExecutePiggybackCommand.JSON_PIGGYBACK_COMMAND),
                                            parsedJson.getValue("headers")
                                                    .filter(JsonValue::isObject)
                                                    .map(JsonValue::asObject)
                                                    .map(DittoHeaders::newBuilder)
                                                    .map(head -> head.putHeaders(dittoHeaders))
                                                    .map(DittoHeadersBuilder::build)
                                                    .orElse(dittoHeaders));
                                }
                        )
                )
        );
    }

    private static Function<JsonValue, JsonValue> transformResponse(final String serviceName, final Integer instance) {
        final JsonPointer transformerPointer = transformerPointer(serviceName, instance);
        if (transformerPointer.isEmpty()) {
            return resp -> resp;
        } else {
            return resp -> resp.asObject()
                    .getValue(transformerPointer)
                    .orElse(JsonFactory.nullObject());
        }
    }

    private static JsonPointer transformerPointer(@Nullable final String serviceName,
            @Nullable final Integer instance) {
        JsonPointer newPointer = JsonPointer.empty();
        if (serviceName != null) {
            newPointer = newPointer.append(JsonPointer.of(serviceName));
        }
        if (instance != null) {
            newPointer = newPointer.append(JsonPointer.of(instance.toString()));
        }
        return newPointer;
    }

    private static DittoHeaders createHeaders(final Optional<Long> optionalTimeout) {
        final DittoHeadersBuilder headersBuilder =
                DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString());
        optionalTimeout.ifPresent(t -> headersBuilder.putHeader(TIMEOUT_PARAMETER, Long.toString(t)));
        return headersBuilder.build();
    }

}
