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
package org.eclipse.ditto.services.gateway.endpoints.routes.health;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective;
import org.eclipse.ditto.services.gateway.health.StatusHealthHelper;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.health.HealthStatus;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP route for {@code /health}.
 */
public final class CachingHealthRoute {

    private static final String PATH_HEALTH = "health";

    private final ActorSystem actorSystem;
    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final Duration refreshInterval;

    private CompletionStage<JsonObject> cachedHealth;
    private Instant lastCheckInstant;

    /**
     * Constructs the {@code /health} route builder.
     *
     * @param actorSystem the Actor System.
     * @param clusterStateSupplier the supplier to get the cluster state.
     */
    public CachingHealthRoute(final ActorSystem actorSystem, final Supplier<ClusterStatus> clusterStateSupplier) {
        this.actorSystem = actorSystem;
        this.clusterStateSupplier = clusterStateSupplier;
        refreshInterval = actorSystem.settings().config().getDuration(ConfigKeys.STATUS_HEALTH_EXTERNAL_CACHE_TIMEOUT);
    }

    /**
     * Builds the {@code /health} route.
     *
     * @return the {@code /health} route.
     */
    public Route buildHealthRoute() {
        return path(PATH_HEALTH, () -> // /health
                get(() -> // GET
                        DevopsBasicAuthenticationDirective.authenticateDevopsBasic(
                                DevopsBasicAuthenticationDirective.REALM_HEALTH,
                                completeWithFuture(createOverallHealthResponse())
                        )
                )
        );
    }

    private boolean isCacheTimedOut() {
        return lastCheckInstant.plusSeconds(refreshInterval.getSeconds()).isBefore(Instant.now());
    }

    private CompletionStage<HttpResponse> createOverallHealthResponse() {
        if (cachedHealth == null || isCacheTimedOut()) {
            cachedHealth = StatusHealthHelper.calculateOverallHealthJson(actorSystem, clusterStateSupplier)
                    .thenApply(overallHealth -> JsonObject.newBuilder()
                            .set(HealthStatus.JSON_KEY_STATUS, overallHealth.getValue(HealthStatus.JSON_KEY_STATUS)
                                    .orElse(JsonValue.newInstance(HealthStatus.Status.DOWN.toString())))
                            .build()
                    );
            lastCheckInstant = Instant.now();
        }

        return cachedHealth.thenApply(overallHealth -> {
            if (StatusHealthHelper.checkIfAllSubStatusAreUp(overallHealth)) {
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
