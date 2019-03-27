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
package org.eclipse.ditto.services.gateway.endpoints.routes.status;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.REALM_DEVOPS;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.authenticateDevopsBasic;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

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
    public static final String PATH_OVERALL = "overall";

    static final String PATH_STATUS = "status";
    static final String PATH_HEALTH = "health";
    static final String PATH_CLUSTER = "cluster";


    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final StatusAndHealthProvider statusHealthProvider;

    /**
     * Constructs the {@code /status} route builder.
     *
     * @param clusterStateSupplier the supplier to get the cluster state.
     * @param statusHealthProvider the provider for retrieving health status of the cluster.
     */
    public OverallStatusRoute(final Supplier<ClusterStatus> clusterStateSupplier,
            final StatusAndHealthProvider statusHealthProvider) {
        this.clusterStateSupplier = clusterStateSupplier;
        this.statusHealthProvider = statusHealthProvider;
    }

    /**
     * Builds the {@code /status} route.
     *
     * @return the {@code /status} route.
     */
    public Route buildOverallStatusRoute() {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_OVERALL), () -> // /overall/*
                authenticateDevopsBasic(REALM_DEVOPS, get(() -> // GET
                        // /overall/status
                        // /overall/status/health
                        // /overall/status/cluster
                        rawPathPrefix(mergeDoubleSlashes().concat(PATH_STATUS), () ->
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
                                ))

                )));
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
