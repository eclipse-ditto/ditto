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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP route for {@code /health}.
 */
public final class CachingHealthRoute {

    /**
     * Public endpoint of health.
     */
    public static final String PATH_HEALTH = "health";

    private final StatusAndHealthProvider statusHealthHelper;
    private final Duration refreshInterval;

    private CompletionStage<StatusInfo> cachedHealth;
    private Instant lastCheckInstant;

    /**
     * Constructs the {@code /health} route builder.
     *
     * @param statusHealthHelper the helper for retrieving health status of the cluster.
     * @param refreshInterval the interval for refreshing the health status.
     */
    public CachingHealthRoute(final StatusAndHealthProvider statusHealthHelper, final Duration refreshInterval) {
        this.statusHealthHelper = statusHealthHelper;
        this.refreshInterval = refreshInterval;
    }

    /**
     * Builds the {@code /health} route.
     *
     * @return the {@code /health} route.
     */
    public Route buildHealthRoute() {
        return path(PATH_HEALTH, () -> // /health
                get(() -> // GET
                        completeWithFuture(createOverallHealthResponse())
                )
        );
    }

    private boolean isCacheTimedOut() {
        return lastCheckInstant.plusSeconds(refreshInterval.getSeconds()).isBefore(Instant.now());
    }

    private CompletionStage<HttpResponse> createOverallHealthResponse() {
        if (cachedHealth == null || isCacheTimedOut()) {
            cachedHealth = statusHealthHelper.retrieveHealth()
                    .exceptionally(t -> StatusInfo.fromDetail(StatusDetailMessage.of(StatusDetailMessage.Level.ERROR,
                            "Exception: " + t.getMessage())));
            lastCheckInstant = Instant.now();
        }

        return cachedHealth.thenApply(overallHealth -> {
            final JsonObject statusObject = JsonFactory.newObject()
                    .set(StatusInfo.JSON_KEY_STATUS, overallHealth.getStatus().toString());
            if (overallHealth.isHealthy()) {
                return HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withEntity(ContentTypes.APPLICATION_JSON, statusObject.toString());
            } else {
                return HttpResponse.create()
                        .withStatus(StatusCodes.SERVICE_UNAVAILABLE)
                        .withEntity(ContentTypes.APPLICATION_JSON, statusObject.toString());
            }
        });
    }

}
