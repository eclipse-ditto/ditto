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
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.put;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /devops}.
 */
public final class DevOpsRoute extends AbstractRoute {

    private static final String PATH_DEVOPS = "devops";
    private static final String PATH_LOGGING = "logging";

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
                Directives.route(
                        rawPathPrefix(mergeDoubleSlashes().concat(PATH_LOGGING), () -> // /devops/logging
                                logging(ctx, createHeaders())
                        )
                )
        );
    }

    /*
     * @return {@code /logging} route.
     */
    private Route logging(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return Directives.route(
                pathEndOrSingleSlash(() -> // /devops/logging
                        route(
                                get(() -> // GET /devops/logging
                                        handlePerRequest(ctx, RetrieveLoggerConfig.ofAllKnownLoggers(dittoHeaders))
                                ),
                                put(() -> // PUT /devops/logging
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        loggerConfigJson ->
                                                                ChangeLogLevel.of(ImmutableLoggerConfig.fromJson(
                                                                        loggerConfigJson), dittoHeaders)
                                                )
                                        )
                                )
                        )
                ),
                serviceName(ctx, dittoHeaders) // /devops/logging/<serviceName>
        );
    }

    /*
     * @return {@code /logging/<serviceName>} route.
     */
    private Route serviceName(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), // /devops/logging/<serviceName>
                serviceName ->
                        route(
                                instance(ctx, dittoHeaders, serviceName), // /devops/logging/<serviceName>/<instance>
                                get(() -> // GET /devops/logging/<serviceName>
                                        handlePerRequest(ctx,
                                                RetrieveLoggerConfig.ofAllKnownLoggers(serviceName, dittoHeaders),
                                                resp ->
                                                        resp.asObject()
                                                                .getValue("/" + serviceName)
                                                                .orElse(JsonFactory.nullObject())
                                        )
                                ),
                                put(() -> // PUT /devops/logging/<serviceName>
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        loggerConfigJson ->
                                                                ChangeLogLevel.of(serviceName,
                                                                        ImmutableLoggerConfig.fromJson(
                                                                                loggerConfigJson),
                                                                        dittoHeaders),
                                                        resp ->
                                                                resp.asObject()
                                                                        .getValue("/" + serviceName)
                                                                        .orElse(JsonFactory.nullObject())
                                                )
                                        )
                                )
                        )
        );
    }

    /*
     * @return {@code /devops/logging/<serviceName>/<instance>} route.
     */
    private Route instance(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String serviceName) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()),
                // /devops/logging/<serviceName>/<instance>
                instance ->
                        route(
                                get(() -> // GET /devops/logging/<serviceName>/<instance>
                                        handlePerRequest(ctx, RetrieveLoggerConfig.ofAllKnownLoggers(
                                                serviceName, Integer.parseInt(instance), dittoHeaders),
                                                resp ->
                                                        resp.asObject()
                                                                .getValue("/" + serviceName + "/" + instance)
                                                                .orElse(JsonFactory.nullObject())
                                        )
                                ),
                                put(() -> // PUT /devops/logging/<serviceName>/<instance>
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        loggerConfigJson ->
                                                                ChangeLogLevel.of(serviceName,
                                                                        Integer.parseInt(instance),
                                                                        ImmutableLoggerConfig.fromJson(
                                                                                loggerConfigJson),
                                                                        dittoHeaders),
                                                        resp ->
                                                                resp.asObject()
                                                                        .getValue("/" + serviceName + "/" + instance)
                                                                        .orElse(JsonFactory.nullObject())
                                                )
                                        )
                                )
                        )
        );
    }

    private static DittoHeaders createHeaders() {
        return DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    }

}
