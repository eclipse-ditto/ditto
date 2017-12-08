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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.services.gateway.health.StatusHealthHelper;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.health.status.Status;

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
    private final StatusHealthHelper statusHealthHelper;

    private final StatusRoute ownStatusRoute;

    /**
     * Constructs the {@code /status} route builder.
     *
     * @param actorSystem the Actor System.
     * @param clusterStateSupplier the supplier to get the cluster state.
     * @param healthCheckingActor the actor for checking the gateways own health.
     * @param statusHealthHelper the helper for retrieving health status of the cluster.
     */
    public OverallStatusRoute(final ActorSystem actorSystem, final Supplier<ClusterStatus> clusterStateSupplier,
            final ActorRef healthCheckingActor, final StatusHealthHelper statusHealthHelper) {
        this.clusterStateSupplier = clusterStateSupplier;
        this.statusHealthHelper = statusHealthHelper;

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
        final JsonObjectBuilder overallStatusBuilder = JsonFactory.newObjectBuilder();
        overallStatusBuilder.setAll(Status.provideStaticStatus());

        // aggregate completion of all completable futures:
        return statusHealthHelper.retrieveOverallRolesStatus()
                .thenApply(statusObjects -> {
                    final JsonObjectBuilder rolesStatusBuilder = JsonFactory.newObjectBuilder();
                    statusObjects.forEach(subStatusObj -> subStatusObj.forEach(rolesStatusBuilder::set));
                    overallStatusBuilder.set(StatusHealthHelper.JSON_KEY_ROLES, rolesStatusBuilder.build());
                    return HttpResponse.create().withStatus(StatusCodes.OK).withEntity(ContentTypes.APPLICATION_JSON,
                            overallStatusBuilder.build().toString());
                });
    }

    private CompletionStage<HttpResponse> createOverallHealthResponse() {
        return statusHealthHelper.calculateOverallHealthJson()
                .thenApply(overallHealth -> {
                    if (statusHealthHelper.checkIfAllSubStatusAreUp(overallHealth)) {
                        return HttpResponse.create()
                                .withStatus(StatusCodes.OK)
                                .withEntity(ContentTypes.APPLICATION_JSON, overallHealth.toString());
                    } else {
                        return HttpResponse.create()
                                .withStatus(StatusCodes.SERVICE_UNAVAILABLE)
                                .withEntity(ContentTypes.APPLICATION_JSON, overallHealth.toString());
                    }
                });
    }

}
