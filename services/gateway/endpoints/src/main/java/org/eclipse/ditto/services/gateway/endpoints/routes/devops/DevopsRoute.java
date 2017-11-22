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

import static akka.http.javadsl.server.Directives.delete;
import static akka.http.javadsl.server.Directives.extractDataBytes;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.put;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.devops.LoggerConfig;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /devops}.
 */
public final class DevopsRoute extends AbstractRoute {

    private static final String PATH_DEVOPS = "devops";
    private static final String PATH_LOGGERS = "loggers";
    private static final String PATH_SERVICES = "services";
    private static final String PATH_INSTANCES = "instances";

    /**
     * Constructs the {@code /devops} route builder.
     *
     * @param actorSystem the Actor System.
     */
    public DevopsRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        super(proxyActor, actorSystem);
    }

    /**
     * Builds the {@code /devops} route.
     *
     * @return the {@code /status} route.
     */
    public Route buildDevopsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_DEVOPS), () -> // /devops/*
                route(
                        rawPathPrefix(mergeDoubleSlashes().concat(PATH_LOGGERS), () -> // /devops/loggers
                                loggers(ctx, dittoHeaders)
                        )
                )
        );
    }

    /*
     * Describes {@code /loggers} route.
     * @return {@code /loggers} route.
     */
    private Route loggers(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return route(
                pathEndOrSingleSlash(() -> // /devops/loggers
                        route(
                                get(() -> // GET /devops/loggers
                                        handlePerRequest(ctx, RetrieveLoggerConfig.ofAllKnownLoggers(dittoHeaders))
                                ),
                                put(() -> // PUT /devops/loggers
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        policyJson -> ChangeLogLevels.of(dittoHeaders)
                                                )
                                        )
                                )
                        )
                ),
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_SERVICES), () -> // /devops/loggers/services
                        pathEndOrSingleSlash(() -> // /devops/loggers/services
                                route(
                                        get(() -> // GET /devops/loggers/services
                                                handlePerRequest(ctx, RetrieveLoggerConfig.ofAllKnownLoggers(dittoHeaders))
                                        ),
                                        put(() -> // PUT /devops/loggers
                                                extractDataBytes(payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                policyJson -> ChangeLogLevels.of(dittoHeaders)
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

}
