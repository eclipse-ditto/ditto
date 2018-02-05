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
package org.eclipse.ditto.services.gateway.endpoints.routes.status;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /status}.
 */
public final class OverallStatusRoute {

    /**
     * Public endpoint of overall status.
     */
    public static final String PATH_STATUS = "status";

    static final String PATH_HEALTH = "health";
    static final String PATH_CLUSTER = "cluster";
    static final String PATH_OWN = "own";

    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final StatusAndHealthProvider statusHealthProvider;

    private final StatusRoute ownStatusRoute;

    /**
     * Constructs the {@code /status} route builder.
     *
     * @param actorSystem the Actor System.
     * @param clusterStateSupplier the supplier to get the cluster state.
     * @param healthCheckingActor the actor for checking the gateways own health.
     * @param statusHealthProvider the provider for retrieving health status of the cluster.
     */
    public OverallStatusRoute(final ActorSystem actorSystem, final Supplier<ClusterStatus> clusterStateSupplier,
            final ActorRef healthCheckingActor, final StatusAndHealthProvider statusHealthProvider) {
        this.clusterStateSupplier = clusterStateSupplier;
        this.statusHealthProvider = statusHealthProvider;

        ownStatusRoute = new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem);
    }

    /**
     * Builds the {@code /status} route.
     *
     * @return the {@code /status} route.
     */
    public Route buildStatusRoute() {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_STATUS), () -> // /status/*
                get(() -> // GET
                        route(
                                // /status/own/status
                                // /status/own/status/health
                                // /status/own/status/cluster
                                rawPathPrefix(mergeDoubleSlashes().concat(PATH_OWN), ownStatusRoute::buildStatusRoute),
                                route(
                                        // /status
                                        pathEndOrSingleSlash(
                                                () -> completeWithFuture(createOverallStatusResponse())),
                                        // /status/health
                                        path(PATH_HEALTH,
                                                () -> completeWithFuture(createOverallHealthResponse())),
                                        path(PATH_CLUSTER, () -> complete( // /status/cluster
                                                HttpResponse.create().withStatus(StatusCodes.OK)
                                                        .withEntity(ContentTypes.APPLICATION_JSON,
                                                                clusterStateSupplier.get().toJson().toString()))
                                        )
                                )
                        )
                )
        );
    }

    private CompletionStage<HttpResponse> createOverallStatusResponse() {
        return statusHealthProvider.retrieveStatus()
                .thenApply(status -> HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withEntity(ContentTypes.APPLICATION_JSON, status.toString()));
    }

    private CompletionStage<HttpResponse> createOverallHealthResponse() {
        return statusHealthProvider.retrieveHealth()
                .thenApply(overallHealth -> {
                    if (overallHealth.isHealthy()) {
                        return HttpResponse.create()
                                .withStatus(StatusCodes.OK)
                                .withEntity(ContentTypes.APPLICATION_JSON, overallHealth.toJsonString());
                    } else {
                        return HttpResponse.create()
                                .withStatus(StatusCodes.SERVICE_UNAVAILABLE)
                                .withEntity(ContentTypes.APPLICATION_JSON, overallHealth.toJsonString());
                    }
                });
    }

}
