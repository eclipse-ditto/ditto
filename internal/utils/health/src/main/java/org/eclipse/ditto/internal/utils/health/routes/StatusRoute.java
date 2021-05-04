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
package org.eclipse.ditto.internal.utils.health.routes;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.Directives.route;

import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.health.HealthRouteSupplier;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.health.status.Status;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP route for {@code /status}.
 */
public final class StatusRoute {

    private static final String PATH_STATUS = "status";
    private static final String PATH_HEALTH = "health";
    private static final String PATH_CLUSTER = "cluster";

    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final HealthRouteSupplier healthRouteSupplier;

    /**
     * Constructs the {@code /status} route builder.
     *
     * @param clusterStateSupplier the supplier to get the cluster state.
     * @param healthCheckingActor the HealthCheckingActor to use.
     * @param actorSystem the Akka ActorSystem.
     */
    public StatusRoute(final Supplier<ClusterStatus> clusterStateSupplier, final ActorRef healthCheckingActor,
            final ActorSystem actorSystem) {
        this.clusterStateSupplier = clusterStateSupplier;
        this.healthRouteSupplier = new HealthRouteSupplier(healthCheckingActor, actorSystem.log());
    }

    /**
     * Builds the {@code /status} route.
     *
     * @return the {@code /status} route.
     */
    public Route buildStatusRoute() {
        return pathPrefix(PATH_STATUS, () -> // /status/*
                get(() -> // GET
                        route(
                                pathEndOrSingleSlash(() -> // /status
                                        complete(
                                                HttpResponse.create().withStatus(StatusCodes.OK)
                                                        .withEntity(ContentTypes.APPLICATION_JSON,
                                                                Status.provideStaticStatus().toString())
                                        )
                                ),
                                path(PATH_HEALTH, healthRouteSupplier), // /status/health
                                path(PATH_CLUSTER, () -> complete( // /status/cluster
                                        HttpResponse.create().withStatus(StatusCodes.OK)
                                                .withEntity(ContentTypes.APPLICATION_JSON,
                                                        clusterStateSupplier.get().toJson().toString()))
                                )
                        )
                ).orElse(complete(StatusCodes.METHOD_NOT_ALLOWED)));
    }

}
